package it.polito.ap.catalogservice.repository

import it.polito.ap.catalogservice.model.User
import it.polito.ap.common.utils.RoleType
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByEmailAndPassword(email: String, password: String): User?
    fun findByEmail(email: String): User?
    fun findByUserId(userId: ObjectId): User?
    fun findByRole(role: RoleType): List<User>?
}