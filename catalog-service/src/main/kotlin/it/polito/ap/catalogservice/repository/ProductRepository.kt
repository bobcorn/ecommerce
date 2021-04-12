package it.polito.ap.catalogservice.repository

import it.polito.ap.catalogservice.model.Product
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : MongoRepository<Product, String> {
    fun findByName(productName: String): Product?
}