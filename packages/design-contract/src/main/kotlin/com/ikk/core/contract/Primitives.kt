package com.ikk.core.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.round

/** docs/json_contract.md §5 — geometry is always a percentage of the frame. */
@Serializable
data class RelRect(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val unit: String = "%",
) {
    init {
        require(unit == "%") { "only \"%\" is legal in schemaVersion 1, got \"$unit\"" }
    }

    fun translate(dx: Double, dy: Double): RelRect = of(x + dx, y + dy, w, h)

    fun withSize(nw: Double, nh: Double): RelRect = of(x, y, nw, nh)

    /** Fractions, which is what Compose consumes. CSS consumes the percentages. */
    val xFraction: Double get() = x / 100.0
    val yFraction: Double get() = y / 100.0
    val wFraction: Double get() = w / 100.0
    val hFraction: Double get() = h / 100.0

    companion object {
        /** The only constructor that should be used: it enforces 1-decimal precision. */
        fun of(x: Double, y: Double, w: Double, h: Double): RelRect =
            RelRect(round1(x), round1(y), round1(w), round1(h))

        internal fun round1(v: Double): Double = round(v * 10.0) / 10.0
    }
}

/** docs/json_contract.md §6 — uppercase #RRGGBB or #RRGGBBAA. */
@JvmInline
@Serializable(with = ColorSerializer::class)
value class Color private constructor(val hex: String) {

    /** Compose wants 0xAARRGGBB; CSS wants #RRGGBBAA. The byte order differs. */
    fun toComposeLiteral(): String =
        if (hex.length == 7) "0xFF${hex.substring(1)}"
        else "0x${hex.substring(7, 9)}${hex.substring(1, 7)}"

    override fun toString(): String = hex

    companion object {
        private val PATTERN = Regex("^#[0-9A-F]{6}([0-9A-F]{2})?$")

        fun of(raw: String): Color {
            val upper = raw.uppercase()
            if (!PATTERN.matches(upper)) {
                throw IllegalArgumentException("invalid colour \"$raw\": expected #RRGGBB or #RRGGBBAA")
            }
            return Color(upper)
        }

        fun orNull(raw: String?): Color? = raw?.let { of(it) }
    }
}

object ColorSerializer : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeString(value.hex)
    override fun deserialize(decoder: Decoder): Color = Color.of(decoder.decodeString())
}

/** docs/json_contract.md §7 — width is dp, alignment is INSIDE on both targets. */
@Serializable
data class Stroke(val color: Color, val width: Double) {
    init { require(width >= 0) { "stroke width must be >= 0, got $width" } }
}

/**
 * docs/json_contract.md §8 — a number of dp, or the literal string "50%".
 *
 * "50%" maps to RoundedCornerShape(percent = 50) in Compose, NOT CircleShape:
 * CircleShape on a non-square box produces a pill, not an ellipse.
 */
@Serializable(with = RadiusSerializer::class)
sealed interface Radius {
    @JvmInline value class Dp(val dp: Double) : Radius
    data object Full : Radius

    companion object {
        val ZERO: Radius = Dp(0.0)
        fun dp(v: Double): Radius = Dp(v)
    }
}

object RadiusSerializer : KSerializer<Radius> {
    override val descriptor = PrimitiveSerialDescriptor("Radius", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Radius) {
        val json = encoder as? JsonEncoder
            ?: throw SerializationException("Radius is JSON-only")
        when (value) {
            is Radius.Dp ->
                json.encodeJsonElement(
                    if (value.dp % 1.0 == 0.0) JsonPrimitive(value.dp.toInt())
                    else JsonPrimitive(value.dp)
                )
            Radius.Full -> json.encodeJsonElement(JsonPrimitive("50%"))
        }
    }

    override fun deserialize(decoder: Decoder): Radius {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("Radius is JSON-only")
        val primitive = json.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("radius must be a number or \"50%\"")

        return if (primitive.isString) {
            if (primitive.content == "50%") Radius.Full
            else throw SerializationException("the only legal radius string is \"50%\", got \"${primitive.content}\"")
        } else {
            Radius.Dp(
                primitive.content.toDoubleOrNull()
                    ?: throw SerializationException("radius \"${primitive.content}\" is not a number")
            )
        }
    }
}

/** docs/json_contract.md §9. `null` TextPayload means no text slot at all. */
@Serializable
data class TextPayload(
    val value: String,
    val size: Double,
    val align: TextAlign,
    val color: Color,
    val weight: Int,
    val lineHeight: Double? = null,
    val fontFamily: String = "Roboto",
    val maxLines: Int? = null,
) {
    init {
        require(size > 0) { "text size must be > 0, got $size" }
        require(weight in 100..900 && weight % 100 == 0) {
            "weight must be 100..900 in steps of 100, got $weight"
        }
    }

    /** Resolved line height. Generators emit this number, never "normal". */
    val resolvedLineHeight: Double get() = lineHeight ?: (size * LINE_HEIGHT_RATIO)

    companion object { const val LINE_HEIGHT_RATIO = 1.3 }
}

@Serializable
enum class TextAlign {
    @kotlinx.serialization.SerialName("start") START,
    @kotlinx.serialization.SerialName("center") CENTER,
    @kotlinx.serialization.SerialName("end") END,
}

@Serializable
enum class ContentScale {
    @kotlinx.serialization.SerialName("crop") CROP,
    @kotlinx.serialization.SerialName("fit") FIT,
    @kotlinx.serialization.SerialName("fill") FILL,
}

@Serializable
data class AssetRef(val ref: String, val mime: String)

/** Which diagonal of its bounding box a line runs along. */
@Serializable
enum class LineOrientation {
    @SerialName("topLeftToBottomRight") TOP_LEFT_TO_BOTTOM_RIGHT,
    @SerialName("bottomLeftToTopRight") BOTTOM_LEFT_TO_TOP_RIGHT,
}

@Serializable
data class LineSpec(val orientation: LineOrientation = LineOrientation.TOP_LEFT_TO_BOTTOM_RIGHT)
