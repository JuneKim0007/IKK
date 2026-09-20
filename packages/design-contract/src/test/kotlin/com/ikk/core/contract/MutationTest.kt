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
        id = "n1", z = 0,
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
            TextPayload("Label", 14.0, TextAlign.START, Color.of("#000000"), TextWeight.NORMAL), t1
        )
        assertEquals(2, after.version)
        assertEquals("Label", after.text!!.value)
    }

    @Test
    fun `withText null removes the slot`() {
        val withText = node.withText(
            TextPayload("Label", 14.0, TextAlign.START, Color.of("#000000"), TextWeight.NORMAL), t1
        )
        assertEquals(null, withText.withText(null, t1).text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a text node cannot drop its text`() {
        TextNode(
            id = "n2", z = 1,
            rect = RelRect.of(0.0, 0.0, 10.0, 10.0),
            text = TextPayload("x", 12.0, TextAlign.START, Color.of("#000000"), TextWeight.NORMAL),
            updatedAt = t0,
        ).withText(null, t1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rect cannot claim radius 50 percent`() {
        node.copy(radius = Radius.Full)
    }

    @Test
    fun `paint order follows z not map order`() {
        val a = node.copy(id = "n1", z = 5)
        val b = node.copy(id = "n2", z = 1)
        val c = Contract(checkpoint = "cp_001", screen = "Home", components = mapOf(a.key to a, b.key to b))
        assertEquals(listOf("n2", "n1"), c.paintOrder().map { it.id })
    }

    @Test
    fun `hidden nodes stay in the contract but are not emitted`() {
        val hidden = node.copy(id = "n2", z = 1, visible = false)
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
    private val tp = TextPayload("x", 12.0, TextAlign.START, Color.of("#000000"), TextWeight.NORMAL)

    @Test fun `rect accepts text, fill and an editable radius`() {
        val n = RectNode(id = "n1", z = 0, rect = r, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(n.acceptsFill); assertTrue(n.radiusEditable)
    }

    @Test fun `ellipse radius is not editable`() {
        val n = EllipseNode(id = "n2", z = 1, rect = r, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(!n.radiusEditable)
    }

    @Test fun `image accepts neither text nor fill`() {
        val n = ImageNode(id = "n3", z = 2, rect = r, source = AssetRef("a", "image/png"), updatedAt = t)
        assertTrue(!n.acceptsText); assertTrue(!n.acceptsFill)
    }

    @Test fun `text node has no fill control`() {
        val n = TextNode(id = "n4", z = 3, rect = r, text = tp, updatedAt = t)
        assertTrue(n.acceptsText); assertTrue(!n.acceptsFill)
    }
}
