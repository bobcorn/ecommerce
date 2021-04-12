package it.polito.ap.warehouseservice.model

import it.polito.ap.warehouseservice.model.utils.WarehouseTransactionStatus

class WarehouseTransaction(
    var orderId: String?,
    var productId: String,
    var quantity: Int,
    var status: WarehouseTransactionStatus
)