package it.polito.ap.catalogservice.config

import it.polito.ap.catalogservice.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationProvider(val userRepository: UserRepository) : AuthenticationProvider {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CustomAuthenticationProvider::class.java)
    }

    @Throws(AuthenticationException::class)
    override fun authenticate(authentication: Authentication): Authentication? {
        LOGGER.info(
            "Authentication user ${authentication.name},${authentication.credentials} -> ${
                authentication.credentials.toString()
            }"
        )
        val user = userRepository.findByEmailAndPassword(authentication.name, authentication.credentials.toString())
        user?.let {
            val authorities = setOf(SimpleGrantedAuthority(user.role.toString()))
            LOGGER.info("Found user ${user.name}, ${user.surname}, $authorities")
            return UsernamePasswordAuthenticationToken(user, authentication.name, authorities)
        } ?: kotlin.run {
            LOGGER.info("User not found into the DB")
            return null
        }
    }

    override fun supports(authentication: Class<*>?): Boolean {
        return authentication == UsernamePasswordAuthenticationToken::class.java
    }
}