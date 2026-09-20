package com.ikk.backend.contract

import com.ikk.core.contract.Contract
import com.ikk.core.contract.DesignNode
import com.ikk.core.contract.Violation
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Validates the raw wire tree so HTTP responses can name contract rules before decoding fails. */
object RawContractValidator {
    private val keyPattern = Regex("^(rect|ellipse|triangle|line|text|image)_[A-Za-z0-9]+$")
    private val colorPattern = Regex("^#[0-9A-F]{6}([0-9A-F]{2})?$")
    private val nodeTypes = setOf("rect", "ellipse", "triangle", "line", "text", "image")

    private val rootFields = setOf(
        "schemaVersion", "checkpoint", "screen", "reference", "layout", "components",
    )
    private val referenceFields = setOf("w", "h", "unit")
    private val nodeFields = setOf(
        "id", "type", "name", "z", "visible", "opacity", "rect", "fill", "stroke",
        "radius", "text", "version", "updatedAt", "line",
    )
    private val imageFields = nodeFields + setOf("source", "contentScale", "alt")
    private val rectFields = setOf("x", "y", "w", "h", "unit")
    private val strokeFields = setOf("color", "width")
    private val textFields = setOf(
        "value", "size", "align", "color", "weight", "lineHeight", "fontFamily", "maxLines",
    )
    private val sourceFields = setOf("ref", "mime")
    private val lineFields = setOf("orientation")

    fun validate(root: tools.jackson.databind.JsonNode): List<Violation> =
        validate(root.toKotlinxJson())

