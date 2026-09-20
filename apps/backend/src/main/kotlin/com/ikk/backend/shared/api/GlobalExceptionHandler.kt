package com.ikk.backend.shared.api

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun api(exception: ApiException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(exception.status).body(
            ApiError(exception.code, exception.message, request.requestId(), exception.details),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidArgument(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val details = exception.bindingResult.allErrors.map { error ->
            if (error is FieldError) "${error.field}: ${error.defaultMessage}" else error.defaultMessage
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            ApiError("validation_failed", "request validation failed", request.requestId(), details),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed(request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError("malformed_request", "request body is not valid JSON", request.requestId()),
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(
            ApiError("asset_too_large", "asset exceeds the configured size limit", request.requestId()),
        )

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        log.error("Unhandled request failure", exception)
        return ResponseEntity.internalServerError().body(
            ApiError("internal_error", "unexpected server error", request.requestId()),
        )
    }

    private fun HttpServletRequest.requestId(): String =
        getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)?.toString() ?: "unknown"

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
