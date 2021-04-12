package it.polito.ap.common.dto

data class DeliveryDTO (
    val deliveryProducts: List<CartProductDTO>,
    val warehouseId: String
)