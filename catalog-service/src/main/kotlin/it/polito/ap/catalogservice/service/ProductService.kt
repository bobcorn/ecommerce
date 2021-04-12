package it.polito.ap.catalogservice.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.polito.ap.catalogservice.model.Product
import it.polito.ap.catalogservice.model.User
import it.polito.ap.catalogservice.repository.ProductRepository
import it.polito.ap.catalogservice.service.mapper.CustomerProductDTOMapper
import it.polito.ap.common.dto.*
import it.polito.ap.common.utils.RoleType
import it.polito.ap.common.utils.StatusType
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.util.*

// useful to manage return type such as List in exchange function
inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}

@Service
class ProductService(
    val productRepository: ProductRepository,
    val userService: UserService,
    val mongoTemplate: MongoTemplate,
    val customerProductDTOMapper: CustomerProductDTOMapper
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProductService::class.java)
    }

    private val jacksonObjectMapper = jacksonObjectMapper()

    private val restTemplate = RestTemplate()
    private val headers = HttpHeaders()

    @Value("\${application.order-address}")
    private lateinit var orderServiceAddress: String

    @Value("\${application.wallet-address}")
    private lateinit var walletServiceAddress: String

    // Receive product quantities from the WarehouseService via Kafka messages
    @KafkaListener(groupId = "product_service", topics = ["product_quantities"])
    @CacheEvict(value = ["product"])
    fun updateProductQuantities(message: String) {
        LOGGER.debug("Received updated quantities of stored products")
        val productQuantities = jacksonObjectMapper.readValue<Map<String, Int>>(message)
        productQuantities.forEach { (name, quantity) -> updateProductQuantity(name, quantity) }
        LOGGER.debug("Updated product quantities")
    }

    fun updateProductQuantity(productName: String, productQuantity: Int) {
        LOGGER.debug("Updating quantity of product $productName")
        val query = Query().addCriteria(Criteria.where("name").`is`(productName))
        val update = Update().set("quantity", productQuantity)
        val updatedWarehouse = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Product::class.java
        )
        updatedWarehouse?.let {
            LOGGER.debug("Quantity of product $productName updated successfully - new quantity: $productQuantity")
        } ?: kotlin.run {
            LOGGER.debug("Could not find product in the database")
        }
    }

    fun saveNewProduct(customerProductDTO: CustomerProductDTO): Product? {
        // Wrapped function for caching purposes
        LOGGER.debug("Saving product ${customerProductDTO.name} in the database")
        val savedProduct = productRepository.save(customerProductDTOMapper.toModel(customerProductDTO))
        LOGGER.debug("Saved product ${customerProductDTO.name} in the database")
        return savedProduct
    }

    // check if product already exists, if not product is added
    fun addProduct(customerProductDTO: CustomerProductDTO): String {
        LOGGER.debug("Received request to add product ${customerProductDTO.name}")
        if (!checkProductQuantity(customerProductDTO.quantity))
            return "Provide a valid quantity: >= 0"
        if (customerProductDTO.name.isBlank()) {
            LOGGER.debug("Product name must be well formed (not empty or blank)")
            return "Product name must be well formed (not empty or blank)"
        }
        val productCheck: Product? = getProductByName(customerProductDTO.name)
        productCheck?.let {
            LOGGER.debug("Product ${customerProductDTO.name} already present into the DB")
            return "Product ${customerProductDTO.name} already present into the DB"
        } ?: kotlin.run {
            saveNewProduct(customerProductDTO)
            LOGGER.debug("Product ${customerProductDTO.name} added successfully")
            return "Product ${customerProductDTO.name} added successfully"
        }
    }

    // return true if is valid
    fun checkProductQuantity(productQuantity: Int): Boolean {
        return productQuantity >= 0
    }

    @Cacheable(value = ["product"])
    fun getProductByName(productName: String): Product? {
        LOGGER.debug("Retrieving product $productName by name")
        val product = productRepository.findByName(productName)
        product?.let {
            LOGGER.debug("Product $productName successfully retrieved")
        } ?: kotlin.run {
            LOGGER.debug("Could not find product $productName in the database")
        }
        return product
    }

    fun getProductById(productId: String): Optional<Product> {
        LOGGER.debug("Received request to retrieve product with id $productId")
        return productRepository.findById(productId)
    }

    fun getAll(): List<Product> {
        LOGGER.debug("Received request to retrieve all the product")
        return productRepository.findAll()
    }

    @CacheEvict(value = ["product"], key = "#productId")
    fun deleteProductById(productId: String) {
        LOGGER.debug("Received request to delete product with id $productId")
        productRepository.deleteById(productId)
    }

    fun editProduct(productName: String, newProduct: CustomerProductDTO): Product? {
        if (!checkProductQuantity(newProduct.quantity)) // if new quantity is invalid -> return null
            return null
        val product = getProductByName(productName)
        if (product == null) {  // if not present it doesn't create a new product. For this use addProduct
            LOGGER.debug("Received request to edit a product that in not present in the DB: $productName")
            return null
        }

        if ("" != newProduct.name)
            product.name = newProduct.name
        if (null != newProduct.category)
            product.category = newProduct.category
        if ("" != newProduct.description)
            product.description = newProduct.description
        if ("" != newProduct.picture)
            product.picture = newProduct.picture
        if (!0.0.equals(newProduct.price))
            product.price = newProduct.price
        if (0 != newProduct.quantity)
            product.quantity = newProduct.quantity

        val query = Query().addCriteria(Criteria.where("name").`is`(productName))
        val update = Update()
            .set("name", product.name)
            .set("category", product.category)
            .set("description", product.description)
            .set("picture", product.picture)
            .set("price", product.price)
            .set("quantity", product.quantity)
        val updatedProduct = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Product::class.java
        )
        updatedProduct?.let {
            LOGGER.debug("Product $productName updated successfully")
        } ?: kotlin.run {
            LOGGER.debug("Could not update product $productName")
        }
        return updatedProduct
    }

    suspend fun placeOrder(cart: List<CartProductDTO>, shippingAddress: String, authentication: Authentication): OrderDTO? {
        if (cart.isEmpty())
            return null
        val userDTO = createUserDTOFromLoggedUser(authentication) ?: return null
        LOGGER.debug("Received request to place an order for user ${userDTO.userId}")

        // check if product is in catalog, if yes: update product price in cart
        cart.forEach {
            if (it.quantity < 1)
                return null
            val product = getProductById(it.productDTO.productId)
            if (product.isEmpty) {
                LOGGER.debug("Received request to place a order but a product is not present in the catalog: ${it.productDTO.productId}")
                return null
            }
            it.productDTO.price = product.get().price
        }

        // send info to order-service
        headers.contentType = MediaType.APPLICATION_JSON
        val orderPlacingDTO = OrderPlacingDTO(cart, userDTO, shippingAddress)
        val requestEntity = HttpEntity<OrderPlacingDTO>(orderPlacingDTO, headers)
        try {
            val responseEntity: ResponseEntity<OrderDTO> = restTemplate.exchange(
                orderServiceAddress,
                HttpMethod.POST,
                requestEntity,
                OrderDTO::class.java
            )
            val statusCode = responseEntity.statusCode
            if (statusCode == HttpStatus.OK)
                return responseEntity.body
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot place order: ${e.message}")
            return null
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot place order: ${e.message}")
            return null
        }
        return null
    }

    suspend fun getOrderStatus(orderId: ObjectId, authentication: Authentication): String {
        val userDTO = createUserDTOFromLoggedUser(authentication)
        if (userDTO == null) {
            val statusString = "Cannot find user"
            LOGGER.debug(statusString)
            return statusString
        }

        headers.contentType = MediaType.APPLICATION_JSON
        val requestEntity = HttpEntity<UserDTO>(userDTO, headers)
        return try {
            restTemplate.exchange(
                "$orderServiceAddress/$orderId/status",
                HttpMethod.POST,
                requestEntity,
                String::class.java
            ).body!!
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot get order status: ${e.message}")
            "Cannot get order status: HttpServerErrorException"
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot get order status: ${e.message}")
            "Cannot get order status: HttpClientErrorException"
        }
    }

    suspend fun modifyOrderStatus(orderId: ObjectId, newStatus: StatusType, authentication: Authentication): String {
        val userDTO = createUserDTOFromLoggedUser(authentication)
        if (userDTO == null) {
            val statusString = "Cannot find user"
            LOGGER.debug(statusString)
            return statusString
        }

        headers.contentType = MediaType.APPLICATION_JSON
        val requestEntity = HttpEntity<UserDTO>(userDTO, headers)
        return try {
            restTemplate.exchange(
                "$orderServiceAddress/$orderId?newStatus=$newStatus",
                HttpMethod.PUT,
                requestEntity,
                String::class.java
            ).body!!
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot change order status: ${e.message}")
            "Cannot change order status: HttpServerErrorException"
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot change order status: ${e.message}")
            "Cannot change order status: HttpClientErrorException"
        }
    }

    private fun createUserDTOFromLoggedUser(authentication: Authentication): UserDTO? {
        val user = retrieveLoggedUser(authentication)
        if (user == null) {
            LOGGER.debug("User not found")
            return null
        }
        return UserDTO(user.userId.toString(), user.role)
    }

    private fun retrieveLoggedUser(authentication: Authentication): User? {
        LOGGER.debug("Request ot retrieve authenticated user")
        val authenticationUser = authentication.principal as User
        return userService.getUserByEmail(authenticationUser.email)
    }

    // if no param the request is performed for the logger used, instead is performed just by admins
    suspend fun getWalletFunds(userId: String?, authentication: Authentication): String {
        LOGGER.debug("Request to retrieve wallet funds")
        val user = retrieveLoggedUser(authentication)
        if (user == null) { // if user logged is not present in the db -> return
            LOGGER.debug("Cannot find user $userId in the database")
            return "Cannot find user in the database"
        }

        var requestAddress = ""
        if (userId == null) {
            LOGGER.debug("Request to retrieve wallet funds for logged user ${user.userId}")
            requestAddress = "$walletServiceAddress/${user.userId}/funds"
        } else if (user.role == RoleType.ROLE_ADMIN) {
            LOGGER.debug("Request to retrieve wallet funds by admin for user $userId")
            requestAddress = "$walletServiceAddress/$userId/funds"
        } else {
            LOGGER.debug("Unauthorized request to retrieve wallet funds")
            return "Unauthorized request to retrieve wallet funds"
        }

        return try {
            restTemplate.exchange(
                requestAddress,
                HttpMethod.GET,
                null,
                Double::class.java
            ).body!!.toString()
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot retrieve user wallet funds: ${e.message}")
            "Cannot retrieve user wallet funds: HttpServerErrorException"
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot retrieve user wallet funds ${e.message}")
            "Cannot retrieve user wallet funds: HttpClientErrorException"
        }
    }

    // if no param the request is performed for the logged user, instead is performed just by admins
    suspend fun getWalletTransactions(userId: String?, authentication: Authentication): Pair<List<TransactionDTO>?, String> {
        LOGGER.debug("Request to retrieve wallet transactions")
        val user = retrieveLoggedUser(authentication)
        if (user == null) {
            LOGGER.debug("Cannot find user in the database: $userId")
            return Pair(null, "Cannot find user in the database")
        }

        var requestAddress = ""
        if (userId == null) {
            LOGGER.debug("Request to retrieve wallet transactions for logged user ${user.userId}")
            requestAddress = "$walletServiceAddress/${user.userId}/transactions"
        } else if (user.role == RoleType.ROLE_ADMIN) {
            LOGGER.debug("Request to retrieve wallet transactions by admin for user $userId")
            requestAddress = "$walletServiceAddress/$userId/transactions"
        } else {
            LOGGER.debug("Unauthorized request to retrieve wallet transactions")
            return Pair(null, "Unauthorized request to retrieve wallet transactions")
        }

        return try {
            val responseEntity = restTemplate.exchange(
                requestAddress,
                HttpMethod.GET,
                null,
                typeRef<List<TransactionDTO>>() // we need it to return a List
            )
            Pair(responseEntity.body, "OK")
        } catch (e: HttpServerErrorException) {
            LOGGER.debug("Cannot retrieve user wallet transactions: ${e.message}")
            Pair(null, "Cannot retrieve user wallet transactions: HttpServerErrorException")
        } catch (e: HttpClientErrorException) {
            LOGGER.debug("Cannot retrieve user wallet transactions ${e.message}")
            Pair(null, "Cannot retrieve user wallet transactions: HttpClientErrorException")
        }
    }

    fun getAdminsEmail(): ArrayList<String>? {
        LOGGER.debug("Request to retrieve admins email")
        val admins = userService.getUsersByRole(RoleType.ROLE_ADMIN)
        admins?.let {
            // in our random logic, we take first 3 admins
            val adminsToTake = 3
            val emails = ArrayList<String>()
            admins.take(adminsToTake).forEach { emails.add(it.email) }
            return emails
        } ?: kotlin.run {
            LOGGER.debug("No admin found")
            return null
        }
    }

    fun getEmailById(userId: ObjectId): String? {
        LOGGER.debug("Request to retrieve user $userId email by id")
        return userService.getUserById(userId)?.email
    }
}
