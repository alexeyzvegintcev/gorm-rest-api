/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gorm.restapi.transform

import gorm.restapi.RestApi
import gorm.restapi.controller.RestApiRepoController
import grails.artefact.Artefact
import grails.artefact.controller.support.AllowedMethodsHelper
import grails.compiler.DelegatingMethod
import grails.compiler.ast.ClassInjector
import grails.io.IOUtils
import grails.util.GrailsNameUtils
import grails.web.Action
import grails.web.controllers.ControllerMethod
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.compiler.injection.ArtefactTypeAstTransformation
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.compiler.injection.GrailsAwareInjectionOperation
import org.grails.compiler.injection.TraitInjectionUtils
import org.grails.compiler.web.ControllerActionTransformer
import org.grails.core.DefaultGrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.plugins.web.controllers.DefaultControllerExceptionHandlerMetaData
import org.grails.plugins.web.rest.transform.LinkableTransform
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.springframework.beans.factory.annotation.Autowired

import javax.servlet.http.HttpServletResponse
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import static java.lang.reflect.Modifier.*

//import grails.rest.Resource
//import grails.rest.RestfulController
import static org.grails.compiler.injection.GrailsASTUtils.ZERO_PARAMETERS
import static org.grails.compiler.injection.GrailsASTUtils.applyDefaultMethodTarget
import static org.grails.compiler.injection.GrailsASTUtils.applyDefaultMethodTarget
import static org.grails.compiler.injection.GrailsASTUtils.hasAnnotation
import static org.grails.compiler.injection.GrailsASTUtils.hasParameters
import static org.grails.compiler.injection.GrailsASTUtils.isInheritedFromTrait
import static org.grails.compiler.injection.GrailsASTUtils.nonGeneric
import static org.grails.compiler.injection.GrailsASTUtils.removeAnnotation

/**
 * The  transform automatically exposes a domain class as a RESTful resource. In effect the transform adds a controller to a Grails application
 * that performs CRUD operations on the domain. See the {@link Resource} annotation for more details
 *
 *
 * This is modified from {@link org.grails.plugins.web.rest.transform.ResourceTransform}
 * to use the RestApiController and get rid of the bits that mess with the URL mapping
 *
 * @author Joshua Burnett
 * @author Graeme Rocher
 *
 */
