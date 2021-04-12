package it.polito.ap.warehouseservice.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.polito.ap.common.dto.CartProductDTO
import it.polito.ap.common.dto.DeliveryDTO
import it.polito.ap.common.dto.ProductDTO
import it.polito.ap.common.dto.WarehouseProductDTO
import it.polito.ap.warehouseservice.model.Warehouse
import it.polito.ap.warehouseservice.model.WarehouseProduct
import it.polito.ap.warehouseservice.model.WarehouseTransaction
import it.polito.ap.warehouseservice.model.utils.WarehouseTransactionStatus
import it.polito.ap.warehouseservice.repository.WarehouseRepository
import it.polito.ap.warehouseservice.service.mapper.WarehouseMapper
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service


@Service
class WarehouseService(
    val warehouseRepository: WarehouseRepository,
    val mapper: WarehouseMapper,
    val mongoTemplate: MongoTemplate,
    val mailSender: JavaMailSender,
    val kafkaTemplate: KafkaTemplate<String, String>
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WarehouseService::class.java)
    }

    private val jacksonObjectMapper = jacksonObjectMapper()

    @KafkaListener(groupId = "warehouse_service", topics = ["rollback"])
    fun rollbackListener(message: String) {
        val orderId = jacksonObjectMapper.readValue<String>(message)
        LOGGER.debug("Ensuring consistency of order $orderId")
        rollbackOrder(orderId)
    }

    fun rollbackOrder(orderId: String) {
        val warehouses = warehouseRepository.getWarehouseByOrderId(orderId)
        warehouses.forEach { rollbackWarehouse(it, orderId) }
    }

    fun getAllWarehouseIds(): List<String> {
        LOGGER.debug("Listing the Id of all warehouses")
        return warehouseRepository.getAllWarehouseIds()
    }

    // Note, in practice, this function could be run late at night, when load on the service is low
