package restify

import gorm.restapi.testing.RestApiFuncSpec
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback

import static org.springframework.http.HttpStatus.CREATED

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

    Map getNewInsertData(){
        Map newMap = getInsertData()
        newMap.title = newMap.title +1
        newMap
    }


    void test_save_post() {
        given:
        def response

        when: "The save action is executed with valid data"
        response = restBuilder.post(resourcePath) {
            json insertData
        }

        then: "The response is correct"
        response.status == CREATED.value()
        verifyHeaders(response)
        subsetEquals(newInsertData, response.json)
        def rget = restBuilder.get("$resourcePath/${response.json.id}")
        subsetEquals(newInsertData, rget.json)
    }

}
