package restify

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic
import gorm.restapi.RestApi

//@EqualsAndHashCode(includes='fooName') //natural key
//@ToString(includes='fooName', includeNames=true, includePackage=false)
@GrailsCompileStatic
@RestApi(description = "The user for the restify application")
class AppUser {

    String userName
    String  magicCode
    String  email

    Date dateCreated
    Date lastUpdated

    static constraints = {
        userName  description: 'The login name',
                  example:"billy1",
                  nullable: false, maxSize:50

        magicCode description: 'The keymaster code. Some call this a password',
                  example:"b4d_p455w0rd",
                  nullable: false

        email     description: "Email will be used for evil.",
                  example:"billy@gmail.com",
                  email:true, maxSize:50, nullable: true
    }
    //static selectFields = ['id, name']

}
