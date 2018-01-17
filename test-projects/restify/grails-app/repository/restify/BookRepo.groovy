package restify

import gorm.tools.repository.DefaultGormRepo
import grails.gorm.transactions.Transactional

@Transactional
class BookRepo extends DefaultGormRepo<Book> {

    @Override
    Book create(Map params){
        params.title += 1
        super.create(params)
    }

}

