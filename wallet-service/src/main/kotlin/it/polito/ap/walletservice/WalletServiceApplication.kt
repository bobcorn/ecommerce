package it.polito.ap.walletservice

import it.polito.ap.walletservice.model.Wallet
import it.polito.ap.walletservice.repository.WalletRepository
import org.bson.types.ObjectId
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WalletServiceApplication(
    walletRepository: WalletRepository
) {
    init {
        walletRepository.deleteAll()
        val wallet1 = Wallet(ObjectId("333333333333333333333333"))
        val wallet2 = Wallet(ObjectId("444444444444444444444444"))
        walletRepository.saveAll(listOf(wallet1, wallet2))
    }
}

fun main(args: Array<String>) {
    runApplication<WalletServiceApplication>(*args)
}
