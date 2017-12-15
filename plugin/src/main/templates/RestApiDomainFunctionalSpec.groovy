package ${packageName}

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import gorm.restapi.testing.RestApiFuncSpec

@Integration
@Rollback
class ${className}RestApiSpec extends RestApiFuncSpec {

	Class<${className}> domainClass = ${className}
	// set to true if you have configured the _error.gson or controller
	// to return application/vnd.error vs application/json
	boolean vndHeaderOnError = false

    String getResourcePath() {
        "\${baseUrl}api/${propertyName}"
    }

    //data to force a post or patch failure
    Map getInvalidData() { [fooName: null] }

    //Override if you don't want to use the autogenerated Example data.
    //Map getInsertData() {[ fooName: "project", code: "x123"]}

    //Map getUpdateData() { [fooName: "project Update", code: "x123u"]}


}
