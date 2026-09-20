package com.ikk.core.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.Instant

/**
 * docs/json_contract.md §4.
 *
 * Nodes are immutable. Every mutation goes through [touch], which is what
 * bumps `version` and stamps `updatedAt` — if a field could be changed without
 * that, sync would silently lose the edit.
 */
@Serializable
sealed class DesignNode {
    abstract val id: String
    abstract val name: String
    abstract val z: Int
    abstract val visible: Boolean
    abstract val opacity: Double
    abstract val rect: RelRect
    abstract val fill: Color?
    abstract val stroke: Stroke?
    abstract val radius: Radius
    abstract val text: TextPayload?
    abstract val version: Int
    @Contextual abstract val updatedAt: Instant

    /** The contract key: `{type}_{n}`, derived from the discriminator and id. */
    abstract val key: String

    // Capability predicates. The envelope is uniform — every node serialises
    // every field — so a `Fillable` marker interface would carry no information.
    // What the editor actually needs to ask is whether a control should be shown.
    abstract val acceptsText: Boolean
    abstract val acceptsFill: Boolean
    abstract val radiusEditable: Boolean

    /** Returns a copy with version bumped and updatedAt stamped. */
    abstract fun touch(now: Instant = Instant.now()): DesignNode

    /** Returns a copy at the given geometry, versioned. */
    abstract fun withRect(rect: RelRect, now: Instant = Instant.now()): DesignNode

    /** Returns a copy carrying the given text, versioned. `null` removes the slot. */
    abstract fun withText(text: TextPayload?, now: Instant = Instant.now()): DesignNode

    protected fun requireCommonInvariants() {
        require(opacity in 0.0..1.0) { "opacity must be in 0.0..1.0, got $opacity" }
        require(rect.w > 0 && rect.h > 0) { "rect must have positive size, got ${rect.w}x${rect.h}" }
        require(version >= 1) { "version must be >= 1, got $version" }
        require(name.isNotBlank()) { "name must not be blank (V13)" }
    }

    /** §4.1 — the key is derived from the designer-facing name, not from the id. */
    protected fun keyFor(type: String): String = "${type}_${slug(name)}"

    companion object {
        fun slug(raw: String): String {
            val parts = raw.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return "unnamed"
            return parts.first().lowercase() +
                parts.drop(1).joinToString("") { w ->
                    w.lowercase().replaceFirstChar { it.uppercase() }
                }
        }
    }
}

@Serializable
@SerialName("rect")
data class RectNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    override val fill: Color? = null,
    override val stroke: Stroke? = null,
    override val radius: Radius = Radius.ZERO,
    override val text: TextPayload? = null,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        require(radius !is Radius.Full) { "a rect cannot have radius \"50%\" — use an ellipse" }
    }

    override val key: String get() = keyFor("rect")
    override val acceptsText get() = true
    override val acceptsFill get() = true
    override val radiusEditable get() = true
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)
    override fun withText(text: TextPayload?, now: Instant) = copy(text = text).touch(now)
}

@Serializable
@SerialName("ellipse")
data class EllipseNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    override val fill: Color? = null,
    override val stroke: Stroke? = null,
    override val radius: Radius = Radius.Full,
    override val text: TextPayload? = null,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        // V10. Serialised rather than computed so every node carries the full
        // envelope — an implementation that omits a field breaks V11 for the next one.
        require(radius is Radius.Full) { "an ellipse must have radius \"50%\"" }
    }

    override val key: String get() = keyFor("ellipse")
    override val acceptsText get() = true
    override val acceptsFill get() = true
    override val radiusEditable get() = false
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)
    override fun withText(text: TextPayload?, now: Instant) = copy(text = text).touch(now)
}

@Serializable
@SerialName("triangle")
data class TriangleNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    override val fill: Color? = null,
    override val stroke: Stroke? = null,
    override val radius: Radius = Radius.ZERO,
    override val text: TextPayload? = null,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        // V23. CSS clip-path clips the border away while Compose's .border()
        // follows the path, so a stroked triangle would render differently on
        // the two surfaces. Outlined triangles need a drawn path on both.
        require(stroke == null) { "a triangle carries no stroke (V23)" }
        require(radius == Radius.ZERO) { "a triangle carries no radius (V23)" }
    }

    override val key: String get() = keyFor("triangle")
    override val acceptsText get() = true
    override val acceptsFill get() = true
    override val radiusEditable get() = false
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)
    override fun withText(text: TextPayload?, now: Instant) = copy(text = text).touch(now)
}

@Serializable
@SerialName("line")
data class LineNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    override val stroke: Stroke,
    val line: LineSpec = LineSpec(),
    override val fill: Color? = null,
    override val radius: Radius = Radius.ZERO,
    override val text: TextPayload? = null,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        // V24. A line is a stroke: without one there is nothing to draw, and a
        // fill would be painting a box the line only uses as a bounding box.
        require(fill == null) { "a line has no fill (V24)" }
        require(stroke.width > 0) { "a line needs a positive stroke width (V24)" }
    }

    override val key: String get() = keyFor("line")
    override val acceptsText get() = false
    override val acceptsFill get() = false
    override val radiusEditable get() = false
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)
    override fun withText(text: TextPayload?, now: Instant): DesignNode {
        require(text == null) { "a line cannot carry text" }
        return this
    }
}

@Serializable
@SerialName("text")
data class TextNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    override val fill: Color? = null,
    override val stroke: Stroke? = null,
    override val radius: Radius = Radius.ZERO,
    override val text: TextPayload,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        // A text node is a node whose only content is text. A filled box with a
        // label is a rect carrying text, not a text node.
        require(fill == null && stroke == null) { "a text node carries no fill or stroke" }
    }

    val payload: TextPayload get() = text

    override val key: String get() = keyFor("text")
    override val acceptsText get() = true
    override val acceptsFill get() = false
    override val radiusEditable get() = false
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)

    /** V7: a text node must keep its text. Removing it is a type change, not an edit. */
    override fun withText(text: TextPayload?, now: Instant): DesignNode {
        requireNotNull(text) { "a text node cannot drop its text; delete the node instead" }
        return copy(text = text).touch(now)
    }
}

@Serializable
@SerialName("image")
data class ImageNode(
    override val id: String,
    override val name: String,
    override val z: Int,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val rect: RelRect,
    val source: AssetRef? = null,
    val contentScale: ContentScale = ContentScale.CROP,
    val alt: String = "",
    override val fill: Color? = null,
    override val stroke: Stroke? = null,
    override val radius: Radius = Radius.ZERO,
    override val text: TextPayload? = null,
    override val version: Int = 1,
    @Contextual override val updatedAt: Instant,
) : DesignNode() {
    init {
        requireCommonInvariants()
        require(text == null) { "an image node cannot carry text" }
    }

    override val key: String get() = keyFor("image")
    override val acceptsText get() = false
    override val acceptsFill get() = false
    override val radiusEditable get() = true
    override fun touch(now: Instant) = copy(version = version + 1, updatedAt = now)
    override fun withRect(rect: RelRect, now: Instant) = copy(rect = rect).touch(now)

    override fun withText(text: TextPayload?, now: Instant): DesignNode {
        require(text == null) { "an image node cannot carry text" }
        return this
    }
}
