package com.ikk.backend.contract

import com.ikk.core.contract.ContractJson
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class CanonicalJsonTest {
    @Test
    fun `sorts keys formats rect decimals and minimizes other numbers`() {
        val raw = ContractJson.format.parseToJsonElement(
            """{"z":1.0,"rect":{"w":85,"x":7.50,"y":0,"h":5.10},"a":null}""",
        )
        assertEquals(
            """{"a":null,"rect":{"h":5.1,"w":85.0,"x":7.5,"y":0.0},"z":1}""",
            CanonicalJson.canonicalJson(raw),
        )
    }

    @Test
    fun `preserves UTF-8 escapes newline and does not escape slash`() {
        val raw = ContractJson.format.parseToJsonElement(
            """{"value":"Привет\nhttps://ikk.dev/a"}""",
        )
        val canonical = CanonicalJson.canonicalJson(raw)
        assertEquals("""{"value":"Привет\nhttps://ikk.dev/a"}""", canonical)
        assertFalse("\\/" in canonical)
    }

    @Test
    fun `checksum is sha256 of canonical UTF-8 bytes`() {
        val raw = ContractJson.format.parseToJsonElement("""{"b":2,"a":1}""")
        assertEquals(
            "sha256:43258cff783fe7036d8a43033f830adfc60ec037382473548ac742b888292777",
            CanonicalJson.checksum(raw),
        )
    }

    @Test
    fun `Jackson boundary produces byte-identical canonical JSON`() {
        val jackson = JsonMapper.shared().readTree("""{"b":2.0,"a":{"x":1}}""")
        val kotlinx = ContractJson.format.parseToJsonElement(jackson.toString())
        assertEquals(CanonicalJson.canonicalJson(kotlinx), CanonicalJson.canonicalJson(jackson))
        assertEquals(CanonicalJson.checksum(kotlinx), CanonicalJson.checksum(jackson))
    }
}
