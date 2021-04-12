package it.polito.ap.orderservice.model

class Delivery(
    var shippingAddress: String = "",
    var deliveryProducts: MutableMap<String, Int> = mutableMapOf(),
    var warehouseId: String = ""
)