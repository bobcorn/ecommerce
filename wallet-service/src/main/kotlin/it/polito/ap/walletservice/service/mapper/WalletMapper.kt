package it.polito.ap.walletservice.service.mapper

import it.polito.ap.common.dto.TransactionDTO
import it.polito.ap.walletservice.model.Transaction
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface WalletMapper {
    fun toModel(transactionDTO: TransactionDTO): Transaction
    fun toDTO(transaction: Transaction): TransactionDTO
}