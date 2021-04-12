package it.polito.ap.warehouseservice

import it.polito.ap.warehouseservice.model.Warehouse
import it.polito.ap.warehouseservice.model.WarehouseProduct
import it.polito.ap.warehouseservice.repository.WarehouseRepository
import org.bson.types.ObjectId
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableCaching
@EnableScheduling
class WarehouseServiceApplication(
    warehouseRepository: WarehouseRepository
) {
    init {
        // Example warehouses for testing purposes
        warehouseRepository.deleteAll()

        val warehouse1 = Warehouse(
            ObjectId("111111111111111111111111"), "warehouse1",
            mutableListOf(
                WarehouseProduct("prod1", 10, 5),
                WarehouseProduct("prod2", 10, 1),
                WarehouseProduct("prod3", 10, 4)
            ),
            mutableListOf(),
            listOf("piem@yopmail.com")
        )
        val warehouse2 = Warehouse(
            ObjectId("222222222222222222222222"), "warehouse2",
            mutableListOf(
                WarehouseProduct("prod2", 6, 5),
                WarehouseProduct("prod3", 20, 5),
                WarehouseProduct("prod4", 10, 3),
                WarehouseProduct("prod5", 10, 4)
            ),
            mutableListOf(),
            listOf("general_admin@yopmail.com")
        )
        warehouseRepository.saveAll(listOf(warehouse1, warehouse2))

    }
}

fun main(args: Array<String>) {
    runApplication<WarehouseServiceApplication>(*args)
}
