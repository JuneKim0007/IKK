package com.ikk.core.contract

import java.math.BigDecimal

/** docs/json_contract.md §12. Reject, do not repair. */
sealed interface ValidationResult {
    data object Ok : ValidationResult
    data class Invalid(val violations: List<Violation>) : ValidationResult
}

data class Violation(val rule: String, val where: String, val message: String) {
    override fun toString() = "$rule at $where: $message"
}

object ContractValidator {

    private val KEY_PATTERN = Regex("^(rect|ellipse|text|image)_[A-Za-z0-9]+$")

    fun validate(contract: Contract): ValidationResult {
        val v = mutableListOf<Violation>()

        if (contract.schemaVersion != Contract.SCHEMA_VERSION) {
            v += Violation("V1", "root", "schemaVersion ${contract.schemaVersion} != ${Contract.SCHEMA_VERSION}")
        }
        if (contract.layout != "relative") {
            v += Violation("V1", "root", "layout \"${contract.layout}\" is not supported in v1")
        }
        if (contract.reference.w <= 0 || contract.reference.h <= 0 || contract.reference.unit != "dp") {
            v += Violation("V16", "reference", "reference must have positive dimensions and unit dp")
        }
        if (!Regex("^cp_[0-9]{3,60}$").matches(contract.checkpoint)) {
            v += Violation("V18", "root.checkpoint", "checkpoint must match ^cp_[0-9]{3,60}$")
        }
        if (!Regex("^[A-Z][A-Za-z0-9]{0,199}$").matches(contract.screen)) {
            v += Violation("V21", "root.screen", "screen must be PascalCase and at most 200 characters")
        }

        val seenIds = mutableSetOf<String>()
        val seenZ = mutableSetOf<Int>()
        val seenNames = mutableSetOf<String>()

        for ((key, node) in contract.components) {
            if (!KEY_PATTERN.matches(key)) {
                v += Violation("V2", key, "key does not match {type}_{slug(name)}")
            }
            if (key != node.key) {
                v += Violation("V2", key, "key disagrees with the node's own key \"${node.key}\"")
            }
            if (!seenIds.add(node.id)) {
                v += Violation("V3", key, "duplicate id \"${node.id}\"")
            }
            if (node.id.isBlank() || node.id.length > 64) {
                v += Violation("V20", key, "id must contain 1..64 characters")
            }
            if (key.length > 240) {
                v += Violation("V22", key, "component key must be at most 240 characters")
            }
            if (node.opacity !in 0.0..1.0) {
                v += Violation("V4", key, "opacity ${node.opacity} out of 0.0..1.0")
            }
            if (node.rect.w <= 0 || node.rect.h <= 0) {
                v += Violation("V5", key, "rect must have positive size, got ${node.rect.w}x${node.rect.h}")
            }
            if (listOf(node.rect.x, node.rect.y, node.rect.w, node.rect.h).any { !it.hasAtMostOneDecimalPlace() }) {
                v += Violation("V17", "$key.rect", "rect values must have at most one decimal place")
            }
            val r = node.radius
            if (r is Radius.Dp && r.dp < 0) {
                v += Violation("V6", key, "radius ${r.dp} is negative")
            }
            if (node is ImageNode && node.source != null && node.source.ref.isBlank()) {
                v += Violation("V8", key, "an image source must have a non-blank ref")
            }
            node.text?.let { text ->
                if (text.fontFamily != "Roboto" ||
                    (text.lineHeight != null && (!text.lineHeight.isFinite() || text.lineHeight <= 0)) ||
                    (text.maxLines != null && text.maxLines <= 0)
                ) {
                    v += Violation(
                        "V19",
                        "$key.text",
                        "fontFamily must be Roboto and lineHeight/maxLines must be positive when set",
                    )
                }
            }
            if (node is EllipseNode && node.radius !is Radius.Full) {
                v += Violation("V10", key, "an ellipse must have radius \"50%\"")
            }
            if (node.name.isBlank()) {
                v += Violation("V13", key, "name is blank")
            }
            if (!seenNames.add(node.name.trim().lowercase())) {
                v += Violation("V14", key, "duplicate name \"${node.name}\" in this scope")
            }
            if (!seenZ.add(node.z)) {
                v += Violation("V12", key, "duplicate z value ${node.z}")
            }
        }

        return if (v.isEmpty()) ValidationResult.Ok else ValidationResult.Invalid(v)
    }

    private fun Double.hasAtMostOneDecimalPlace(): Boolean =
        isFinite() && BigDecimal.valueOf(this).stripTrailingZeros().scale() <= 1
}
