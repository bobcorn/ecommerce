package it.polito.ap.walletservice.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class Wallet(
    @Id
    val userId: ObjectId
) {
    var funds: Double = 0.0
    var transactionList: List<Transaction> = mutableListOf()
}