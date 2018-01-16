package gorm.restapi.controller

import gorm.tools.repository.errors.EntityNotFoundException
import gorm.tools.repository.errors.EntityValidationException
import grails.util.GrailsClassUtils
import grails.validation.ValidationException
import org.grails.compiler.web.ControllerActionTransformer
import org.grails.plugins.web.controllers.ControllerExceptionHandlerMetaData
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.validation.Errors

import java.lang.reflect.Method

import static org.springframework.http.HttpStatus.*

/**
 *  Adds controller error handlers
 *
 *  Created by alexeyzvegintcev.
 */
trait RestControllerErrorHandling extends RestControllerLogging {


    def handleException1(EntityNotFoundException e) {
        render(status: NOT_FOUND, e.message)
    }

    def handleException2(EntityValidationException e){
        render(status: UNPROCESSABLE_ENTITY, e.message)
    }

    def handleException3(ValidationException e){
        render(status: UNPROCESSABLE_ENTITY, e.message)
    }

    def handleException4(OptimisticLockingFailureException e){
        render(status: CONFLICT, e.message)
    }

    def handleException(RuntimeException e){
        throw e
    }

    String buildMessage(Errors errors){

    }

    @SuppressWarnings("unchecked")
    Method getExceptionHandlerMethodFor(final Class<? extends Exception> exceptionType) throws Exception {
        if(!Exception.class.isAssignableFrom(exceptionType)) {
            throw new IllegalArgumentException("exceptionType [${exceptionType.getName()}] argument must be Exception or a subclass of Exception")
        }

        Method handlerMethod
        final List<ControllerExceptionHandlerMetaData> exceptionHandlerMetaDataInstances = (List<ControllerExceptionHandlerMetaData>)GrailsClassUtils.getStaticFieldValue(this.getClass(), ControllerActionTransformer.EXCEPTION_HANDLER_META_DATA_FIELD_NAME)
        if(exceptionHandlerMetaDataInstances) {

            // find all of the handler methods which could accept this exception type
            final List<ControllerExceptionHandlerMetaData> matches = (List<ControllerExceptionHandlerMetaData>)exceptionHandlerMetaDataInstances.findAll { ControllerExceptionHandlerMetaData cemd ->
                cemd.exceptionType.isAssignableFrom(exceptionType)
            }

            if(matches.size() > 0) {
                ControllerExceptionHandlerMetaData theOne = matches.get(0)

                // if there are more than 1, find the one that is farthest down the inheritance hierarchy
                for(int i = 1; i < matches.size(); i++) {
                    final ControllerExceptionHandlerMetaData nextMatch = matches.get(i)
                    if(theOne.getExceptionType().isAssignableFrom(nextMatch.getExceptionType())) {
                        theOne = nextMatch
                    }
                }
                handlerMethod = this.getClass().getMethod(theOne.getMethodName(), theOne.getExceptionType())
            }
        }

        handlerMethod
    }
}