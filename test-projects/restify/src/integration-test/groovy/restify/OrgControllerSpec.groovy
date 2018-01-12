package restify

import geb.spock.GebSpec
import gorm.restapi.testing.RestApiTestTrait
import grails.testing.mixin.integration.Integration

@Integration
class OrgControllerSpec extends GebSpec implements RestApiTestTrait{

    void setupSpec(){
        100.times {
            new Org(name: "Org#$it",
                    num: "Org-num#$it",
                    revenue: 100 * it,
                    isActive: (it % 2 == 0),
                    credit: (it % 2 ? 5000 : null),
                    refId: it * 200 as Long,
                    testDate: (new Date() + it).clearTime(),
                    address: new Address(city: "City#$it", testId: it * 3).persist()).persist()
        }
    }

    String getResourcePath() {
        "${baseUrl}api/org"
    }



    def "Check list"() {
        when:
        def response = restBuilder.get(resourcePath)
        then:
        response.json.size() == 10
    }

    def "Filter by Name eq"() {
        when:
        List list = restBuilder.post(resourcePath+"/list") {
            json([criteria: [name: "Org#23"]])
        }.json
        then:
        list.size() == 1
        list[0].name == "Org#23"
    }

    def "Filter by id eq"() {
        given:
        Map data= [criteria: [id: 24]]

        when:
        List list = restBuilder.post(resourcePath+"/list") {
            json(data)
        }.json
        then:
        list.size() == 1
        list[0].name == "Org#23"
    }

    def "Filter by id inList"() {
        given:
        Map data= [criteria: [id: [24, 25]]]

        when:
        List list = restBuilder.post(resourcePath+"/list") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#23"
    }

    def "Filter by Name ilike"() {
        given:
        Map data= [criteria: [name: "Org#2%"]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 11
        list[0].name == "Org#2"
        list[1].name == "Org#20"
        list[10].name == "Org#29"
    }

    def "Filter by nested id"() {
        given:
        Map data= [criteria: [address: [id: 2]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#1"
        list[0].address.id == 2
    }

    def "Filter by nestedId"() {
        given:
        Map data= [criteria: ["address.id": 2]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#1"
        list[0].address.id == 2
    }

    def "Filter by nested id inList"() {
        given:
        Map data= [criteria: [address: [id: [24, 25, 26]]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 3
        list[0].name == "Org#23"
    }

    def "Filter by nested string"() {
        given:
        Map data= [criteria: [address: [city: "City#2"]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#2"
        list[0].address.id == 3
    }

    def "Filter by nested string ilike"() {
        given:
        Map data= [criteria: [address: [city: "City#2%"]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 11
        list[0].name == "Org#2"
        list[1].name == "Org#20"
        list[10].name == "Org#29"
        list[0].address.id == 3
        list[1].address.id == 21
        list[10].address.id == 30
    }

    def "Filter by boolean"() {
        given:
        Map data= [criteria: [isActive: true]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == Org.createCriteria().list() { eq "isActive", true }.size()
    }

    def "Filter by boolean in list"() {
        given:
        Map data= [criteria: [isActive: [false]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 50
        list[0].isActive == false
        list[1].isActive == false
    }

    def "Filter by BigDecimal"() {
        given:
        Map data= [criteria: [revenue: 200.0]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#2"
    }

    def "Filter by BigDecimal in list"() {
        given:
        Map data= [criteria: [revenue: [200.0, 500.0]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#2"
        list[1].name == "Org#5"
    }

    def "Filter by Date"() {
        given:
        Map data= [criteria: [testDate: (new Date() + 1).clearTime()]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#1"
    }

    def "Filter by Date le"() {
        given:
        Map data= [criteria: ['testDate.$lte': (new Date() + 1).clearTime()]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == Org.createCriteria().list() { le "testDate", (new Date() + 1).clearTime() }.size()
        list[0].name == Org.createCriteria().list() { le "testDate", (new Date() + 1).clearTime() }[0].name
    }

    def "Filter by xxxId 1"() {
        given:
        Map data= [criteria: [refId: 200]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#1"
    }

    def "Filter by xxxId 2"() {
        given:
        Map data= [criteria: ["address.testId": 9]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json
        then:
        list.size() == 1
        list[0].name == "Org#3"
    }


    def "Filter by xxxId 3"() {
        given:
        Map data= [criteria: [address: [testId: 3]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 1
        list[0].name == "Org#1"
    }

    def "Filter by xxxId 4"() {
        given:
        Map data= [criteria: ["address.testId": [9, 12]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#3"
    }


    def "Filter with `or` "() {
        given:
        Map data= [criteria: ['$or': ["name": "Org#1", "address.id": 4]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#1"
        list[1].name == "Org#3"
    }

    def "Filter with `or` on low level"() {
        given:
        Map data= [criteria: [address: ['$or': ["city": "City#1", "id": 4]]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#1"
        list[1].name == "Org#3"
    }

    def "Filter with `or` with like"() {
        given:
        Map data= [criteria: ["\$or": ["name": "Org#2%", "address.id": 4]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 12
        list[0].name == "Org#2"
        list[1].name == "Org#3"
        list[2].name == "Org#20"
    }

    def "Filter with `between()`"() {
        given:
        Map data= [criteria: [id: ["\$between": [2, 10]]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 9
        list[0].name == "Org#1"
        list[1].name == "Org#2"
        list[-1].name == "Org#9"
    }

    def "Filter with `in()`"() {
        given:
        Map data= [criteria: [id: ["\$in": [24, 25]]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#23"
    }

    def "Filter with `inList()`"() {
        given:
        Map data= [criteria: [id: ["\$inList": [24, 25]]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 2
        list[0].name == "Org#23"
    }



    def "Filter by Name ilike()"() {
        given:
        Map data= [criteria: [name: ["\$ilike": "Org#2%"]]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json

        then:
        list.size() == 11
        list[0].name == "Org#2"
        list[1].name == "Org#20"
        list[10].name == "Org#29"
    }

    def "test paging, defaults"() {
        when:
        List list = restBuilder.post(resourcePath+"/list").json

        then:
        list.size() == 10
    }

    def "test paging"() {
        when:
        List list = restBuilder.get(resourcePath+"/list?max=20").json
        then:
        list.size() == 20
        list[0].id == 1

        when:
        list = restBuilder.get(resourcePath+"/list?page=2").json
        then:
        list.size() == 10
        list[0].id == 11

    }

    def "test quick search"() {
        given:
        Map data= [criteria: ['$quickSearch': "Org#2%"]]

        when:
        List list = restBuilder.post(resourcePath+"/list?max=150") {
            json(data)
        }.json
        then:
        list.size() == 11

    }

}
