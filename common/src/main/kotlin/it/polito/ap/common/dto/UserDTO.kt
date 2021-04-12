package it.polito.ap.common.dto

import it.polito.ap.common.utils.RoleType

data class UserDTO (
    var userId: String,
    var role: RoleType
)