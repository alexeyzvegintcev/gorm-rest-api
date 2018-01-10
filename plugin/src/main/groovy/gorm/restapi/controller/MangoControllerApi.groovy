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

    abstract Class getEntityClass()

    @Autowired
    @Qualifier("mango")
    MangoQueryApi mangoQuery

    DetachedCriteria buildCriteria(Map criteriaParams=[:], Map params = [:], Closure closure = null) {
        getMangoQuery().buildCriteria(getEntityClass(), criteriaParams + params, closure)
    }

    List query(Map criteriaParams=[:], Map params = [:], Closure closure = null) {
        getMangoQuery().query(getEntityClass(), criteriaParams + params, closure)
    }

}
