package it.polito.ap.warehouseservice.model

class WarehouseProduct(
    var productId: String,
    var quantity: Int = 0,
    var alarmThreshold: Int = -1
)