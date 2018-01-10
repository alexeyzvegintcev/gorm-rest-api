package gorm.restapi.controller

import gorm.tools.mango.api.MangoQueryApi
import gorm.tools.repository.GormRepoEntity
import gorm.tools.repository.api.RepositoryApi
import grails.artefact.Artefact

//import gorm.tools.Pager
import grails.converters.JSON
import grails.core.GrailsApplication
import gorm.tools.repository.errors.RepoExceptionSupport
import grails.util.GrailsNameUtils
import grails.validation.ValidationException
import org.apache.commons.lang.StringEscapeUtils
import org.springframework.context.MessageSource

/**
 * Credits: took rally.BaseDomainController with core concepts from grails RestfulConroller
 * Some of this is, especailly the cache part is lifted from the older grails2 restful-api plugin
 *
 * @author Joshua Burnett
 */
//see grails-core/grails-plugin-rest/src/main/groovy/grails/artefact/controller/RestResponder.groovy
// we can get some good ideas from how that plugin does things
@SuppressWarnings(['CatchException', 'NoDef', 'ClosureAsLastMethodParameter', 'FactoryMethodName'])
@Artefact("Controller")
class RestApiRepoController<D extends GormRepoEntity> implements RestRepositoryApi<D> {
    static allowedMethods = [list  : ["GET", "POST"], create: "POST",
                             update: ["PUT", "PATCH"], delete: "DELETE"]

    static responseFormats = ['json']
    static namespace = 'api'


    //AppSetupService appSetupService
    GrailsApplication grailsApplication

    RestApiRepoController(Class<D> resource) {
        this(resource, false)
    }

    RestApiRepoController(Class<D> resource, boolean readOnly) {
        this.resource = resource
        this.readOnly = readOnly
        resourceClassName = resource.simpleName
        resourceName = GrailsNameUtils.getPropertyName(resource)
    }


    MangoQueryApi getMangoQuery() {
        getRepo().mangoQuery
    }

//    @PostConstruct
//    protected void init(){
//        //println "init called and ga is ${grailsApplication?'initialized':'null'}"
//    }

    protected String getDomainInstanceName() {
        def suffix = grailsApplication.config?.grails?.scaffolding?.templates?.domainSuffix
        if (!suffix) {
            suffix = ''
        }
        def propName = GrailsNameUtils.getPropertyNameRepresentation(domainClass)
        "${propName}${suffix}"
    }


// ---------------------------------- ACTIONS ---------------------------------

    // GET /api/resource
    //
    def list() {

        log.trace "list invoked for ${params.pluralizedResourceName} - request_id=${request.request_id}"
        try {
            //cache headers
            def requestParams = params
            def logger = log

            def result

            if (request.method == "POST") {
                //request.body will possibly have the criteria
                result = listPost(request.body, requestParams)
            } else if (request.method == "GET") {
                result = listGet(requestParams)
            }

            respond result

        }
        catch (e) {
            logMessageError(e)
            renderErrorResponse(e)
        }
    }

    /**
     * returns the list of domain obects
     */
    protected def listPost(body, requestParams) {
        query(body as Map, requestParams as Map)
    }

    /**
     * returns the list of domain obects
     */
    protected def listGet(requestParams) {
        query(requestParams as Map)
    }
}
