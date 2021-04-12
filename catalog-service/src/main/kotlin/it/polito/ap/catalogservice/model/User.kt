package it.polito.ap.catalogservice.model

import it.polito.ap.common.utils.RoleType
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class User(
    @Id
    val userId: ObjectId = ObjectId(),
    var email: String,
    var password: String,
    val name: String,
    val surname: String,
    val deliveryAddress: String? = null,
) {
    var role = RoleType.ROLE_CUSTOMER
}