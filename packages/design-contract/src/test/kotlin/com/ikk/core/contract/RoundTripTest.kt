package com.ikk.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * docs/json_contract.md principle 4: parse(serialise(x)) == x, for every type.
 * This is the cheapest defence against the two surfaces drifting apart.
 */
class RoundTripTest {

    private val now: Instant = Instant.parse("2026-09-20T14:22:31Z")

    private fun roundTrip(c: Contract): Contract = ContractJson.decode(ContractJson.encode(c))

    private fun contractOf(vararg nodes: DesignNode) = Contract(
        checkpoint = "cp_005",
        screen = "Home",
        components = nodes.associateBy { it.key },
    )

    private val rect = RectNode(
        id = "n1", z = 0,
        rect = RelRect.of(0.0, 0.0, 100.0, 27.0),
        fill = Color.of("#65558F"),
        radius = Radius.dp(8.0),
        updatedAt = now,
    )

    private val ellipse = EllipseNode(
        id = "n2", z = 1,
        rect = RelRect.of(78.4, 26.7, 14.9, 8.4),
        fill = Color.of("#2E8B74"),
        text = TextPayload("3", 22.0, TextAlign.CENTER, Color.of("#FFFFFF"), TextWeight.SEMIBOLD),
        updatedAt = now,
    )

    private val text = TextNode(
        id = "n3", z = 2,
        rect = RelRect.of(7.5, 11.1, 69.3, 5.1),
        text = TextPayload("Good morning", 26.0, TextAlign.START, Color.of("#FFFFFF"), TextWeight.SEMIBOLD),
        updatedAt = now,
    )

    private val image = ImageNode(
        id = "n4", z = 3,
        rect = RelRect.of(6.4, 51.3, 87.2, 22.5),
        source = AssetRef("asset_12", "image/png"),
        contentScale = ContentScale.CROP,
        alt = "cover",
        radius = Radius.dp(14.0),
        updatedAt = now,
    )

    @Test fun `rect round trips`() = assertEquals(contractOf(rect), roundTrip(contractOf(rect)))
    @Test fun `ellipse round trips`() = assertEquals(contractOf(ellipse), roundTrip(contractOf(ellipse)))
    @Test fun `text round trips`() = assertEquals(contractOf(text), roundTrip(contractOf(text)))
    @Test fun `image round trips`() = assertEquals(contractOf(image), roundTrip(contractOf(image)))

    @Test
    fun `full contract round trips`() {
        val c = contractOf(rect, ellipse, text, image)
        assertEquals(c, roundTrip(c))
    }

    /** §9: null and "" are different states and must survive distinctly. */
    @Test
    fun `null text and empty text are distinguishable after a round trip`() {
        val noSlot = rect.copy(id = "n9", text = null)
        val emptySlot = rect.copy(
            id = "n8",
            text = TextPayload("", 14.0, TextAlign.START, Color.of("#000000"), TextWeight.NORMAL),
        )
        val back = roundTrip(contractOf(noSlot, emptySlot.copy(z = 5)))
        assertNull(back.components["rect_9"]!!.text)
        assertNotNull(back.components["rect_8"]!!.text)
        assertEquals("", back.components["rect_8"]!!.text!!.value)
    }

    /** §8: "50%" is the only legal radius string, and it is not CircleShape. */
    @Test
    fun `ellipse radius serialises as the string 50 percent`() {
        val json = ContractJson.encode(contractOf(ellipse))
        assertTrue("expected \"50%\" in $json", json.contains("\"radius\":\"50%\""))
    }

    /** §8: a whole-number radius emits without a decimal point. */
    @Test
    fun `whole radius emits as an integer`() {
        val json = ContractJson.encode(contractOf(rect))
        assertTrue("expected radius 8 in $json", json.contains("\"radius\":8"))
    }

    /** §6: colours are normalised to uppercase so identical designs checksum alike. */
    @Test
    fun `lowercase colour input is normalised`() {
        assertEquals("#65558F", Color.of("#65558f").hex)
    }

    /** §6: Compose takes 0xAARRGGBB, CSS takes #RRGGBBAA. The byte order differs. */
    @Test
    fun `compose literal moves alpha to the front`() {
        assertEquals("0xFF65558F", Color.of("#65558F").toComposeLiteral())
        assertEquals("0x8065558F", Color.of("#65558F80").toComposeLiteral())
    }

    /** §5: Compose consumes fractions, CSS consumes percentages. */
    @Test
    fun `rect exposes both percentage and fraction`() {
        val r = RelRect.of(7.5, 11.1, 85.1, 5.1)
        assertEquals(7.5, r.x, 0.001)
        assertEquals(0.075, r.xFraction, 0.0001)
    }

    /** §5: one decimal place, always. */
    @Test
    fun `rect rounds to one decimal place`() {
        assertEquals(7.5, RelRect.of(7.48291, 0.0, 1.0, 1.0).x, 0.0001)
    }
}
