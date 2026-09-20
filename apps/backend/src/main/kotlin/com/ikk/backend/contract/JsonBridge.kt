package com.ikk.backend.contract

import com.ikk.core.contract.ContractJson
import kotlinx.serialization.json.JsonElement
import tools.jackson.databind.JsonNode

/** Keeps Jackson at the HTTP boundary and kotlinx.serialization in the domain core. */
internal fun JsonNode.toKotlinxJson(): JsonElement =
    ContractJson.format.parseToJsonElement(toString())
