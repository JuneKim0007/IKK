package com.ikk.backend.contract

import com.ikk.backend.shared.api.ApiException
import com.ikk.core.contract.Contract
import com.ikk.core.contract.Violation
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Service
class ContractService(private val json: JsonMapper) {
    fun validate(tree: JsonNode): List<Violation> = BackendContract.validate(tree)

    fun requireValid(tree: JsonNode): Contract {
        val violations = validate(tree)
        if (violations.isNotEmpty()) {
            throw ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "contract_invalid",
                "contract failed validation",
                violations,
            )
        }
        return try {
            BackendContract.decode(tree.toString())
        } catch (exception: RuntimeException) {
            throw ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "contract_invalid",
                "contract shape is invalid",
                listOf(Violation("V11", "root", exception.message?.lineSequence()?.first() ?: "invalid JSON")),
            )
        }
    }

    fun normalize(tree: JsonNode): JsonNode =
        json.readTree(BackendContract.encode(requireValid(tree)))
}
