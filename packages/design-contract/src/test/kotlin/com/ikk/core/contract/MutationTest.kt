package com.ikk.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Every mutation must bump version and stamp updatedAt, or sync loses the edit. */
class MutationTest {

    private val t0 = Instant.parse("2026-09-20T14:22:31Z")
    private val t1 = Instant.parse("2026-09-20T14:25:02Z")

    private val node = RectNode(
        id = "n1", name = "Header", z = 0,
        rect = RelRect.of(0.0, 0.0, 50.0, 10.0),
        fill = Color.of("#65558F"),
        updatedAt = t0,
    )

    @Test
    fun `touch bumps version and stamps time`() {
        val after = node.touch(t1)
        assertEquals(2, after.version)
        assertEquals(t1, after.updatedAt)
    }

    @Test
    fun `withRect versions the node`() {
        val after = node.withRect(RelRect.of(10.0, 10.0, 50.0, 10.0), t1)
        assertEquals(2, after.version)
        assertEquals(10.0, after.rect.x, 0.001)
    }

    @Test
    fun `withText adds a slot and versions`() {
        val after = node.withText(
            TextPayload("Label", 14.0, TextAlign.START, Color.of("#000000"), 400), t1
        )
        assertEquals(2, after.version)
        assertEquals("Label", after.text!!.value)
    }

