package com.ikk.core.contract

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
            if (node.opacity !in 0.0..1.0) {
                v += Violation("V4", key, "opacity ${node.opacity} out of 0.0..1.0")
            }
            if (node.rect.w <= 0 || node.rect.h <= 0) {
                v += Violation("V5", key, "rect must have positive size, got ${node.rect.w}x${node.rect.h}")
            }
            val r = node.radius
            if (r is Radius.Dp && r.dp < 0) {
                v += Violation("V6", key, "radius ${r.dp} is negative")
            }
            if (node is TextNode && node.text == null) {
                v += Violation("V7", key, "a text node must carry text")
            }
            if (node is ImageNode && node.source != null && node.source.ref.isBlank()) {
                v += Violation("V8", key, "an image source must have a non-blank ref")
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
}
