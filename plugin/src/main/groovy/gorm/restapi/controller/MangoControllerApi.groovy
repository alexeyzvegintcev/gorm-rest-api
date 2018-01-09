package gorm.restapi.controller

import gorm.tools.mango.api.MangoQueryApi
import grails.gorm.DetachedCriteria
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

/**
 *  Adds controller methods for list
 *
 *  Created by alexeyzvegintcev.
 */
trait MangoControllerApi {

    abstract Class getResource()

    @Autowired
    @Qualifier("mango")
    MangoQueryApi mangoQuery

    DetachedCriteria buildCriteria(Map params = [:], Closure closure = null) {
        getMangoQuery().buildCriteria(getResource(), params, closure)
    }

    List query(Map params = [:], Closure closure = null) {
        getMangoQuery().query(getResource(), params, closure)
    }

}