    @Test
    fun `withText null removes the slot`() {
        val withText = node.withText(
            TextPayload("Label", 14.0, TextAlign.START, Color.of("#000000"), 400), t1
        )
        assertEquals(null, withText.withText(null, t1).text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a text node cannot drop its text`() {
        TextNode(
            id = "n2", name = "Title", z = 1,
            rect = RelRect.of(0.0, 0.0, 10.0, 10.0),
            text = TextPayload("x", 12.0, TextAlign.START, Color.of("#000000"), 400),
            updatedAt = t0,
        ).withText(null, t1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rect cannot claim radius 50 percent`() {
        node.copy(radius = Radius.Full)
    }

    @Test
    fun `paint order follows z not map order`() {
        val a = node.copy(id = "n1", name = "Header", z = 5)
        val b = node.copy(id = "n2", name = "Title", z = 1)
        val c = Contract(checkpoint = "cp_001", screen = "Home", components = mapOf(a.key to a, b.key to b))
        assertEquals(listOf("n2", "n1"), c.paintOrder().map { it.id })
    }

    @Test
    fun `hidden nodes stay in the contract but are not emitted`() {
        val hidden = node.copy(id = "n2", name = "Title", z = 1, visible = false)
        val c = Contract(checkpoint = "cp_001", screen = "Home", components = mapOf(node.key to node, hidden.key to hidden))
        assertEquals(2, c.components.size)
        assertEquals(listOf("n1"), c.emittable().map { it.id })
    }

    @Test
    fun `unknown schema version is rejected loudly`() {
        val json = """{"schemaVersion":99,"checkpoint":"cp_001","screen":"Home",
            "reference":{"w":375,"h":667,"unit":"dp"},"layout":"relative","components":{}}"""
        val e = runCatching { ContractJson.decode(json) }.exceptionOrNull()
        assertTrue("expected UnsupportedSchemaVersion, got $e", e is SchemaMigration.UnsupportedSchemaVersion)
    }

    @Test
    fun `an unknown field is rejected, not ignored`() {
        val json = """{"schemaVersion":1,"checkpoint":"cp_001","screen":"Home","surprise":true,
            "reference":{"w":375,"h":667,"unit":"dp"},"layout":"relative","components":{}}"""
        assertTrue(runCatching { ContractJson.decode(json) }.isFailure)
    }
}

/** Capability predicates drive which inspector controls are shown. */
class CapabilityTest {
    private val t = Instant.parse("2026-09-20T14:22:31Z")
    private val r = RelRect.of(0.0, 0.0, 10.0, 10.0)
    private val tp = TextPayload("x", 12.0, TextAlign.START, Color.of("#000000"), 400)

    @Test fun `rect accepts text, fill and an editable radius`() {
        val n = RectNode(id = "n1", name = "Header", z = 0, rect = r, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(n.acceptsFill); assertTrue(n.radiusEditable)
    }

    @Test fun `ellipse radius is not editable`() {
        val n = EllipseNode(id = "n2", name = "Title", z = 1, rect = r, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(!n.radiusEditable)
    }

    @Test fun `image accepts neither text nor fill`() {
        val n = ImageNode(id = "n3", name = "Badge", z = 2, rect = r, source = AssetRef("a", "image/png"), updatedAt = t)
        assertTrue(!n.acceptsText); assertTrue(!n.acceptsFill)
    }

    @Test fun `text node has no fill control`() {
        val n = TextNode(id = "n4", name = "Photo", z = 3, rect = r, text = tp, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(!n.acceptsFill)
    }
}


/** docs/json_contract.md §4.1 — id, name and key are three different jobs. */
class IdentityTest {
    private val t = Instant.parse("2026-09-20T14:22:31Z")
    private fun rect(id: String, name: String, z: Int = 0) = RectNode(
        id = id, name = name, z = z,
        rect = RelRect.of(0.0, 0.0, 10.0, 10.0), updatedAt = t,
    )

    @Test fun `key derives from the name, not the id`() {
        assertEquals("rect_signIn", rect("0b4c1e6a-6f9d", "Sign in").key)
    }

    @Test fun `slug strips punctuation and camel-cases`() {
        assertEquals("rect_ctaButton2", rect("x", "CTA button 2").key)
    }

    @Test fun `renaming does not change the id`() {
        val a = rect("uuid-1", "Card")
        val b = a.copy(name = "Hero card").touch(t)
        assertEquals(a.id, b.id)
        assertEquals("rect_heroCard", b.key)
    }

    @Test fun `V14 rejects a duplicate name in the same scope`() {
        val a = rect("uuid-1", "Action", z = 0)
        val b = rect("uuid-2", "Action", z = 1)
        val c = Contract(checkpoint = "cp_001", screen = "Home",
            components = mapOf("rect_action" to a, "rect_action2" to b))
        val r = ContractValidator.validate(c)
        assertTrue(r is ValidationResult.Invalid &&
            r.violations.any { it.rule == "V14" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V13 rejects a blank name`() { rect("uuid-1", "   ") }
}

/** docs/json_contract.md §3.1 — the screen surface. */
class BackgroundTest {
    private val t = Instant.parse("2026-09-20T14:22:31Z")
    private fun contract(bg: Background?) = Contract(
        checkpoint = "cp_001", screen = "Home", background = bg,
        components = emptyMap(),
    )

    private fun roundTrip(c: Contract) = ContractJson.decode(ContractJson.encode(c))

    @Test fun `a solid background round trips`() {
        val c = contract(Background(Fill.Solid(Color.of("#141021"))))
        assertEquals(c, roundTrip(c))
    }

    @Test fun `a solid background serialises as a bare colour string`() {
        val json = ContractJson.encode(contract(Background(Fill.Solid(Color.of("#141021")))))
        assertTrue(json, json.contains("\"fill\":\"#141021\""))
    }

    @Test fun `a gradient background round trips`() {
        val c = contract(Background(Fill.LinearGradient(135.0, listOf(
            GradientStop(Color.of("#F49AA8"), 0.0),
            GradientStop(Color.of("#C86DD7"), 100.0),
        ))))
        assertEquals(c, roundTrip(c))
    }

    @Test fun `an absent background is absent, not white`() {
        val back = roundTrip(contract(null))
        assertEquals(null, back.background)
    }

    /** V25 — one stop is a solid colour wearing a gradient's clothes. */
    @Test(expected = IllegalArgumentException::class)
    fun `a gradient needs at least two stops`() {
        Fill.LinearGradient(90.0, listOf(GradientStop(Color.of("#000000"), 0.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gradient stops must not decrease`() {
        Fill.LinearGradient(90.0, listOf(
            GradientStop(Color.of("#000000"), 80.0),
            GradientStop(Color.of("#FFFFFF"), 20.0),
        ))
    }
}