@SuppressWarnings(['VariableName', 'AbcMetric', 'ThrowRuntimeException', 'NoDef', 'MethodSize'])
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RestApiTransform implements ASTTransformation, CompilationUnitAware {
    private static final ClassNode MY_TYPE = new ClassNode(RestApi)
    public static final String ATTR_READY_ONLY = "readOnly"
    public static final String ATTR_SUPER_CLASS = "superClass"
    public static final String RESPOND_METHOD = "respond"
    public static final String ATTR_RESPONSE_FORMATS = "formats"
    public static final String ATTR_URI = "uri"
    public static final String PARAMS_VARIABLE = "params"
    public static final ConstantExpression CONSTANT_STATUS = new ConstantExpression(ARGUMENT_STATUS)
    public static final String ATTR_NAMESPACE = "namespace"
    public static final String RENDER_METHOD = "render"
    public static final String ARGUMENT_STATUS = "status"
    public static final String REDIRECT_METHOD = "redirect"
    public static final ClassNode AUTOWIRED_CLASS_NODE = new ClassNode(Autowired).getPlainNodeReference()
    private static final ClassNode OBJECT_CLASS = new ClassNode(Object.class)
    public static final String EXCEPTION_HANDLER_META_DATA_FIELD_NAME = '$exceptionHandlerMetaData'
    private static final String ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME = "ALLOWED_METHODS_HANDLED";
    public static final String VOID_TYPE = "void";

    public static final AnnotationNode ACTION_ANNOTATION_NODE = new AnnotationNode(
            new ClassNode(Action.class));

    private CompilationUnit unit

    //private static final ConfigObject CO = new ConfigSlurper().parse(getContents(new File("grails-app/conf/application.groovy"))) //grails.io.IOUtils has a better way to do this.
    //see https://github.com/9ci/grails-audit-trail/blob/master/audit-trail-plugin/src/main/groovy/gorm/AuditStampASTTransformation.java for some ideas on how we can tweak this.

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {

        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof ClassNode)) {
            throw new RuntimeException('Internal error: wrong types: $node.class / $parent.class')
        }

        ClassNode parent = (ClassNode) astNodes[1]
        AnnotationNode annotationNode = (AnnotationNode) astNodes[0]
        if (!MY_TYPE.equals(annotationNode.getClassNode())) {
            return
        }
        println ""
        String className = "${parent.name}${ControllerArtefactHandler.TYPE}"
        final File resource = IOUtils.findSourceFile(className)
        LinkableTransform.addLinkingMethods(parent)

        if (resource == null) {
            ClassNode<?> superClassNode
            Expression superClassAttribute = annotationNode.getMember(ATTR_SUPER_CLASS)
            if (superClassAttribute instanceof ClassExpression) {
                superClassNode = ((ClassExpression) superClassAttribute).getType()
            } else {
                superClassNode = ClassHelper.make(RestApiRepoController)
            }

            final ast = source.getAST()
            final newControllerClassNode = new ClassNode(className, PUBLIC, nonGeneric(superClassNode, parent))
            processMethods(newControllerClassNode, source)
            final transactionalAnn = new AnnotationNode(TransactionalTransform.MY_TYPE)
            transactionalAnn.addMember(ATTR_READY_ONLY, ConstantExpression.PRIM_TRUE)
            newControllerClassNode.addAnnotation(transactionalAnn)

            final readOnlyAttr = annotationNode.getMember(ATTR_READY_ONLY)
            boolean isReadOnly = readOnlyAttr != null && ((ConstantExpression) readOnlyAttr).trueExpression
            addConstructor(newControllerClassNode, parent, isReadOnly)

            List<ClassInjector> injectors = ArtefactTypeAstTransformation.findInjectors(ControllerArtefactHandler.TYPE, GrailsAwareInjectionOperation.getClassInjectors())

            ArtefactTypeAstTransformation.performInjection(source, newControllerClassNode, injectors.findAll {
                !(it instanceof ControllerActionTransformer)
            })

            if (unit) {
                TraitInjectionUtils.processTraitsForNode(source, newControllerClassNode, 'Controller', unit)
            }

            final responseFormatsAttr = annotationNode.getMember(ATTR_RESPONSE_FORMATS)
            final uriAttr = annotationNode.getMember(ATTR_URI)
            final namespaceAttr = annotationNode.getMember(ATTR_NAMESPACE)
            final domainPropertyName = GrailsNameUtils.getPropertyName(parent.getName())

            ListExpression responseFormatsExpression = new ListExpression()
            boolean hasHtml = false
            if (responseFormatsAttr != null) {
                if (responseFormatsAttr instanceof ConstantExpression) {
                    if (responseFormatsExpression.text.equalsIgnoreCase('html')) {
                        hasHtml = true
                    }
                    responseFormatsExpression.addExpression(responseFormatsAttr)
                } else if (responseFormatsAttr instanceof ListExpression) {
                    responseFormatsExpression = (ListExpression) responseFormatsAttr
                    for (Expression expr in responseFormatsExpression.expressions) {
                        if (expr.text.equalsIgnoreCase('html')) hasHtml = true; break
                    }
                }
            } else {
                responseFormatsExpression.addExpression(new ConstantExpression("json"))
                //responseFormatsExpression.addExpression(new ConstantExpression("xml"))
            }

            if (namespaceAttr != null) {
                final namespace = namespaceAttr?.getText()
                final namespaceField = new FieldNode('namespace', STATIC, ClassHelper.STRING_TYPE, newControllerClassNode, new ConstantExpression(namespace))
                newControllerClassNode.addField(namespaceField)
            }

            // if (uriAttr != null || namespaceAttr != null) {

            //     String uri = uriAttr?.getText()
            //     final namespace=namespaceAttr?.getText()
            //     if(uri || namespace) {
            //         final urlMappingsClassNode = new ClassNode(UrlMappings).getPlainNodeReference()

            //         final lazyInitField = new FieldNode('lazyInit', PUBLIC | STATIC | FINAL, ClassHelper.Boolean_TYPE,newControllerClassNode, new ConstantExpression(Boolean.FALSE))
            //         newControllerClassNode.addField(lazyInitField)

            //         final urlMappingsField = new FieldNode('$urlMappings', PRIVATE, urlMappingsClassNode,newControllerClassNode, null)
            //         newControllerClassNode.addField(urlMappingsField)
            //         final urlMappingsSetterParam = new Parameter(urlMappingsClassNode, "um")
            //         final controllerMethodAnnotation = new AnnotationNode(new ClassNode(ControllerMethod).getPlainNodeReference())
            //         MethodNode urlMappingsSetter = new MethodNode("setUrlMappings", PUBLIC, VOID_CLASS_NODE, [urlMappingsSetterParam] as Parameter[], null, new ExpressionStatement(new BinaryExpression(new VariableExpression(urlMappingsField.name),Token.newSymbol(Types.EQUAL, 0, 0), new VariableExpression(urlMappingsSetterParam))))
            //         final autowiredAnnotation = new AnnotationNode(AUTOWIRED_CLASS_NODE)
            //         autowiredAnnotation.addMember("required", ConstantExpression.FALSE)

            //         final qualifierAnnotation = new AnnotationNode(new ClassNode(Qualifier).getPlainNodeReference())
            //         qualifierAnnotation.addMember("value", new ConstantExpression("grailsUrlMappingsHolder"))
            //         urlMappingsSetter.addAnnotation(autowiredAnnotation)
            //         urlMappingsSetter.addAnnotation(qualifierAnnotation)
            //         urlMappingsSetter.addAnnotation(controllerMethodAnnotation)
            //         newControllerClassNode.addMethod(urlMappingsSetter)
            //         processVariableScopes(source, newControllerClassNode, urlMappingsSetter)

            //         final methodBody = new BlockStatement()

            //         final urlMappingsVar = new VariableExpression(urlMappingsField.name)

            //         MapExpression map=new MapExpression()
            //         if(uri){
            //             map.addMapEntryExpression(new MapEntryExpression(new ConstantExpression("resources"), new ConstantExpression(domainPropertyName)))
            //         }
            //         if(namespace){
            //             final namespaceField = new FieldNode('namespace', STATIC, ClassHelper.STRING_TYPE,newControllerClassNode, new ConstantExpression(namespace))
            //             newControllerClassNode.addField(namespaceField)
            //             if(map.getMapEntryExpressions().size()==0){
            //                 uri="/${namespace}/${domainPropertyName}"
            //                 map.addMapEntryExpression(new MapEntryExpression(new ConstantExpression("resources"), new ConstantExpression(domainPropertyName)))
            //             }
            //             map.addMapEntryExpression(new MapEntryExpression(new ConstantExpression("namespace"), new ConstantExpression(namespace)))
            //         }

            //         final resourcesUrlMapping = new MethodCallExpression(buildThisExpression(), uri, new MapExpression([ new MapEntryExpression(new ConstantExpression("resources"), new ConstantExpression(domainPropertyName))]))
            //         final urlMappingsClosure = new ClosureExpression(null, new ExpressionStatement(resourcesUrlMapping))

            //         def addMappingsMethodCall = applyDefaultMethodTarget(new MethodCallExpression(urlMappingsVar, "addMappings", urlMappingsClosure), urlMappingsClassNode)
            //         methodBody.addStatement(new IfStatement(new BooleanExpression(urlMappingsVar), new ExpressionStatement(addMappingsMethodCall),new EmptyStatement()))

            //         def initialiseUrlMappingsMethod = new MethodNode("initializeUrlMappings", PUBLIC, VOID_CLASS_NODE, ZERO_PARAMETERS, null, methodBody)
            //         initialiseUrlMappingsMethod.addAnnotation(new AnnotationNode(new ClassNode(PostConstruct).getPlainNodeReference()))
            //         initialiseUrlMappingsMethod.addAnnotation(controllerMethodAnnotation)
            //         newControllerClassNode.addMethod(initialiseUrlMappingsMethod)
            //         processVariableScopes(source, newControllerClassNode, initialiseUrlMappingsMethod)
            //     }
            // }

            final publicStaticFinal = PUBLIC | STATIC | FINAL

            newControllerClassNode.addProperty("scope", publicStaticFinal, ClassHelper.STRING_TYPE, new ConstantExpression("singleton"), null, null)
            newControllerClassNode.addProperty("responseFormats", publicStaticFinal, new ClassNode(List).getPlainNodeReference(), responseFormatsExpression, null, null)

            ArtefactTypeAstTransformation.performInjection(source, newControllerClassNode, injectors.findAll {
                it instanceof ControllerActionTransformer
            })
            new TransactionalTransform().visit(source, transactionalAnn, newControllerClassNode)
            newControllerClassNode.setModule(ast)

            final artefactAnnotation = new AnnotationNode(new ClassNode(Artefact))
            artefactAnnotation.addMember("value", new ConstantExpression(ControllerArtefactHandler.TYPE))
            newControllerClassNode.addAnnotation(artefactAnnotation)

            ast.classes.add(newControllerClassNode)
        }
    }

    private void processMethods(ClassNode classNode, SourceUnit source) {

        List<MethodNode> deferredNewMethods = new ArrayList<MethodNode>()

        Collection<MethodNode> exceptionHandlerMethods = getExceptionHandlerMethods(classNode, source)
        final FieldNode exceptionHandlerMetaDataField = classNode.getField(EXCEPTION_HANDLER_META_DATA_FIELD_NAME)
        if(exceptionHandlerMetaDataField == null || !exceptionHandlerMetaDataField.getDeclaringClass().equals(classNode)) {
            final ListExpression listOfExceptionHandlerMetaData = new ListExpression()
            for(final MethodNode exceptionHandlerMethod : exceptionHandlerMethods) {
                println exceptionHandlerMethod
                final Parameter[] parameters = exceptionHandlerMethod.getParameters()
                final Parameter firstParameter = parameters[0]
                final ClassNode firstParameterTypeClassNode = firstParameter.getType()
                final String exceptionHandlerMethodName = exceptionHandlerMethod.getName()
                final ArgumentListExpression defaultControllerExceptionHandlerMetaDataCtorArgs = new ArgumentListExpression()
                defaultControllerExceptionHandlerMetaDataCtorArgs.addExpression(new ConstantExpression(exceptionHandlerMethodName))
                defaultControllerExceptionHandlerMetaDataCtorArgs.addExpression(new ClassExpression(firstParameterTypeClassNode.getPlainNodeReference()))
                listOfExceptionHandlerMetaData.addExpression(new ConstructorCallExpression(new ClassNode(DefaultControllerExceptionHandlerMetaData.class), defaultControllerExceptionHandlerMetaDataCtorArgs))
            }
            classNode.addField(EXCEPTION_HANDLER_META_DATA_FIELD_NAME,
                    Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL, new ClassNode(List.class),
                    listOfExceptionHandlerMetaData)
        }


        for (MethodNode newMethod : exceptionHandlerMethods) {
            classNode.addMethod(newMethod)
        }
    }

    protected void wrapMethodBodyWithExceptionHandling(final ClassNode controllerClassNode, final MethodNode methodNode) {
        final BlockStatement catchBlockCode = new BlockStatement()
        final String caughtExceptionArgumentName = '$caughtException'
        final Expression caughtExceptionVariableExpression = new VariableExpression(caughtExceptionArgumentName)
        final Expression caughtExceptionTypeExpression = new PropertyExpression(caughtExceptionVariableExpression, "class")
        final Expression thisExpression = new VariableExpression("this")
        final MethodCallExpression getExceptionHandlerMethodCall = new MethodCallExpression(thisExpression, "getExceptionHandlerMethodFor", caughtExceptionTypeExpression)
        applyDefaultMethodTarget(getExceptionHandlerMethodCall, controllerClassNode)

        final ClassNode reflectMethodClassNode = new ClassNode(Method.class)
        final String exceptionHandlerMethodVariableName = '$method'
        final Expression exceptionHandlerMethodExpression = new VariableExpression(exceptionHandlerMethodVariableName, new ClassNode(Method.class))
        final Expression declareExceptionHandlerMethod = new DeclarationExpression(
                new VariableExpression(exceptionHandlerMethodVariableName, reflectMethodClassNode), Token.newSymbol(Types.EQUALS, 0, 0), getExceptionHandlerMethodCall)
        final ArgumentListExpression invokeArguments = new ArgumentListExpression()
        invokeArguments.addExpression(thisExpression)
        invokeArguments.addExpression(caughtExceptionVariableExpression)
        final MethodCallExpression invokeExceptionHandlerMethodExpression = new MethodCallExpression(new VariableExpression(exceptionHandlerMethodVariableName), "invoke", invokeArguments)
        applyDefaultMethodTarget(invokeExceptionHandlerMethodExpression, reflectMethodClassNode)

        final Statement returnStatement = new ReturnStatement(invokeExceptionHandlerMethodExpression)
        final Statement throwCaughtExceptionStatement = new ThrowStatement(caughtExceptionVariableExpression)
        final Statement ifExceptionHandlerMethodExistsStatement = new IfStatement(new BooleanExpression(exceptionHandlerMethodExpression), returnStatement, throwCaughtExceptionStatement)
        catchBlockCode.addStatement(new ExpressionStatement(declareExceptionHandlerMethod))
        catchBlockCode.addStatement(ifExceptionHandlerMethodExistsStatement)

        final CatchStatement catchStatement = new CatchStatement(new Parameter(new ClassNode(Exception.class), caughtExceptionArgumentName), catchBlockCode)
        final Statement methodBody = methodNode.getCode()

        BlockStatement tryBlock = new BlockStatement()
        BlockStatement codeToHandleAllowedMethods = getCodeToHandleAllowedMethods(controllerClassNode, methodNode.getName())
        tryBlock.addStatement(codeToHandleAllowedMethods)
        tryBlock.addStatement(methodBody)

        final TryCatchStatement tryCatchStatement = new TryCatchStatement(tryBlock, new EmptyStatement())
        tryCatchStatement.addCatch(catchStatement)

        final ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        argumentListExpression.addExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME))

        final PropertyExpression requestPropertyExpression = new PropertyExpression(new VariableExpression("this"), "request")
        final Expression removeAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "removeAttribute", argumentListExpression)

        final Expression getAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "getAttribute", new ArgumentListExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME)))
        final VariableExpression attributeValueExpression = new VariableExpression('$allowed_methods_attribute_value', ClassHelper.make(Object.class))
        final Expression initializeAttributeValue = new DeclarationExpression(
                attributeValueExpression, Token.newSymbol(Types.EQUALS, 0, 0), getAttributeMethodCall)
        final Expression attributeValueMatchesMethodNameExpression = new BinaryExpression(new ConstantExpression(methodNode.getName()),
                Token.newSymbol(Types.COMPARE_EQUAL, 0, 0),
                attributeValueExpression)
        final Statement ifAttributeValueMatchesMethodName =
                new IfStatement(new BooleanExpression(attributeValueMatchesMethodNameExpression),
                        new ExpressionStatement(removeAttributeMethodCall), new EmptyStatement())

        final BlockStatement blockToRemoveAttribute = new BlockStatement()
        blockToRemoveAttribute.addStatement(new ExpressionStatement(initializeAttributeValue))
        blockToRemoveAttribute.addStatement(ifAttributeValueMatchesMethodName)

        final TryCatchStatement tryCatchToRemoveAttribute = new TryCatchStatement(blockToRemoveAttribute, new EmptyStatement())
        tryCatchToRemoveAttribute.addCatch(new CatchStatement(new Parameter(ClassHelper.make(Exception.class), '$exceptionRemovingAttribute'), new EmptyStatement()))

        tryCatchStatement.setFinallyStatement(tryCatchToRemoveAttribute)

        methodNode.setCode(tryCatchStatement)
    }

    protected BlockStatement getCodeToHandleAllowedMethods(ClassNode controllerClass, String methodName) {
        GrailsASTUtils.addEnhancedAnnotation(controllerClass, DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY);
        final BlockStatement checkAllowedMethodsBlock = new BlockStatement();

        final PropertyExpression requestPropertyExpression = new PropertyExpression(new VariableExpression("this"), "request");

        final FieldNode allowedMethodsField = controllerClass.getField(DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY);

        if(allowedMethodsField != null) {
            final Expression initialAllowedMethodsExpression = allowedMethodsField.getInitialExpression();
            if(initialAllowedMethodsExpression instanceof MapExpression) {
                boolean actionIsRestricted = false;
                final MapExpression allowedMethodsMapExpression = (MapExpression) initialAllowedMethodsExpression;
                final List<MapEntryExpression> allowedMethodsMapEntryExpressions = allowedMethodsMapExpression.getMapEntryExpressions();
                for(MapEntryExpression allowedMethodsMapEntryExpression : allowedMethodsMapEntryExpressions) {
                    final Expression allowedMethodsMapEntryKeyExpression = allowedMethodsMapEntryExpression.getKeyExpression();
                    if(allowedMethodsMapEntryKeyExpression instanceof ConstantExpression) {
                        final ConstantExpression allowedMethodsMapKeyConstantExpression = (ConstantExpression) allowedMethodsMapEntryKeyExpression;
                        final Object allowedMethodsMapKeyValue = allowedMethodsMapKeyConstantExpression.getValue();
                        if(methodName.equals(allowedMethodsMapKeyValue)) {
                            actionIsRestricted = true;
                            break;
                        }
                    }
                }
                if(actionIsRestricted) {
                    final PropertyExpression responsePropertyExpression = new PropertyExpression(new VariableExpression("this"), "response");

                    final ArgumentListExpression isAllowedArgumentList = new ArgumentListExpression();
                    isAllowedArgumentList.addExpression(new ConstantExpression(methodName));
                    isAllowedArgumentList.addExpression(new PropertyExpression(new VariableExpression("this"), "request"));
                    isAllowedArgumentList.addExpression(new PropertyExpression(new VariableExpression("this"), DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY));
                    final Expression isAllowedMethodCall = new StaticMethodCallExpression(ClassHelper.make(AllowedMethodsHelper.class), "isAllowed", isAllowedArgumentList);
                    final BooleanExpression isValidRequestMethod = new BooleanExpression(isAllowedMethodCall);
                    final MethodCallExpression sendErrorMethodCall = new MethodCallExpression(responsePropertyExpression, "sendError", new ConstantExpression(HttpServletResponse.SC_METHOD_NOT_ALLOWED));
                    final ReturnStatement returnStatement = new ReturnStatement(new ConstantExpression(null));
                    final BlockStatement blockToSendError = new BlockStatement();
                    blockToSendError.addStatement(new ExpressionStatement(sendErrorMethodCall));
                    blockToSendError.addStatement(returnStatement);
                    final IfStatement ifIsValidRequestMethodStatement = new IfStatement(isValidRequestMethod, new ExpressionStatement(new EmptyExpression()), blockToSendError);

                    checkAllowedMethodsBlock.addStatement(ifIsValidRequestMethodStatement);
                }
            }
        }

        final ArgumentListExpression argumentListExpression = new ArgumentListExpression();
        argumentListExpression.addExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME));
        argumentListExpression.addExpression(new ConstantExpression(methodName));

        final Expression setAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "setAttribute", argumentListExpression);

        final BlockStatement codeToExecuteIfAttributeIsNotSet = new BlockStatement();
        codeToExecuteIfAttributeIsNotSet.addStatement(new ExpressionStatement(setAttributeMethodCall));
        codeToExecuteIfAttributeIsNotSet.addStatement(checkAllowedMethodsBlock);

        final BooleanExpression attributeIsSetBooleanExpression = new BooleanExpression(new MethodCallExpression(requestPropertyExpression, "getAttribute", new ArgumentListExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME))));
        final Statement ifAttributeIsAlreadySetStatement = new IfStatement(attributeIsSetBooleanExpression, new EmptyStatement(), codeToExecuteIfAttributeIsNotSet);

        final BlockStatement code = new BlockStatement();
        code.addStatement(ifAttributeIsAlreadySetStatement);

        code
    }


    protected Collection<MethodNode> getExceptionHandlerMethods(final ClassNode classNode, SourceUnit sourceUnit) {
        final Map<ClassNode, MethodNode> exceptionTypeToHandlerMethodMap = new HashMap<ClassNode, MethodNode>()
        final List<MethodNode> methods = classNode.getMethods()
        for(MethodNode methodNode : methods) {

            if(isExceptionHandlingMethod(methodNode)) {
                final Parameter exceptionParameter = methodNode.getParameters()[0]
                final ClassNode exceptionType = exceptionParameter.getType()
                if(!exceptionTypeToHandlerMethodMap.containsKey(exceptionType)) {

                    exceptionTypeToHandlerMethodMap.put(exceptionType, methodNode)
                } else {
                    final MethodNode otherHandlerMethod = exceptionTypeToHandlerMethodMap.get(exceptionType)
                    final String message = "A controller may not define more than 1 exception handler for a particular exception type.  [%s] defines the [%s] and [%s] exception handlers which each accept a [%s] which is not allowed."
                    final String formattedMessage = String.format(message, classNode.getName(), otherHandlerMethod.getName(), methodNode.getName(), exceptionType.getName())
                    GrailsASTUtils.error(sourceUnit, methodNode, formattedMessage)
                }
            }
        }
        final ClassNode superClass = classNode.getSuperClass()
        if(!superClass.equals(OBJECT_CLASS)) {
            final Collection<MethodNode> superClassMethods = getExceptionHandlerMethods(superClass, sourceUnit)
            for(MethodNode superClassMethod : superClassMethods) {
                final Parameter exceptionParameter = superClassMethod.getParameters()[0]
                final ClassNode exceptionType = exceptionParameter.getType()
                // only add this super class handler if we don't already have
                // a handler for this exception type in this class
                if(!exceptionTypeToHandlerMethodMap.containsKey(exceptionType)) {
                    exceptionTypeToHandlerMethodMap.put(exceptionType, superClassMethod)
                }
            }
        }
        exceptionTypeToHandlerMethodMap.values()
    }


    private boolean isExceptionHandlingMethod(MethodNode methodNode) {
        boolean isExceptionHandler = false
        if(!methodNode.isPrivate() && methodNode.getName().indexOf('$') == -1) {
            Parameter[] parameters = methodNode.getParameters()
            if(parameters.length == 1) {
                ClassNode parameterTypeClassNode = parameters[0].getType()
                isExceptionHandler = parameterTypeClassNode.isDerivedFrom(new ClassNode(Exception.class))
            }
        }
        isExceptionHandler
    }

    protected boolean methodShouldBeConfiguredAsControllerAction(final MethodNode method) {
        int minLineNumber = 0;
        if (isInheritedFromTrait(method) && hasAnnotation(method, Action.class) && hasParameters(method)) {
            removeAnnotation(method, Action.class);
            //Trait methods have a line number of -1
            --minLineNumber;
        }
        return !method.isStatic() &&
                method.isPublic() &&
                !method.isAbstract() &&
                method.getAnnotations(ACTION_ANNOTATION_NODE.getClassNode()).isEmpty() &&
                method.getAnnotations(new ClassNode(ControllerMethod.class)).isEmpty() &&
                method.getLineNumber() >= minLineNumber &&
                !method.getName().startsWith('$') &&
                !method.getReturnType().getName().equals(VOID_TYPE) &&
                !isExceptionHandlingMethod(method)
    }



    ConstructorNode addConstructor(ClassNode controllerClassNode, ClassNode domainClassNode, boolean readOnly) {
        BlockStatement constructorBody = new BlockStatement()
        constructorBody.addStatement(new ExpressionStatement(new ConstructorCallExpression(ClassNode.SUPER, new TupleExpression(new ClassExpression(domainClassNode), new ConstantExpression(readOnly, true)))))
        controllerClassNode.addConstructor(Modifier.PUBLIC, ZERO_PARAMETERS, ClassNode.EMPTY_ARRAY, constructorBody)
    }

    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }
}
