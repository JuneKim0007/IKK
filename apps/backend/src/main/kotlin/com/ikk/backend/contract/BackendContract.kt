package com.ikk.backend.contract

import com.ikk.core.contract.Contract
import com.ikk.core.contract.ContractJson
import com.ikk.core.contract.ContractValidator
import com.ikk.core.contract.DesignNode
import com.ikk.core.contract.ValidationResult
import com.ikk.core.contract.Violation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** HTTP-facing adapter around the model and codec owned by :packages:design-contract. */
object BackendContract {
    fun decode(text: String): Contract {
        val raw = ContractJson.format.parseToJsonElement(text)
        requireValid(raw)
        return ContractJson.decode(text)
    }

    fun encode(contract: Contract): String = ContractJson.encode(contract)

    fun validate(text: String): List<Violation> =
        validate(ContractJson.format.parseToJsonElement(text))

    fun validate(raw: tools.jackson.databind.JsonNode): List<Violation> =
        validate(raw.toKotlinxJson())

    fun validate(raw: JsonElement): List<Violation> {
        val rawViolations = RawContractValidator.validate(raw)
        val decoded = runCatching { ContractJson.decode(raw.toString()) }.getOrNull()
            ?: return rawViolations.ifEmpty {
                listOf(Violation("V11", "root", "contract does not match the v1 wire schema"))
            }
        return merge(rawViolations, coreViolations(decoded))
    }

    fun validate(contract: Contract): List<Violation> {
        val raw = ContractJson.format.parseToJsonElement(ContractJson.encode(contract))
        return merge(RawContractValidator.validate(raw), coreViolations(contract))
    }

    fun requireValid(raw: JsonElement) {
        val violations = validate(raw)
        if (violations.isNotEmpty()) throw ContractValidationException(violations)
    }

    fun requireValid(raw: tools.jackson.databind.JsonNode) {
        val violations = validate(raw)
        if (violations.isNotEmpty()) throw ContractValidationException(violations)
    }

    /** Derives the map key for a sync payload, which intentionally carries no key itself. */
    fun derivedKey(payload: JsonObject): String {
        val type = (payload["type"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?: throw IllegalArgumentException("node payload has no string type")
        val name = (payload["name"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("node payload has no non-blank name")
        require(type in setOf("rect", "ellipse", "triangle", "line", "text", "image")) {
            "unsupported node type \"$type\""
        }
        return "${type}_${DesignNode.slug(name)}"
    }

    fun derivedKey(payload: tools.jackson.databind.JsonNode): String {
        val raw = payload.toKotlinxJson() as? JsonObject
            ?: throw IllegalArgumentException("node payload must be a JSON object")
        return derivedKey(raw)
    }

    private fun coreViolations(contract: Contract): List<Violation> =
        when (val result = ContractValidator.validate(contract)) {
            ValidationResult.Ok -> emptyList()
            is ValidationResult.Invalid -> result.violations.map { violation ->
                if (violation.rule == "V2" && "disagrees with" in violation.message) {
                    violation.copy(rule = "V15")
                } else {
                    violation
                }
            }
        }

    private fun merge(first: List<Violation>, second: List<Violation>): List<Violation> {
        val seen = mutableSetOf<Pair<String, String>>()
        return (first + second).filter { seen.add(it.rule to it.where) }
    }
}

class ContractValidationException(violations: List<Violation>) : IllegalArgumentException(
    violations.joinToString("; "),
) {
    val violations: List<Violation> = violations.toList()
}