//    @Scheduled(cron = "0 1/1 * * * ?")
    @Scheduled(cron = "0 30 4 1/1 * ?")
    fun gatherProductQuantities(): Map<String, Int> {
        // Avoid loading all warehouses simultaneously in memory
        val warehouseIds = getAllWarehouseIds()

        LOGGER.debug("Gathering product quantities across all warehouses")
        val productQuantities = mutableMapOf<String, Int>()
        warehouseIds.forEach { warehouseId ->
            val warehouse = getWarehouseByWarehouseId(warehouseId)
            warehouse!!.inventory.forEach { product ->
                productQuantities[product.productId] = (productQuantities[product.productId] ?: 0) + product.quantity
            }
        }

        LOGGER.debug("Sending product quantity information")
        kafkaTemplate.send("product_quantities", jacksonObjectMapper.writeValueAsString(productQuantities))
        return productQuantities
    }

    fun sendAlarmEmail(admins: List<String>, product: WarehouseProduct) {
        val message = SimpleMailMessage()
        message.setSubject("${product.productId} - warehouse alarm")
        message.setText(
            """
            Quantity of ${product.productId} is below the alarm threshold of ${product.alarmThreshold}.
            Remaining quantity: ${product.quantity}.
            """.trimIndent()
        )
        message.setTo(*admins.toTypedArray())
        mailSender.send(message)
    }

    fun checkAlarmThreshold(warehouse: Warehouse, productId: String) {
        // If this function is reached, warehouse and the product within it are assured to exist
        val product = warehouse.inventory.firstOrNull { it.productId == productId }!!
        if (product.quantity < product.alarmThreshold) {
            LOGGER.debug("Quantity of product $productId below the alarm threshold - Notifying admins")
            sendAlarmEmail(warehouse.adminEmails, product)
        }
    }

    fun rollbackWarehouse(warehouse: Warehouse, orderId: String) {
        LOGGER.debug("Rolling back transactions for order $orderId in warehouse ${warehouse.warehouseId}")
        val relevantTransactions = warehouse.transactionList.filter { it.orderId == orderId }
        relevantTransactions.forEach { transaction ->
            LOGGER.debug("Rolling back transaction for product ${transaction.productId} in order $orderId in warehouse ${warehouse.warehouseId}")
            val query = Query().addCriteria(
                Criteria.where("warehouseId").`is`(warehouse.warehouseId)
                    .and("inventory").elemMatch(
                        Criteria.where("productId").`is`(transaction.productId)
                    )
            )
            val rollbackTransaction = WarehouseTransaction(
                transaction.orderId,
                transaction.productId,
                -transaction.quantity,
                WarehouseTransactionStatus.ROLLBACK
            )
            val update = Update()
                .inc("inventory.$.quantity", -transaction.quantity)
                .push("transactionList", rollbackTransaction)
            val updatedWarehouse = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions().returnNew(true), Warehouse::class.java
            )
            updatedWarehouse?.let {
                LOGGER.debug("Rollback successful - product ${transaction.productId}, order $orderId")
            } ?: kotlin.run {
                LOGGER.error("Rollback failed - product ${transaction.productId}, order $orderId")
            }
        }
    }

    @Cacheable(value = ["warehouse"])
    fun getWarehouseByWarehouseId(warehouseId: String): Warehouse? {
        LOGGER.debug("Attempting to retrieve warehouse $warehouseId from the database")
        val warehouse = warehouseRepository.getWarehouseByWarehouseId(warehouseId)
        warehouse?.let {
            LOGGER.debug("Found warehouse $warehouseId in the DB")
        } ?: kotlin.run {
            LOGGER.debug("Could not find warehouse $warehouseId in the DB")
        }
        return warehouse
    }

    @Cacheable(value = ["warehouse"])
    fun selectWarehouse(productId: String): String? {
        val warehouseIds = warehouseRepository.getWarehouseByMaxProductQuantity(productId)
        return if (warehouseIds.isEmpty()) {
            LOGGER.debug("No warehouse containing product $productId found")
            null
        } else {
            LOGGER.debug("Warehouse ${warehouseIds[0]} was selected")
            warehouseIds[0]
        }
    }

    fun warehouseInventory(warehouseId: String): List<WarehouseProductDTO>? {
        LOGGER.debug("Received request for the inventory of warehouse $warehouseId")
        val warehouse = getWarehouseByWarehouseId(warehouseId)
        warehouse?.let {
            LOGGER.debug("Retrieved inventory of warehouse $warehouseId")
            return mapper.toProductDTOList(warehouse.inventory)
        } ?: kotlin.run {
            LOGGER.debug("Could not find warehouse $warehouseId")
            return null
        }
    }

    private fun updateWarehouseProductQuantity(warehouse: Warehouse, warehouseProduct: WarehouseProduct): String {
        LOGGER.debug(
            "Received request to update product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}"
        )
        val product = warehouse.inventory.firstOrNull { it.productId == warehouseProduct.productId }
        if (product == null) {
            LOGGER.debug("Could not find product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}")
            return "product not found"
        }

        val query = Query().addCriteria(
            Criteria.where("warehouseId").`is`(warehouse.warehouseId)
                .and("inventory").elemMatch(
                    Criteria.where("productId").`is`(warehouseProduct.productId)
                        .and("quantity").gte(-warehouseProduct.quantity)
                )
        )
        val transaction = WarehouseTransaction(
            null,
            warehouseProduct.productId,
            warehouseProduct.quantity,
            WarehouseTransactionStatus.ADMIN_MODIFICATION
        )
        val update = Update()
            .inc("inventory.$.quantity", warehouseProduct.quantity)
            .push("transactionList", transaction)

        val updatedWarehouse = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Warehouse::class.java
        )
        updatedWarehouse?.let {
            LOGGER.debug(
                "Updated quantity of product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}"
            )
            checkAlarmThreshold(updatedWarehouse, warehouseProduct.productId)
            return "product updated"
        } ?: kotlin.run {
            LOGGER.debug(
                "Insufficient quantity of product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}"
            )
            return "insufficient quantity"
        }
    }

    fun updateWarehouseProductAlarmThreshold(warehouse: Warehouse, warehouseProduct: WarehouseProduct): String {
        LOGGER.debug("Received request to update alarm threshold for product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}")
        val product = warehouse.inventory.firstOrNull { it.productId == warehouseProduct.productId }
        if (product == null) {
            LOGGER.debug("Could not find product ${warehouseProduct.productId} in warehouse ${warehouse.warehouseId}")
            return "product not found"
        }

        val query = Query().addCriteria(
            Criteria.where("warehouseId").`is`(warehouse.warehouseId)
                .and("inventory").elemMatch(
                    Criteria.where("productId").`is`(warehouseProduct.productId)
                )
        )
        val update = Update().set("inventory.$.alarmThreshold", warehouseProduct.alarmThreshold)
        val updatedWarehouse = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Warehouse::class.java
        )
        updatedWarehouse?.let {
            LOGGER.debug("Updated alarm threshold for product ${warehouseProduct.productId} in ${warehouse.warehouseId} to ${warehouseProduct.alarmThreshold}")
            return "alarm updated"
        } ?: kotlin.run {
            LOGGER.debug("Could not update alarm threshold for product ${warehouseProduct.productId} in ${warehouse.warehouseId}")
            return "alarm update failed"
        }
    }

    private fun addWarehouseProduct(warehouseId: String, warehouseProduct: WarehouseProduct): String {
        // Assumes warehouse exists and product is not found
        LOGGER.debug("Adding product ${warehouseProduct.productId} to warehouse $warehouseId")

        // In case product was added after the "product already exists" check
        val query = Query().addCriteria(
            Criteria.where("warehouseId").`is`(warehouseId)
                .and("inventory.productId").nin(listOf(warehouseProduct.productId))
        )

        val transaction = WarehouseTransaction(
            null,
            warehouseProduct.productId,
            warehouseProduct.quantity,
            WarehouseTransactionStatus.ADMIN_MODIFICATION
        )
        val update = Update().push("inventory", warehouseProduct).push("transactionList", transaction)

        val updatedWarehouse = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Warehouse::class.java
        )

        updatedWarehouse?.let {
            LOGGER.debug("Added product ${warehouseProduct.productId} to warehouse $warehouseId")
            checkAlarmThreshold(updatedWarehouse, warehouseProduct.productId)
            return "product added"
        } ?: kotlin.run {
            LOGGER.debug("Could not add product ${warehouseProduct.productId} to warehouse $warehouseId")
            return "failed to add transaction"
        }
    }

    fun editProduct(warehouseId: String, warehouseProductDTO: WarehouseProductDTO): String {
        LOGGER.debug("Received request to edit product ${warehouseProductDTO.productId} alarm in $warehouseId")
        val warehouseProduct = mapper.toModel(warehouseProductDTO)
        // Additional read, but it allows us to differentiate between failures owed to warehouse not existing and the
        // product not being present in the inventory
        val warehouse = getWarehouseByWarehouseId(warehouseId)
        warehouse?.let {
            val outcome = updateWarehouseProductQuantity(warehouse, warehouseProduct)
            if (outcome == "product not found") {
                return when {
                    warehouseProduct.quantity < 0 -> "negative product quantity"
                    warehouseProduct.alarmThreshold < 0 -> "invalid alarm threshold"
                    else -> addWarehouseProduct(warehouse.warehouseId.toString(), warehouseProduct)
                }
            }
            return outcome
        } ?: kotlin.run {
            return "warehouse not found"
        }
    }

    fun editAlarm(warehouseId: String, warehouseProductDTO: WarehouseProductDTO): String {
        LOGGER.debug("Received request to edit alarm threshold for product ${warehouseProductDTO.productId} in warehouse $warehouseId")
        val warehouseProduct = mapper.toModel(warehouseProductDTO)
        if (warehouseProduct.alarmThreshold < 0) {
            return "invalid alarm threshold"
        }

        val warehouse = getWarehouseByWarehouseId(warehouseId)
        warehouse?.let {
            return updateWarehouseProductAlarmThreshold(warehouse, warehouseProduct)
        } ?: kotlin.run {
            return "warehouse not found"
        }
    }

    private fun createWarehouseDelivery(
        warehouseId: String, orderId: String, orderItems: MutableMap<String, Int>
    ): DeliveryDTO? {
        LOGGER.debug("Creating DeliveryDTO for warehouse $warehouseId")

        // Read warehouse contents first, update optimistically and fail if this operation causes the quantity of any
        // product to drop below zero.
        val warehouse = getWarehouseByWarehouseId(warehouseId)!!

        val orderItemsRemaining = orderItems.filter { it.value > 0 }
        val deliveryProducts = mutableListOf<CartProductDTO>()
        val relevantInventory = warehouse.inventory.filter { it.productId in orderItemsRemaining }
        for (warehouseProduct in relevantInventory) {

            LOGGER.debug("Picking up product ${warehouseProduct.productId} from warehouse $warehouseId")

            val productId = warehouseProduct.productId
            val requestedQuantity = orderItems[productId]!!
            val warehouseQuantity = warehouseProduct.quantity

            var deliveryQuantity = requestedQuantity
            if (warehouseQuantity < requestedQuantity) {
                deliveryQuantity = warehouseQuantity
            }

            val query = Query().addCriteria(
                Criteria.where("warehouseId").`is`(warehouseId)
                    .and("inventory").elemMatch(
                        Criteria.where("productId").`is`(productId)
                            .and("quantity").gte(deliveryQuantity)
                    )
            )
            val transaction = WarehouseTransaction(
                orderId, productId, -deliveryQuantity, WarehouseTransactionStatus.CONFIRMED
            )
            val update = Update()
                .inc("inventory.$.quantity", -deliveryQuantity)
                .push("transactionList", transaction)

            val updatedWarehouse = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions().returnNew(true), Warehouse::class.java
            )
            updatedWarehouse?.let {
                LOGGER.debug("Successfully picked up product $productId from warehouse $warehouseId")
                orderItems[productId] = orderItems[productId]!!.minus(deliveryQuantity)
                deliveryProducts.add(CartProductDTO(ProductDTO(productId), deliveryQuantity))
                checkAlarmThreshold(updatedWarehouse, productId)
            } ?: kotlin.run {
                LOGGER.debug("Failed to pick up product $productId from warehouse $warehouseId")
            }


        }
        if (deliveryProducts.isEmpty()) {
            LOGGER.debug("No products could be picked up from warehouse $warehouseId")
            return null
        }
        return DeliveryDTO(deliveryProducts, warehouseId)
    }

    fun createDeliveryList(orderId: String, cart: List<CartProductDTO>): List<DeliveryDTO>? {
        LOGGER.debug("Received request to compute the delivery list for order $orderId")
        val deliveryList = mutableListOf<DeliveryDTO>()
        val orderItems = cart.associateBy({ it.productDTO.productId }, { it.quantity }).toMutableMap()

        while (orderItems.values.sum() > 0) {
            val mostRequestedProductId = orderItems.maxByOrNull { it.value }!!.key
            val selectedWarehouseId = selectWarehouse(mostRequestedProductId)
            if (selectedWarehouseId == null) {
                LOGGER.debug("Rolling back - Failed to create delivery list for order $orderId")
                // Note: in case of physical failure, a Kafka rollback message will be sent by the order message
                // => Even in this case no transaction information is lost
                rollbackOrder(orderId)
                return null
            }
            val warehouseDeliveryDTO = createWarehouseDelivery(selectedWarehouseId, orderId, orderItems)
            warehouseDeliveryDTO?.let {
                LOGGER.debug("Created delivery list for warehouse $selectedWarehouseId")
                deliveryList.add(warehouseDeliveryDTO)
            }
        }
        return deliveryList
    }

    fun addProduct(warehouseId: String, warehouseProductDTO: WarehouseProductDTO): String {
        val warehouseProduct = mapper.toModel(warehouseProductDTO)
        LOGGER.debug("Received request to add product ${warehouseProduct.productId} to warehouse $warehouseId")
        when {
            warehouseProduct.quantity < 0 -> {
                return "negative product quantity"
            }
            warehouseProduct.alarmThreshold < 0 -> {
                return "negative alarm threshold"
            }
            else -> {
                // Additional read, but it allows us to differentiate between failures owed to warehouse not existing
                // and the product already being present
                val warehouse = getWarehouseByWarehouseId(warehouseId)
                if (warehouse == null) {
                    LOGGER.debug("Could not find warehouse $warehouseId")
                    return "warehouse not found"
                }
                val product = warehouse.inventory.firstOrNull { it.productId == warehouseProduct.productId }
                if (product != null) {
                    LOGGER.debug("Product ${warehouseProduct.productId} already present in warehouse $warehouseId")
                    return "product already present"
                }

                return addWarehouseProduct(warehouseId, warehouseProduct)
            }
        }
    }

}