package it.polito.ap.catalogservice.controller

import it.polito.ap.catalogservice.model.Product
import it.polito.ap.catalogservice.service.ProductService
import it.polito.ap.common.dto.CartProductDTO
import it.polito.ap.common.dto.CustomerProductDTO
import it.polito.ap.common.dto.OrderDTO
import it.polito.ap.common.dto.TransactionDTO
import it.polito.ap.common.utils.StatusType
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/products")
class ProductController(val productService: ProductService) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProductController::class.java)
    }

    @GetMapping("")
    fun getAll(): ResponseEntity<List<Product>> {
        LOGGER.info("Received request to retrieve all the product")
        val products = productService.getAll()
        if (products.isEmpty()) {
            LOGGER.info("Found no product")
            return ResponseEntity(null, HttpStatus.NOT_FOUND)
        }
        LOGGER.info("Found ${products.size} products")
        return ResponseEntity.ok(products)
    }

    @GetMapping("/{productName}")
    fun getProductByName(@PathVariable productName: String): ResponseEntity<Product> {
        LOGGER.info("Received request for $productName")
        val product = productService.getProductByName(productName)
        product?.let {
            LOGGER.info("Found product $productName")
            return ResponseEntity.ok(product)
        }
        return ResponseEntity.badRequest().body(null)
    }

    @PostMapping("")
    fun addProduct(@RequestBody product: CustomerProductDTO): ResponseEntity<String> {
        LOGGER.info("Received request to add product ${product.name}")
        return ResponseEntity.ok(productService.addProduct(product))
    }

    @PutMapping("/{productId}")
    fun editProduct(
        @PathVariable productId: String,
        @RequestBody newProduct: CustomerProductDTO
    ): ResponseEntity<Product> {
        LOGGER.info("Received request to modify product with id $productId")
        val product = productService.editProduct(productId, newProduct)
        product?.let {
            LOGGER.info("Modification on $productId worked")
            return ResponseEntity.ok(product)
        } ?: kotlin.run {
            LOGGER.info("Modification on $productId failed")
            return ResponseEntity.badRequest().body(null)
        }
    }

    @DeleteMapping("/{productId}")
    fun deleteProductById(@PathVariable productId: String): ResponseEntity<String> {
        LOGGER.info("Received request to delete the product with id $productId")
        productService.deleteProductById(productId)
        return ResponseEntity.ok("Deletion of product with id $productId completed")
    }

    @PostMapping("/placeOrder")
    suspend fun placeOrder(
        @RequestBody cart: List<CartProductDTO>,
        authentication: Authentication,
        @RequestParam shippingAddress: String
    ): ResponseEntity<OrderDTO> {
        LOGGER.info("Received request to place a order")
        val order = productService.placeOrder(cart, shippingAddress, authentication)
        order?.let {
            LOGGER.info("Order placed with ID: ${order.orderId}")
            return ResponseEntity.ok(order)
        } ?: kotlin.run {
            LOGGER.info("Cannot place order")
            return ResponseEntity.badRequest().body(null)
        }
    }

    @GetMapping("/orderStatus/{orderId}")
    suspend fun getOrderStatus(
        @PathVariable orderId: ObjectId,
        authentication: Authentication
    ): ResponseEntity<String> {
        LOGGER.info("Received request to get order status for order $orderId")
        when (val message = productService.getOrderStatus(orderId, authentication)) {
            "Cannot find user" -> {
                val statusString = "Cannot find user ${authentication.principal}"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
            "Cannot get order status: HttpServerErrorException" -> {
                val statusString = "Cannot get order status: HttpServerErrorException"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.INTERNAL_SERVER_ERROR)
            }
            "Cannot get order status: HttpClientErrorException" -> {
                val statusString = "Cannot get order status: HttpClientErrorException"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
            else -> {
                LOGGER.info(message)
                return ResponseEntity(message, HttpStatus.OK)
            }
        }
    }

    @PutMapping("/order/{orderId}")
    suspend fun changeStatus(
        @PathVariable orderId: ObjectId,
        @RequestParam newStatus: StatusType,
        authentication: Authentication
    ): ResponseEntity<String> {
        LOGGER.info("Received request to modify order status")
        when (val message = productService.modifyOrderStatus(orderId, newStatus, authentication)) {
            "Cannot find user" -> {
                val statusString = "Cannot find user ${authentication.principal}"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
            "Cannot change order status: HttpServerErrorException" -> {
                val statusString = "Cannot change order status: HttpServerErrorException"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.INTERNAL_SERVER_ERROR)
            }
            "Cannot change order status: HttpClientErrorException" -> {
                val statusString = "Cannot change order status: HttpClientErrorException"
                LOGGER.info(statusString)
                return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
            }
            else -> {
                LOGGER.info(message)
                return ResponseEntity(message, HttpStatus.OK)
            }
        }
    }

    // if no param the request is performed for the logger used, instead is performed just by admins
    @GetMapping("/walletFunds")
    suspend fun getWalletFunds(
        @RequestParam(required = false) userId: String?,
        authentication: Authentication
    ): ResponseEntity<Double> {
        LOGGER.info("Received request to retrieve wallet funds")
        when (val returnValue = productService.getWalletFunds(userId, authentication)) {
            "Cannot find user in the database" -> {
                LOGGER.info(returnValue)
                return ResponseEntity(null, HttpStatus.BAD_REQUEST)
            }
            "Cannot retrieve user wallet funds: HttpClientErrorException" -> {
                LOGGER.info(returnValue)
                return ResponseEntity(null, HttpStatus.BAD_REQUEST)
            }
            "Cannot retrieve user wallet funds: HttpServerErrorException" -> {
                LOGGER.info(returnValue)
                return ResponseEntity(null, HttpStatus.INTERNAL_SERVER_ERROR)
            }
            "Unauthorized request to retrieve wallet funds" -> {
                LOGGER.info(returnValue)
                return ResponseEntity(null, HttpStatus.UNAUTHORIZED)
            }
            else -> {
                return ResponseEntity(returnValue.toDouble(), HttpStatus.OK)
            }
        }
    }

    // if no param the request is performed for the logger used, instead is performed just by admins
    @GetMapping("/walletTransactions")
    suspend fun getWalletTransactions(
        @RequestParam(required = false) userId: String?,
        authentication: Authentication
    ): ResponseEntity<List<TransactionDTO>> {
        LOGGER.info("Received request to retrieve wallet funds")
        val returnValue = productService.getWalletTransactions(userId, authentication)
        when (returnValue.second) {
            "Cannot find user in the database" -> {
                LOGGER.info(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.BAD_REQUEST)
            }
            "Unauthorized request to retrieve wallet transactions" -> {
                LOGGER.info(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.UNAUTHORIZED)
            }
            "Cannot retrieve user wallet transactions: HttpServerErrorException" -> {
                LOGGER.info(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.INTERNAL_SERVER_ERROR)
            }
            "Cannot retrieve user wallet transactions: HttpClientErrorException" -> {
                LOGGER.info(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.BAD_REQUEST)
            }
            "OK" -> {
                LOGGER.info(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.OK)
            }
            else -> {
                LOGGER.error(returnValue.second)
                return ResponseEntity(returnValue.first, HttpStatus.BAD_REQUEST)
            }
        }
    }

    @GetMapping("/getAdminsEmail")
    fun getAdminsEmail(): ArrayList<String>? {
        LOGGER.info("Received request to retrieve admins email")
        return productService.getAdminsEmail()
    }

    @GetMapping("/getEmail/{userId}")
    fun getEmailById(@PathVariable userId: ObjectId): String? {
        LOGGER.info("Received request to retrieve email for user $userId")
        return productService.getEmailById(userId)
    }
}