package it.polito.ap.catalogservice.service.mapper

import it.polito.ap.catalogservice.model.Product
import it.polito.ap.common.dto.CustomerProductDTO
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface CustomerProductDTOMapper {
    fun toDTO(product: Product): CustomerProductDTO
    fun toModel(customerProductDTO: CustomerProductDTO): Product
}