package it.polito.ap.orderservice.repository

import it.polito.ap.orderservice.model.Order
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : MongoRepository<Order, String> {
    fun getOrderByOrderId(orderId: String): Order?
}