package it.polito.ap.orderservice.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.polito.ap.common.dto.*
import it.polito.ap.common.utils.RoleType
import it.polito.ap.common.utils.StatusType
import it.polito.ap.common.utils.TransactionMotivation
import it.polito.ap.orderservice.model.Delivery
import it.polito.ap.orderservice.model.Order
import it.polito.ap.orderservice.model.utils.CartElement
import it.polito.ap.orderservice.repository.OrderRepository
import it.polito.ap.orderservice.service.mapper.CartProductMapper
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.util.*
import kotlin.collections.ArrayList

// useful to manage return type such as List in exchange function
inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}

@Service
class OrderService(
    val orderRepository: OrderRepository,
    val orderMapper: CartProductMapper,
    val mongoTemplate: MongoTemplate,
    val kafkaTemplate: KafkaTemplate<String, String>,
    val emailSender: JavaMailSender
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OrderService::class.java)
    }

    private val restTemplate = RestTemplate()

    @Value("\${application.wallet-address}")
    private lateinit var walletServiceAddress: String

    @Value("\${application.warehouse-address}")
    private lateinit var warehouseServiceAddress: String

    @Value("\${application.catalog-address}")
    private lateinit var catalogServiceAddress: String

    @Value("\${application.consistency-check-timeout-ms}")
    private lateinit var consistencyCheckTimeoutMillis: Number

    private val jacksonObjectMapper = jacksonObjectMapper()

    @KafkaListener(groupId = "order_service", topics = ["place_order"])
    fun ensureOrderConsistency(message: String) {
        val orderId = jacksonObjectMapper.readValue<String>(message)
        LOGGER.debug("Ensuring consistency of order $orderId")
        Thread.sleep(consistencyCheckTimeoutMillis.toLong())
        val order = getOrderByOrderId(orderId)
        if (order == null) {
            LOGGER.debug("Order $orderId missing from the database, rolling back")
            orderRollback(orderId)
        } else {
            LOGGER.debug("Found order $orderId in the database, order successful")
            val user = order.buyer?.let { createUserDTO(it) }
            sendEmail(order, user!!)
        }
    }

    private fun createUserDTO(userId: String): UserDTO {
        return UserDTO(userId, RoleType.ROLE_CUSTOMER)
    }


    fun orderRollback(orderId: String) {
        kafkaTemplate.send("rollback", jacksonObjectMapper.writeValueAsString(orderId))
    }

    fun getOrderByOrderId(orderId: String): Order? {
        LOGGER.debug("Attempting to retrieve order $orderId from the database")
        val order = orderRepository.getOrderByOrderId(orderId)
        order?.let {
            LOGGER.debug("Found order $orderId in the database")
        } ?: kotlin.run {
            LOGGER.debug("Could not find order $orderId in the database")
        }
        return order
    }

    suspend fun createNewOrder(orderPlacingDTO: OrderPlacingDTO): OrderDTO? {
        LOGGER.debug("Received a request to place an order for user ${orderPlacingDTO.user.userId}")

        val cart = orderPlacingDTO.cart.map { orderMapper.toModel(it) }
        val cartPrice = cart.sumByDouble { it.price * it.quantity }
        val order = Order()

        kafkaTemplate.send("place_order", jacksonObjectMapper.writeValueAsString(order.orderId.toString()))

        val user = orderPlacingDTO.user

        // send info to wallet-service
        val transactionDTO = TransactionDTO(order.orderId.toString(), -cartPrice, TransactionMotivation.ORDER_PAYMENT)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val requestEntityWallet = HttpEntity<TransactionDTO>(transactionDTO, headers)

        try {
            val responseEntityWallet: HttpEntity<String> = restTemplate.exchange(
                "$walletServiceAddress/${user.userId}/transactions",
                HttpMethod.PUT,
                requestEntityWallet,
                String::class.java
            )
            LOGGER.debug("Communication with wallet service successful: ${responseEntityWallet.body}")
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot modify wallet: ${e.message}")
            return null
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot modify wallet: ${e.message}")
            return null
        }

        // send info to warehouse-service
        val cartProductDTOList = ArrayList<CartProductDTO>()
        cart.forEach {
            cartProductDTOList.add(
                CartProductDTO(
                    ProductDTO(it.productId, it.price),
                    it.quantity
                )
            )
        }

        val deliveryList: ArrayList<Delivery> = ArrayList()
        val delivery = Delivery()

        val requestEntityWarehouse = HttpEntity<List<CartProductDTO>>(cartProductDTOList, headers)
        try {
            val responseEntityWarehouse: ResponseEntity<List<DeliveryDTO>> = restTemplate.exchange(
                "$warehouseServiceAddress/${order.orderId}/deliveries",
                HttpMethod.POST,
                requestEntityWarehouse,
                typeRef<List<DeliveryDTO>>() // we need it to return a List
            )
            LOGGER.debug("Communication with warehouse service successful: ${responseEntityWarehouse.body}")

            responseEntityWarehouse.body?.forEach { deliveryDTO ->
                deliveryDTO.deliveryProducts.forEach {
                    delivery.deliveryProducts[it.productDTO.productId] = it.quantity
                }
                deliveryList.add(delivery)
                // fill delivery information
                delivery.shippingAddress = orderPlacingDTO.shippingAddress
                delivery.warehouseId = deliveryDTO.warehouseId
            }
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot create Delivery List: ${e.message}")
            return null
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot create Delivery List: ${e.message}")
            return null
        }

        // here all checks are successful
        // fill order information
        order.cart = cart as MutableList<CartElement>
        order.buyer = user.userId
        order.deliveryList = deliveryList

        saveOrder(order)
        LOGGER.debug("Placed new order ${order.orderId}")
        return OrderDTO(order.orderId.toString(), order.status)
    }

    private fun saveOrder(order: Order) {
        LOGGER.debug("Request to save order ${order.orderId}")
        orderRepository.save(order)
        LOGGER.debug("Order ${order.orderId} saved successfully")
    }

    fun getOrderStatus(orderId: ObjectId, user: UserDTO): String {
        LOGGER.debug("Request to get order status for order $orderId")
        val order = orderRepository.findById(orderId.toString())
        if (order.get().buyer == user.userId || user.role == RoleType.ROLE_ADMIN) {
            LOGGER.debug("Getting order status for order $orderId")
            return orderRepository.findById(orderId.toString()).get().status.toString()
        }
        val statusString = "Unauthorized user! Unable to get order status for $orderId"
        LOGGER.debug(statusString)
        return statusString
    }

    suspend fun modifyOrderStatus(orderId: ObjectId, newStatus: StatusType, user: UserDTO): String {
        LOGGER.debug("Receiving request to modify status for order $orderId")
        // admin logic
        if (RoleType.ROLE_ADMIN == user.role) {
            val query = Query().addCriteria(
                Criteria.where("orderId").`is`(orderId)
            )
            val update = Update().set("status", newStatus)
            val updateStatus = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions().returnNew(true), Order::class.java
            )

            updateStatus?.let {
                if (updateStatus.status == StatusType.CANCELLED || updateStatus.status == StatusType.FAILED) // rollback is needed just in these two cases
                    orderRollback(updateStatus.orderId.toString())
                LOGGER.debug("Order modified by admin! Order status: ${updateStatus.status}")
                sendEmail(updateStatus, user)
                return "Order modified by admin"
            } ?: kotlin.run {
                val statusString = "Cannot change order status"
                LOGGER.debug(statusString)
                return statusString
            }
        }
        // customer logic
        val query = Query().addCriteria(
            Criteria.where("orderId").`is`(orderId)
                .and("buyer").`is`(user.userId)
                .and("status").`is`(StatusType.ISSUED)
        )
        val update = Update().set("status", StatusType.CANCELLED)
        val updateStatus = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Order::class.java
        )

        updateStatus?.let {
            orderRollback(updateStatus.orderId.toString()) // here we know order is CANCELLED
            sendEmail(updateStatus, user)
            LOGGER.debug("Order modified! Order status: ${updateStatus.status}")
            return "Order modified"
        } ?: kotlin.run {
            val statusString = "Unauthorized request to modify order status"
            LOGGER.debug(statusString)
            return statusString
        }
    }

    private fun sendEmail(order: Order, user: UserDTO) {
        val message = SimpleMailMessage()
        message.setSubject("Change status for order ${order.orderId}")
        message.setText(
            """
            Change status for order ${order.orderId}: ${order.status}
            """.trimIndent()
        )

        val emails = getAdminsEmail()
        if (emails == null) {
            LOGGER.debug("Cannot retrieve admins email")
            return
        }

        val customerEmail = order.buyer?.let { getEmailById(it) }
        if (customerEmail == null) {
            LOGGER.debug("Cannot retrieve customer email")
            return
        }
        if (!emails.contains(customerEmail))
            emails.add(customerEmail)

        if (user.role == RoleType.ROLE_ADMIN) {
            val adminEmail = getEmailById(user.userId)
            if (adminEmail == null) {
                LOGGER.debug("Cannot retrieve admin email")
                return
            }
            if (!emails.contains(adminEmail))
                emails.add(adminEmail)
        }

        message.setTo(*emails.toTypedArray())
        emailSender.send(message)
        LOGGER.debug("Emails sent")
    }

    private fun getAdminsEmail(): ArrayList<String>? {
        LOGGER.debug("Request to retrieve admins email")
        return try {
            restTemplate.exchange(
                "$catalogServiceAddress/getAdminsEmail",
                HttpMethod.GET,
                null,
                typeRef<ArrayList<String>>() // we need it to return a List
            ).body
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot retrieve admins email: ${e.message}")
            return null
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot retrieve admins email: ${e.message}")
            return null
        }
    }

    private fun getEmailById(userId: String): String? {
        LOGGER.debug("Request to retrieve email for $userId")
        return try {
            restTemplate.exchange(
                "$catalogServiceAddress/getEmail/$userId",
                HttpMethod.GET,
                null,
                String::class.java
            ).body
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot retrieve email for $userId: ${e.message}")
            return null
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot retrieve email for $userId: ${e.message}")
            return null
        }
    }

}