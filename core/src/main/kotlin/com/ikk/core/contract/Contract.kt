package com.ikk.core.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

/** docs/json_contract.md §3. */
@Serializable
data class Contract(
    val schemaVersion: Int = SCHEMA_VERSION,
    val checkpoint: String,
    val screen: String,
    val reference: Reference = Reference(),
    val layout: String = "relative",
    val components: Map<String, DesignNode>,
) {
    /** Paint order is z ascending — never map iteration order. */
    fun paintOrder(): List<DesignNode> = components.values.sortedWith(compareBy({ it.z }, { it.id }))

    /** Hidden nodes stay in the contract and are not emitted. */
    fun emittable(): List<DesignNode> = paintOrder().filter { it.visible }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class Reference(val w: Int = 375, val h: Int = 667, val unit: String = "dp")

/**
 * docs/json_contract.md §2 — an unknown schemaVersion is rejected, never guessed.
 * Migrations are registered here as the version climbs.
 */
object SchemaMigration {

    class UnsupportedSchemaVersion(found: Int) : IllegalStateException(
        "contract schemaVersion $found is not supported by this build " +
            "(expected ${Contract.SCHEMA_VERSION}). Refusing to guess — " +
            "upgrade the client or migrate the document."
    )

    fun check(found: Int) {
        if (found != Contract.SCHEMA_VERSION) throw UnsupportedSchemaVersion(found)
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/**
 * The one JSON configuration. Both the editors and the backend must use it.
 *
 * `ignoreUnknownKeys = false` is deliberate and is validation rule V11: a field
 * one implementation writes and another silently drops is a divergence that
 * only shows up at a demo.
 */
object ContractJson {

    val format: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = true
        classDiscriminator = "type"
        prettyPrint = false
        serializersModule = kotlinx.serialization.modules.SerializersModule {
            contextual(Instant::class, InstantSerializer)
        }
    }

    val pretty: Json = Json(format) { prettyPrint = true; prettyPrintIndent = "  " }

    fun decode(text: String): Contract {
        val probe = format.parseToJsonElement(text)
        val found = probe.let {
            (it as? kotlinx.serialization.json.JsonObject)
                ?.get("schemaVersion")
                ?.let { v -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
        } ?: throw IllegalArgumentException("contract has no schemaVersion")
        SchemaMigration.check(found)
        return format.decodeFromString(Contract.serializer(), text)
    }

    fun encode(contract: Contract): String =
        format.encodeToString(Contract.serializer(), contract)

    fun encodePretty(contract: Contract): String =
        pretty.encodeToString(Contract.serializer(), contract)
}
