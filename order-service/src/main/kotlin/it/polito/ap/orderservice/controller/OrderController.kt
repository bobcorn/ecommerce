package it.polito.ap.orderservice.controller

import it.polito.ap.common.dto.OrderDTO
import it.polito.ap.common.dto.OrderPlacingDTO
import it.polito.ap.common.dto.UserDTO
import it.polito.ap.common.utils.StatusType
import it.polito.ap.orderservice.service.OrderService
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/orders")
class OrderController(
    val orderService: OrderService
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OrderController::class.java)
    }

    @PostMapping("")
    suspend fun placeOrder(@RequestBody orderPlacingDTO: OrderPlacingDTO): ResponseEntity<OrderDTO?> {
        LOGGER.info("Received a request to place an order for user ${orderPlacingDTO.user.userId}")
        val order = orderService.createNewOrder(orderPlacingDTO)
        order?.let {
            LOGGER.info("Order ${order.orderId} placed successfully!")
            return ResponseEntity.ok(order)
        } ?: kotlin.run {
            LOGGER.info("Cannot place order")
            return ResponseEntity.badRequest().body(null)
        }
    }

    @PostMapping("/{orderId}/status")
    fun orderStatus(@PathVariable orderId: ObjectId, @RequestBody user: UserDTO): ResponseEntity<String> {
        LOGGER.info("Received request for the status of $orderId by ${user.userId} with role: ${user.role}!")
        return when (val message = orderService.getOrderStatus(orderId, user)) {
            "Unauthorized user! Unable to get order status for $orderId" -> {
                LOGGER.info(message)
                ResponseEntity(message, HttpStatus.UNAUTHORIZED)
            }
            else -> {
                LOGGER.info(message)
                ResponseEntity(message, HttpStatus.OK)
            }
        }
    }

    @PutMapping("/{orderId}")
    suspend fun changeStatus(
        @PathVariable orderId: ObjectId,
        @RequestParam newStatus: StatusType,
        @RequestBody user: UserDTO
    ): ResponseEntity<String> {
        LOGGER.info("Received request to change the status of $orderId to $newStatus from ${user.userId} with role: ${user.role}!")
        when (orderService.modifyOrderStatus(orderId, newStatus, user)) {
            "Order modified by admin" -> {
                val statusString = "Order modified by admin"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.OK)
            }
            "Cannot change order status" -> {
                val statusString = "Cannot change order status"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
            "Order modified" -> {
                val statusString = "Order modified"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.OK)
            }
            "Unauthorized request to modify order status" -> {
                val statusString = "Unauthorized request to modify order status"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.UNAUTHORIZED)
            }
            else -> {
                val statusString = "Unexpected error"
                LOGGER.error(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
        }
    }
}
