package restify

import gorm.restapi.testing.RestApiFuncSpec
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback

@Integration
@Rollback
class BookRestApiSpec extends RestApiFuncSpec {

    Class<Book> domainClass = Book
    boolean vndHeaderOnError = false

    String getResourcePath() {
        "${baseUrl}api/book"
    }

    //data to force a post or patch failure
    Map getInvalidData() { [title: null] }

    //Override if you don't want to use the autogenerated Example data.
    Map getInsertData() { [title: "project"] }

    Map getUpdateData() { [title: "project Update"] }


}
