package it.polito.ap.warehouseservice.repository

import it.polito.ap.warehouseservice.model.Warehouse
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface WarehouseRepository : MongoRepository<Warehouse, String> {

    fun getWarehouseByWarehouseId(warehouseId: String): Warehouse?

    @Query("{'transactionList.orderId': ?0}")
    fun getWarehouseByOrderId(orderId: String): List<Warehouse>

    @Aggregation(
        "{'\$unwind' : '\$inventory'}",
        "{'\$match' : { '\$and': [ {'inventory.productId': ?0}, {'inventory.quantity': {'\$gt': 0} } ] } }",
        "{'\$sort':{'inventory.quantity': -1}}",
        "{'\$limit': 1}",
        "{'\$project':{_id: 1}}"
    )
    fun getWarehouseByMaxProductQuantity(productId: String): List<String>

    @Aggregation("{'\$project':{_id: 1}}")
    fun getAllWarehouseIds(): List<String>
}