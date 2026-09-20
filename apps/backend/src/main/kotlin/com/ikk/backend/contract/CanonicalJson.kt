package com.ikk.backend.contract

import com.ikk.core.contract.Contract
import com.ikk.core.contract.ContractJson
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Deterministic section-13 serialization and the server-owned payload checksum. */
object CanonicalJson {
    private val rectFields = setOf("x", "y", "w", "h")

    fun canonicalJson(contract: Contract): String =
        canonicalJson(ContractJson.format.parseToJsonElement(ContractJson.encode(contract)))

    fun canonicalJson(value: JsonElement): String = render(value, emptyList())

    fun canonicalJson(value: tools.jackson.databind.JsonNode): String =
        canonicalJson(value.toKotlinxJson())

    fun canonicalBytes(value: JsonElement): ByteArray =
        canonicalJson(value).toByteArray(StandardCharsets.UTF_8)

    fun canonicalBytes(value: tools.jackson.databind.JsonNode): ByteArray =
        canonicalBytes(value.toKotlinxJson())

    fun checksum(value: JsonElement): String =
        "sha256:" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(canonicalBytes(value)),
        )

    fun checksum(value: tools.jackson.databind.JsonNode): String = checksum(value.toKotlinxJson())

    fun checksum(contract: Contract): String =
        checksum(ContractJson.format.parseToJsonElement(ContractJson.encode(contract)))

    private fun render(value: JsonElement, path: List<String>): String = when (value) {
        JsonNull -> "null"
        is JsonObject -> value.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, child) ->
                "${JsonPrimitive(key)}:${render(child, path + key)}"
            }
        is JsonArray -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
            render(it, path)
        }
        is JsonPrimitive -> renderPrimitive(value, path)
    }

    private fun renderPrimitive(value: JsonPrimitive, path: List<String>): String {
        if (value.isString) return value.toString()
        if (value.content == "true" || value.content == "false") return value.content

        val number = value.content.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("unsupported JSON primitive ${value.content}")
        if (path.size >= 2 && path[path.lastIndex - 1] == "rect" && path.last() in rectFields) {
            return number.setScale(1, RoundingMode.HALF_EVEN).toPlainString()
        }
        return if (number.signum() == 0) "0" else number.stripTrailingZeros().toPlainString()
    }
}
