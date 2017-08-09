package gorm.restapi

import grails.converters.JSON

import grails.artefact.Artefact
//import grails.transaction.ReadOnly
//import grails.gorm.transactions.Transactional
import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import grails.web.http.HttpHeaders
import org.springframework.http.HttpStatus

import static org.springframework.http.HttpStatus.*

/**
 *
 * A Conroller for a RestApi. Can be extended and gets generated by default using
 * the @RestApi on the domain
 *
 * @author Joshua Burnett
 *
 * based on Grails' RestFullController
 */
@Artefact("Controller")
class RestApiDomainController<T> {
    static allowedMethods = [save: "POST", update: ["PUT", "POST"], patch: "PATCH", delete: "DELETE"]

    static responseFormats = ['json']
    static namespace = 'api'

    Class<T> resource
    String resourceName
    String resourceClassName
    boolean readOnly

    RestApiDomainController(Class<T> resource) {
        this(resource, false)
    }

    RestApiDomainController(Class<T> resource, boolean readOnly) {
        this.resource = resource
        this.readOnly = readOnly
        resourceClassName = resource.simpleName
        resourceName = GrailsNameUtils.getPropertyName(resource)
    }

    Class getDomainClass() {
        resource
    }


    /**
     * Lists all resources up to the given maximum
     *
     * @param max The maximum
     * @return A list of resources
     */
    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond listAllResources(params), model: [("${resourceName}Count".toString()): countResources()]
    }

    /**
     * Shows a single resource
     * @param id The id of the resource
     * @return The rendered resource or a 404 if it doesn't exist
     */
    def show() {
        respond queryForResource(params.id)
    }

    /**
     * Saves a resource
     */
    @Transactional
    def save() {
        if(handleReadOnly()) {
            return
        }
        def instance = createResource()

        instance.validate()
        if (instance.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond instance.errors, view:'create' // STATUS CODE 422
            return
        }

        saveResource instance

        response.addHeader(HttpHeaders.LOCATION,
                        grailsLinkGenerator.link( resource: this.controllerName, action: 'show',id: instance.id, absolute: true,
                                            namespace: hasProperty('namespace') ? this.namespace : null ))
        respond instance, [status: CREATED, view:'show']
    }

    /**
     * Updates a resource for the given id
     * @param id
     */
    @Transactional
    def patch() {
        update()
    }

    /**
     * Updates a resource for the given id
     * @param id
     */
    @Transactional
    def update() {
        if(handleReadOnly()) {
            return
        }

        T instance = queryForResource(params.id)
        if (instance == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        instance.properties = getObjectToBind()

        if (instance.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond instance.errors, view:'edit' // STATUS CODE 422
            return
        }

        updateResource instance

        response.addHeader(HttpHeaders.LOCATION,
                grailsLinkGenerator.link( resource: this.controllerName, action: 'show',id: instance.id, absolute: true,
                                    namespace: hasProperty('namespace') ? this.namespace : null ))
        respond instance, [status: OK]

    }

    /**
     * Deletes a resource for the given id
     * @param id The id
     */
    @Transactional
    def delete() {
        if(handleReadOnly()) {
            return
        }

        def instance = queryForResource(params.id)
        if (instance == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        deleteResource instance
        render status: NO_CONTENT
    }

    /**
     * handles the request for write methods (create, edit, update, save, delete) when controller is in read only mode
     *
     * @return true if controller is read only
     */
    protected boolean handleReadOnly() {
        if(readOnly) {
            render status: HttpStatus.METHOD_NOT_ALLOWED.value()
            return true
        } else {
            return false
        }
    }

    /**
     * The object that can be bound to a domain instance.  Defaults to the request.  Subclasses may override this
     * method to return anything that is a valid second argument to the bindData method in a controller.  This
     * could be the request, a {@link java.util.Map} or a {@link org.grails.databinding.DataBindingSource}.
     *
     * @return the object to bind to a domain instance
     */
    protected getObjectToBind() {
        request
    }

    /**
     * Queries for a resource for the given id
     *
     * @param id The id
     * @return The resource or null if it doesn't exist
     */
    protected T queryForResource(Serializable id) {
        resource.get(id)
    }

    /**
     * Creates a new instance of the resource for the given parameters
     *
     * @param params The parameters
     * @return The resource instance
     */
    protected T createResource(Map params) {
        resource.newInstance(params)
    }

    /**
     * Creates a new instance of the resource.  If the request
     * contains a body the body will be parsed and used to
     * initialize the new instance, otherwise request parameters
     * will be used to initialized the new instance.
     *
     * @return The resource instance
     */
    protected T createResource() {
        T instance = resource.newInstance()
        bindData instance, getObjectToBind()
        instance
    }

    /**
     * List all of resource based on parameters
     *
     * @return List of resources or empty if it doesn't exist
     */
    protected List<T> listAllResources(Map params) {
        resource.list(params)
    }

    /**
     * Counts all of resources
     *
     * @return List of resources or empty if it doesn't exist
     */
    protected Integer countResources() {
        resource.count()
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [classMessageArg, params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    /**
     * Saves a resource
     *
     * @param resource The resource to be saved
     * @return The saved resource or null if can't save it
     */
    protected T saveResource(T resource) {
        resource.save flush: true
    }

    /**
     * Updates a resource
     *
     * @param resource The resource to be updated
     * @return The updated resource or null if can't save it
     */
    protected T updateResource(T resource) {
        saveResource resource
    }

    /**
     * Deletes a resource
     *
     * @param resource The resource to be deleted
     */
    protected void deleteResource(T resource) {
        resource.delete flush: true
    }

    protected String getClassMessageArg() {
        message(code: "${resourceName}.label".toString(), default: resourceClassName)
    }

}

