package gorm.restapi

import gorm.tools.GormMetaUtils
import grails.core.DefaultGrailsApplication
import grails.gorm.validation.ConstrainedProperty
import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.validation.discovery.ConstrainedDiscovery
import org.springframework.core.annotation.AnnotationUtils

import javax.annotation.Resource

import static grails.util.GrailsClassUtils.getStaticPropertyValue

/**
 * Generates the domain part
 * should be merged with either Swaggydocs or Springfox as outlined
 * https://github.com/OAI/OpenAPI-Specification is the new openAPI that
 * Swagger moved to.
 * We are chasing this part https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#schemaObject
 * Created by JBurnett on 6/19/17.
 */
//@CompileStatic
@SuppressWarnings(['UnnecessaryGetter', 'NoDef', 'AbcMetric'])
class JsonSchemaGenerator {

    @Resource
    HibernateMappingContext persistentEntityMappingContext

    @Resource
    DefaultGrailsApplication grailsApplication

    //good overview here
    //https://spacetelescope.github.io/understanding-json-schema/index.html
    //https://docs.spring.io/spring-data/rest/docs/current/reference/html/#metadata.json-schema
    //https://github.com/OAI/OpenAPI-Specification
    Map generate(Class clazz) {
        return generate(clazz.name)
    }

    Map generate(String domainName) {
        println(domainName)
        PersistentEntity domClass = GormMetaUtils.getPersistentEntity(domainName)
        Map schema = [:]
        schema['$schema'] = "http://json-schema.org/schema#"
        schema['$id'] = "http://localhost:8080/schema/${domClass.getDecapitalizedName().capitalize()}.json"
        schema["definitions"] = [:]
        schema.putAll generate(domClass, schema)
        return schema
    }

    Map generate(PersistentEntity domainClass, Map schema) {
        Map map = [:]
        //TODO figure out a more performant way to do these if
        Mapping mapping = getMapping(domainClass.name)

        //Map cols = mapping.columns
        map.title = domainClass.name //TODO Should come from application.yml !?

        if (mapping?.comment) map.description = mapping.comment

        if (AnnotationUtils.findAnnotation(domainClass.class, RestApi.class)) {
            map.description = AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(domainClass.class, RestApi.class)).description
        }

        map.type = 'Object'
        map.required = []

        def (props, required) = getDomainProperties(domainClass, schema)

        map.properties = props
        map.required = required

