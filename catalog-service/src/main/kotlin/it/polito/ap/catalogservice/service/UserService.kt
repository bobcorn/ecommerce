package it.polito.ap.catalogservice.service

import it.polito.ap.catalogservice.model.User
import it.polito.ap.catalogservice.repository.UserRepository
import it.polito.ap.common.utils.RoleType
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserService(val userRepository: UserRepository) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UserService::class.java)
    }

    fun getUserByEmail(email: String): User? {
        LOGGER.info("Retrieving user with email: $email")
        val user = userRepository.findByEmail(email)
        user?.let {
            LOGGER.info("Retrieved user with email: $email")
        } ?: kotlin.run {
            LOGGER.info("Could not find user with email: $email")
        }
        return user
    }

    fun getUserById(userId: ObjectId): User? {
        LOGGER.info("Retrieving user with id: $userId")
        val user = userRepository.findByUserId(userId)
        user?.let {
            LOGGER.info("Retrieved user with id: $userId")
        } ?: kotlin.run {
            LOGGER.info("Could not find user with id: $userId")
        }
        return user
    }

    fun getUsersByRole(role: RoleType): List<User>? {
        LOGGER.info("Retrieving users with role: $role")
        val users = userRepository.findByRole(role)
        users?.let {
            LOGGER.info("Retrieved users with role: $role")
        } ?: kotlin.run {
            LOGGER.info("Could not find users with role: $role")
        }
        return users
    }
}
