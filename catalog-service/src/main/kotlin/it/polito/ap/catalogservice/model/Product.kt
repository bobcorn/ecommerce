package it.polito.ap.catalogservice.model

import it.polito.ap.common.utils.CategoryType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class Product(
    @Id
    var name: String = "",
    var description: String = "",
    var picture: String = "",
    var category: CategoryType? = null,
    var price: Double = 0.0,
    var quantity: Int = 0  // Cached info from WarehouseService
)