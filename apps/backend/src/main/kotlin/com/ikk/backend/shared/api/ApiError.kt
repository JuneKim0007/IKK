package com.ikk.backend.shared.api

import org.springframework.http.HttpStatus

data class ApiError(
    val error: String,
    val message: String,
    val requestId: String,
    val details: List<Any?> = emptyList(),
)

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val details: List<Any?> = emptyList(),
) : RuntimeException(message)
