package gorm.restapi.controller

import gorm.tools.repository.errors.EntityNotFoundException
import gorm.tools.repository.errors.EntityValidationException
import grails.validation.ValidationException

/**
 *  Adds controller error handlers
 *
 *  Created by alexeyzvegintcev.
 */
trait RestControllerErrorHandling {


    def handleEntityNotFoundException(EntityNotFoundException e) {
        render(status: 404, e.message)
    }

    def handleEntityValidationException(EntityValidationException e){
        render(status: 422, e.message)
    }

    def handleValidationException(ValidationException e){
        render(status: 422, e.message)
    }

}