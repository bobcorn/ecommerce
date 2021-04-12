package it.polito.ap.common.dto

data class WarehouseProductDTO (
    var productId: String,
    var quantity: Int = -1,
    var alarmThreshold: Int = -1
)