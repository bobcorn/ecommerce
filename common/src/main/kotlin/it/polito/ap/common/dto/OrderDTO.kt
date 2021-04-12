package it.polito.ap.common.dto

import it.polito.ap.common.utils.StatusType

data class OrderDTO (
    var orderId: String,
    var status: StatusType
)