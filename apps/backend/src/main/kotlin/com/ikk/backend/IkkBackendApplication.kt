package com.ikk.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class IkkBackendApplication

fun main(args: Array<String>) {
    runApplication<IkkBackendApplication>(*args)
}
