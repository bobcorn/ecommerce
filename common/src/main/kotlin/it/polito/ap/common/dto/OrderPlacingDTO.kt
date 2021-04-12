package it.polito.ap.common.dto

data class OrderPlacingDTO(
    var cart: List<CartProductDTO>,
    var user: UserDTO,
    var shippingAddress: String
)
