package it.polito.ap.catalogservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@EnableWebSecurity
@Configuration
class SecurityConfig(val customAuthenticationProvider: CustomAuthenticationProvider) : WebSecurityConfigurerAdapter() {

    @Throws(Exception::class)
    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(customAuthenticationProvider)
    }


    override fun configure(http: HttpSecurity) {
        http.httpBasic()
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, "/products").hasRole("ADMIN")
            .antMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")
            .antMatchers(HttpMethod.POST, "/products/placeOrder").hasAnyRole("CUSTOMER", "ADMIN")
            .antMatchers(HttpMethod.PUT, "/products/order/**").hasAnyRole("CUSTOMER", "ADMIN")
            .antMatchers(HttpMethod.GET, "/products/orderStatus/**").hasAnyRole("CUSTOMER", "ADMIN")
            .antMatchers(HttpMethod.GET, "/products/walletFunds**").hasAnyRole("CUSTOMER", "ADMIN")
            .antMatchers(HttpMethod.GET, "/products/walletTransactions**").hasAnyRole("CUSTOMER", "ADMIN")
            .antMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")
            .and()
            .csrf().disable()
    }

}