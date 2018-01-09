package gorm.restapi.controller

import gorm.tools.repository.errors.EntityNotFoundException
import gorm.tools.repository.errors.EntityValidationException

/**
 *  Adds controller methods for error handling
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

}