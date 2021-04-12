package it.polito.ap.warehouseservice.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class Warehouse(
    @Id
    val warehouseId: ObjectId,
    val name: String,
    var inventory: MutableList<WarehouseProduct>,
    var transactionList: MutableList<WarehouseTransaction>,
    var adminEmails: List<String>
)
