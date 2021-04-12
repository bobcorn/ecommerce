package it.polito.ap.common.dto

import it.polito.ap.common.utils.TransactionMotivation

data class TransactionDTO(
    var issuerId: String,
    var amount: Double,
    var transactionMotivation: TransactionMotivation,
)
