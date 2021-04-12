package it.polito.ap.walletservice.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.polito.ap.common.dto.TransactionDTO
import it.polito.ap.common.utils.TransactionMotivation
import it.polito.ap.walletservice.model.Wallet
import it.polito.ap.walletservice.repository.WalletRepository
import it.polito.ap.walletservice.service.mapper.WalletMapper
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class WalletService(
    val walletRepository: WalletRepository,
    val mapper: WalletMapper,
    val mongoTemplate: MongoTemplate
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WalletService::class.java)
    }

    private val jacksonObjectMapper = jacksonObjectMapper()

    @KafkaListener(groupId = "wallet_service", topics = ["rollback"])
    fun rollbackListener(message: String) {
        val orderId = jacksonObjectMapper.readValue<String>(message)
        LOGGER.debug("Ensuring consistency of order $orderId")
        val wallet = walletRepository.getWalletByIssuerId(orderId)
        wallet?.let {
            LOGGER.debug("Transaction present in the database, rolling back")
            val relevantTransaction = wallet.transactionList.filter { it.issuerId == orderId }[0]
            val rollbackTransactionDTO = TransactionDTO(
                orderId,
                -relevantTransaction.amount,
                TransactionMotivation.AUTOMATED_REFUND
            )
            val updatedFunds = addTransaction(wallet.userId.toString(), rollbackTransactionDTO)
            LOGGER.debug("Rollback successful, updated funds: $updatedFunds")
        } ?: kotlin.run {
            LOGGER.debug("Transaction not present in the database, no rollback needed")
        }
    }

    fun getWalletByUserId(userId: String): Wallet? {
        LOGGER.debug("Received request to retrieve the wallet of user $userId")
        val wallet = walletRepository.getWalletByUserId(userId)
        wallet?.let {
            LOGGER.debug("Retrieved the wallet of user $userId")
            return it
        } ?: kotlin.run {
            LOGGER.debug("Could not find wallet for user $userId")
            return null
        }
    }

    fun availableFunds(userId: String): Double? {
        LOGGER.debug("Received request to retrieve the funds of user $userId")
        val wallet = getWalletByUserId(userId)
        wallet?.let {
            return it.funds
        } ?: kotlin.run {
            return null
        }
    }

    fun transactionList(userId: String): List<TransactionDTO>? {
        LOGGER.debug("Received request for the transaction list of user $userId")
        val wallet = getWalletByUserId(userId)
        return wallet?.transactionList?.map { mapper.toDTO(it) }
    }

    fun addTransaction(userId: String, transactionDTO: TransactionDTO): Double? {
        LOGGER.debug("Received request to add a transaction to the wallet of user $userId")
        val transaction = mapper.toModel(transactionDTO)

        val query = Query()
        if (transaction.amount < 0) {
            query.addCriteria(
                Criteria.where("userId").`is`(userId).and("funds").gte(-transaction.amount)
            )
        } else {
            query.addCriteria(Criteria.where("userId").`is`(userId))
        }
        val update = Update().inc("funds", transaction.amount).push("transactionList", transaction)
        val wallet = mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions().returnNew(true), Wallet::class.java
        )

        // Note: the query's output is null both when the wallet could not be found and when the transaction could not
        // be completed successfully (meaning the $match on "funds" yielded no documents)
        wallet?.let {
            LOGGER.debug("Transaction carried out successfully")
        } ?: kotlin.run {
            LOGGER.debug("Transaction failed")
        }
        return wallet?.funds
    }
}