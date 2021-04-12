package it.polito.ap.common.dto

import it.polito.ap.common.utils.CategoryType
import java.lang.IllegalArgumentException

data class CustomerProductDTO(
    var name: String = "",
    var description: String = "",
    var picture: String = "",
    var category: CategoryType? = null,
    var price: Double = 0.0,
    var quantity: Int = 0
)
