package it.polito.ap.walletservice.controller

import it.polito.ap.common.dto.TransactionDTO
import it.polito.ap.walletservice.service.WalletService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/wallets")
class WalletController(val walletService: WalletService) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WalletController::class.java)
    }

    @GetMapping("/{userId}/funds")
    fun availableFunds(@PathVariable userId: String): ResponseEntity<Double> {
        LOGGER.info("Received request to retrieve the wallet of user $userId")
        val availableFunds = walletService.availableFunds(userId)
        availableFunds?.let {
            LOGGER.info("Available funds for user $userId: $availableFunds")
            return ResponseEntity.ok(availableFunds)
        } ?: kotlin.run {
            LOGGER.info("Could not find wallet for user $userId")
            return ResponseEntity(null, HttpStatus.NOT_FOUND)
        }
    }

    @GetMapping("/{userId}/transactions")
    fun transactionList(@PathVariable userId: String): ResponseEntity<List<TransactionDTO>> {
        LOGGER.info("Received request for the transaction list of user $userId")
        val transactionList = walletService.transactionList(userId)
        transactionList?.let {
            LOGGER.info("Retrieved the transaction list of user $userId")
            return ResponseEntity.ok(transactionList)
        } ?: kotlin.run {
            LOGGER.info("Could not find wallet for user $userId")
            return ResponseEntity(null, HttpStatus.NOT_FOUND)
        }
    }

    @PutMapping("/{userId}/transactions")
    fun addTransaction(
        @PathVariable userId: String, @RequestBody transactionDTO: TransactionDTO
    ): ResponseEntity<String> {
        LOGGER.info("Received a transaction for the wallet of user $userId")
        val newUserBalance = walletService.addTransaction(userId, transactionDTO)
        newUserBalance?.let {
            val statusString = "Transaction carried out successfully - New user balance: $newUserBalance"
            LOGGER.info(statusString)
            return ResponseEntity.ok(statusString)
        } ?: kotlin.run {
            val statusString = "Transaction failed - Nonexistent user or insufficient funds"
            LOGGER.info(statusString)
            return ResponseEntity(statusString, HttpStatus.BAD_REQUEST)
        }
    }
}