package it.polito.ap.common.dto

data class CartProductDTO (
    var productDTO: ProductDTO,
    var quantity: Int
)