        return map
    }

    private List getDomainProperties(PersistentEntity domClass, Map schema) {
        String domainName = GrailsNameUtils.getPropertyNameRepresentation(domClass.name)
        Map<String, String> map = [:]
        List required = []

        PersistentProperty idProp = domClass.getIdentity()

        //id
        map[idProp.name] = [type: getJsonType(idProp.type).type, readOnly: true]

        //version
        if (domClass.version) map[domClass.version.name] = [type: 'integer', readOnly: true]

        Mapping mapping = getMapping(domainName)
        List<PersistentProperty> props = resolvePersistentProperties(domClass)

        for (PersistentProperty prop : props) {
            ConstrainedProperty constraints = getConstrainedProperties(domClass).get(prop.name)
            //Map mappedBy = domClass.mappedBy
            if (!constraints.display) continue //skip if display is false
            if (prop instanceof Association) {
                PersistentEntity referencedDomainClass = GormMetaUtils.getPersistentEntity(prop.type)
                if (/*(prop.isManyToOne() || prop.isOneToOne()) && */ !schema.definitions.containsKey(referencedDomainClass.name)) {
                    if (referencedDomainClass.javaClass.isAnnotationPresent(RestApi)) {
                        //treat as a seperate file
                        map[prop.name] = ['$ref': "${prop.owner.name}.json"]
                    } else {
                        //treat as definition in same schema
                        schema.definitions[referencedDomainClass.getDecapitalizedName().capitalize()] = [:]
                        schema.definitions[referencedDomainClass.getDecapitalizedName().capitalize()] = generate(referencedDomainClass, schema)
                        map[prop.name] = ['$ref': "#/definitions/$prop.owner.name"]
                    }

                    if (!constraints.isNullable() && constraints.editable) {
                        required.add(prop.name)
                    }
                }
            } else {
                Map jprop = [:]
                //jprop.title = prop.naturalName
                jprop.title = getMetaConstraintValue(constraints, "title") ?: prop.name
                //title override
                //def metaConstraints = constraints.getMetaConstraintValue()metaConstraints
                //if(constraints.attributes?.title) jprop.title = constraints.attributes.title
                //if(constraints.getMetaConstraintValue("title"))
                String description = getMetaConstraintValue(constraints, "description")
                if (description) jprop.description = description

                //Example
                String example = getMetaConstraintValue(constraints, "example")
                if (example) jprop.example = example

                //type
                Map typeFormat = getJsonType(constraints.propertyType)
                jprop.type = typeFormat.type
                //format
                if (typeFormat.format) jprop.format = typeFormat.format
                if (typeFormat.enum) jprop.enum = typeFormat.enum
                //format override from constraints
                if (constraints.property.format) jprop.format = constraints.property.format
                //if (constraints.property?.appliedConstraints?.email) jprop.format = 'email'
                //pattern TODO

                //defaults
                String defVal = getDefaultValue(mapping, prop.name)
                if (defVal != null) jprop.default = defVal //TODO convert to string?

                //required
                if (!constraints.isNullable() && constraints.editable) {
                    //TODO update this so it can use config too
                    if (prop.name in ['dateCreated', 'lastUpdated']) {
                        jprop.readOnly = true
                    }
                    //if its nullable:false but has a default then its not required as it will get filled in.
                    else if (jprop.default == null) {
                        jprop.required = true
                        required.add(prop.name)
                    }
                }
                //readOnly
                if (!constraints.editable) jprop.readOnly = true
                //default TODO
                //minLength
                if (constraints.getMaxSize()) jprop.maxLength = constraints.getMaxSize()
                //maxLength
                if (constraints.getMinSize()) jprop.minLength = constraints.getMinSize()

                if (constraints.getMin() != null) jprop.minimum = constraints.getMin()
                if (constraints.getMax() != null) jprop.maximum = constraints.getMax()
                if (constraints.getScale() != null) jprop.multipleOf = 1 / Math.pow(10, constraints.getScale())

                map[prop.name] = jprop
            }

            //def typeFormat = getJsonType(constraints)
            //map.properties[prop.pathFromRoot] = typeFormat

        }

        return [map, required]
    }

    String getDefaultValue(Mapping mapping, String propName) {
        mapping?.columns?."$propName"?.columns?.getAt(0)?.defaultValue
        //cols[prop.name]?.columns?.getAt(0)?.defaultValue
    }

    String getMetaConstraintValue(ConstrainedProperty constraints, String name) {
        constraints?.property?.metaConstraints?."$name"
    }

    @CompileDynamic
    PersistentEntity getDomainClass(String domainName) {
        grailsApplication.domainClasses.find { it.naturalName == domainName } as PersistentEntity
    }

    @CompileDynamic
    Mapping getMapping(String domainName) {
        PersistentEntity pe = getDomainClass(domainName)
        return persistentEntityMappingContext.mappingFactory?.entityToMapping?.get(pe)
    }
    /* see http://epoberezkin.github.io/ajv/#formats */
    /* We are adding 'money' and 'date' as formats too
     * big decimal defaults to money
     */

    protected Map getJsonType(Class propertyType) {
        Map typeFormat = [type: 'string']
        switch (propertyType) {
            case [Boolean, Byte]:
                typeFormat.type = 'boolean'
                break
            case [Integer, Long, Short]:
                typeFormat.type = 'integer'
                break
            case [Double, Float, BigDecimal]:
                typeFormat.type = 'number'
                break
            case [BigDecimal]:
                typeFormat.type = 'number'
                typeFormat.format = 'money'
                break
            case [java.time.LocalDate]:
                typeFormat.type = 'string'
                //date. verified to be a date of the format YYYY-MM-DD
                typeFormat.format = 'date'
                break
            case [Date]:
                //date-time. verified to be a valid date and time in the format YYYY-MM-DDThh:mm:ssZ
                typeFormat.type = 'string'
                typeFormat.format = 'date-time'
                break
            case [String]:
                typeFormat.type = 'string'
                break
            case { it.isEnum() }:
                typeFormat.type = 'string'
                typeFormat.enum = propertyType.values()*.name() as String[]

        }
        //TODO what about types like Byte etc..?
        return typeFormat
    }

    //copied from FormFieldsTagLib in the Fields plugin
    @CompileDynamic
    private List<PersistentProperty> resolvePersistentProperties(PersistentEntity domainClass, Map attrs = [:]) {
        List properties
        if (attrs.order) {
            def orderBy = attrs.order?.tokenize(',')*.trim() ?: []
            properties = orderBy.collect { propertyName -> domainClass.getPersistentProperty(propertyName) }
        } else {
            properties = domainClass.persistentProperties as List
            def blacklist = attrs.except?.tokenize(',')*.trim() ?: []
            //blacklist << 'dateCreated' << 'lastUpdated'
            Map scaffoldProp = getStaticPropertyValue(domainClass.class, 'scaffold')
            if (scaffoldProp) {
                blacklist.addAll(scaffoldProp.exclude)
            }
            properties.removeAll { it.name in blacklist }
            properties.removeAll { !getConstrainedProperties(it.owner)[it.name]?.display }
            properties.removeAll { it.propertyMapping.mappedForm.derived }

            //Collections.sort(properties, new org.grails.validation.DomainClassPropertyComparator(domainClass))
        }

        return properties
    }

    Map getConstrainedProperties(PersistentEntity persistentEntity) {
        Map constrainedProperties = [:]
        ConstrainedDiscovery constrainedDiscovery = GrailsFactoriesLoader.loadFactory(ConstrainedDiscovery.class)
        constrainedProperties = constrainedDiscovery.findConstrainedProperties(persistentEntity)
        return constrainedProperties
    }
}