    fun validate(root: JsonElement): List<Violation> {
        val contract = root as? JsonObject
            ?: return listOf(Violation("V11", "root", "contract must be a JSON object"))
        val violations = mutableListOf<Violation>()

        if (contract["schemaVersion"].intValue() != Contract.SCHEMA_VERSION) {
            violations += Violation(
                "V1",
                "root",
                "schemaVersion must equal ${Contract.SCHEMA_VERSION}",
            )
        }

        unknownFields(contract, rootFields, "root", violations)
        unknownFields(contract["reference"], referenceFields, "reference", violations)

        val checkpoint = contract["checkpoint"].stringValue()
        if (checkpoint == null || !CHECKPOINT_PATTERN.matches(checkpoint)) {
            violations += Violation("V18", "root.checkpoint", "checkpoint must match ^cp_[0-9]{3,60}$")
        }
        val screen = contract["screen"].stringValue()
        if (screen == null || !SCREEN_PATTERN.matches(screen)) {
            violations += Violation("V21", "root.screen", "screen must be PascalCase and at most 200 characters")
        }

        val reference = contract["reference"] as? JsonObject
        val referenceWidth = reference?.get("w").intValue()
        val referenceHeight = reference?.get("h").intValue()
        if (referenceWidth == null || referenceHeight == null || referenceWidth <= 0 || referenceHeight <= 0 ||
            reference?.get("unit").stringValue() != "dp"
        ) {
            violations += Violation(
                "V16",
                "reference",
                "reference must have positive integer dimensions and unit dp",
            )
        }

        val components = contract["components"] as? JsonObject ?: return violations
        val seenIds = mutableSetOf<String>()
        val seenZ = mutableSetOf<Int>()
        val seenNames = mutableSetOf<String>()

        for ((key, element) in components.entries.sortedBy { it.key }) {
            if (!keyPattern.matches(key)) {
                violations += Violation(
                    "V2",
                    key,
                    "key must match ^(rect|ellipse|triangle|line|text|image)_[A-Za-z0-9]+$",
                )
            }

            val node = element as? JsonObject ?: continue
            val type = node["type"].stringValue()
            unknownFields(node, if (type == "image") imageFields else nodeFields, key, violations)
            unknownFields(node["rect"], rectFields, "$key.rect", violations)
            unknownFields(node["stroke"], strokeFields, "$key.stroke", violations)
            unknownFields(node["text"], textFields, "$key.text", violations)
            unknownFields(node["line"], lineFields, "$key.line", violations)
            if (type == "image") {
                unknownFields(node["source"], sourceFields, "$key.source", violations)
            }

            val text = node["text"] as? JsonObject
            if (text != null) {
                val lineHeight = text["lineHeight"]
                val maxLines = text["maxLines"]
                if (text["fontFamily"].stringValue() != "Roboto" ||
                    (!lineHeight.isNullOrMissing() && (lineHeight.finiteNumber() ?: 0.0) <= 0.0) ||
                    (!maxLines.isNullOrMissing() && (maxLines.intValue() ?: 0) <= 0)
                ) {
                    violations += Violation(
                        "V19",
                        "$key.text",
                        "fontFamily must be Roboto and lineHeight/maxLines must be positive when set",
                    )
                }
            }

            node["id"].stringValue()?.let { id ->
                if (!seenIds.add(id)) {
                    violations += Violation("V3", key, "duplicate id \"$id\"")
                }
            }
            val id = node["id"].stringValue()
            if (id == null || id.isBlank() || id.length > 64) {
                violations += Violation("V20", key, "id must contain 1..64 characters")
            }
            if (key.length > 240) {
                violations += Violation("V22", key, "component key must be at most 240 characters")
            }

            val opacity = node["opacity"].finiteNumber()
            if (opacity == null || opacity !in 0.0..1.0) {
                violations += Violation("V4", key, "opacity must be within 0.0..1.0")
            }

            val rect = node["rect"] as? JsonObject
            val width = rect?.get("w").finiteNumber()
            val height = rect?.get("h").finiteNumber()
            if (width == null || height == null || width <= 0.0 || height <= 0.0) {
                violations += Violation("V5", key, "rect width and height must be positive")
            }
            if (rect != null && rectFields.minus("unit").any { !rect[it].hasAtMostOneDecimalPlace() }) {
                violations += Violation("V17", "$key.rect", "rect values must have at most one decimal place")
            }

            val radius = node["radius"]
            val numericRadius = radius.finiteNumber()
            val radiusIsValid = (numericRadius != null && numericRadius >= 0.0) ||
                radius.stringValue() == "50%"
            if (!radiusIsValid) {
                violations += Violation(
                    "V6",
                    key,
                    "radius must be non-negative or exactly \"50%\"",
                )
            }

            if (type == "text" && node["text"].isNullOrMissing()) {
                violations += Violation("V7", key, "a text node must carry text")
            }
            if (type == "image" && node["contentScale"].isNullOrMissing()) {
                violations += Violation("V8", key, "an image node must set contentScale")
            }

            checkColor(node["fill"], "$key.fill", violations)
            checkColor((node["stroke"] as? JsonObject)?.get("color"), "$key.stroke.color", violations)
            checkColor((node["text"] as? JsonObject)?.get("color"), "$key.text.color", violations)

            if (type == "ellipse" && radius.stringValue() != "50%") {
                violations += Violation("V10", key, "an ellipse must have radius \"50%\"")
            }
            if (type == "triangle" && (!node["stroke"].isNullOrMissing() || numericRadius != 0.0)) {
                violations += Violation("V23", key, "a triangle carries no stroke or radius")
            }
            if (type == "line") {
                val strokeWidth = (node["stroke"] as? JsonObject)?.get("width").finiteNumber()
                if (!node["fill"].isNullOrMissing() || strokeWidth == null || strokeWidth <= 0.0) {
                    violations += Violation("V24", key, "a line has no fill and requires a positive stroke width")
                }
            }

            node["z"].intValue()?.let { z ->
                if (!seenZ.add(z)) {
                    violations += Violation("V12", key, "duplicate z value $z")
                }
            }

            val name = node["name"].stringValue()
            if (name == null || name.isBlank()) {
                violations += Violation("V13", key, "name is blank")
            } else {
                val normalizedName = name.trim().lowercase(Locale.ROOT)
                if (!seenNames.add(normalizedName)) {
                    violations += Violation("V14", key, "duplicate name \"$name\" in this scope")
                }
                if (type in nodeTypes) {
                    val expected = "${type}_${DesignNode.slug(name)}"
                    if (key != expected) {
                        violations += Violation(
                            "V15",
                            key,
                            "key must equal the derived key \"$expected\"",
                        )
                    }
                }
            }
        }

        return violations
    }

    private fun unknownFields(
        element: JsonElement?,
        allowed: Set<String>,
        where: String,
        violations: MutableList<Violation>,
    ) {
        val value = element as? JsonObject ?: return
        for (field in (value.keys - allowed).sorted()) {
            violations += Violation("V11", "$where.$field", "unknown field \"$field\"")
        }
    }

    private fun checkColor(
        element: JsonElement?,
        where: String,
        violations: MutableList<Violation>,
    ) {
        if (element == null || element === JsonNull) return
        val value = element.stringValue()
        if (value == null || !colorPattern.matches(value)) {
            violations += Violation(
                "V9",
                where,
                "colour must be uppercase #RRGGBB or #RRGGBBAA",
            )
        }
    }

    private fun JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.intValue(): Int? =
        (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonElement?.finiteNumber(): Double? =
        (this as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.doubleOrNull
            ?.takeIf { it.isFinite() }

    private fun JsonElement?.hasAtMostOneDecimalPlace(): Boolean {
        val primitive = (this as? JsonPrimitive)?.takeUnless { it.isString } ?: return false
        val number = primitive.content.toBigDecimalOrNull() ?: return false
        return number.scale() <= 1
    }

    private fun JsonElement?.isNullOrMissing(): Boolean = this == null || this === JsonNull

    private val CHECKPOINT_PATTERN = Regex("^cp_[0-9]{3,60}$")
    private val SCREEN_PATTERN = Regex("^[A-Z][A-Za-z0-9]{0,199}$")
}
