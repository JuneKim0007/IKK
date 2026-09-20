package com.ikk.backend.contract

import com.ikk.core.contract.ContractJson
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class RawContractValidatorTest {
    @Test
    fun `V8 requires image contentScale but permits null source`() {
        val missingScale = validImage.replace("\"contentScale\":\"crop\",", "")
        assertTrue(BackendContract.validate(missingScale).any { it.rule == "V8" })

        assertFalse(BackendContract.validate(validImage).any { it.rule == "V8" })
    }

    @Test
    fun `V13 and V15 are reported independently`() {
        val blank = validRect.replace("\"name\":\"Header\"", "\"name\":\"   \"")
        assertTrue(BackendContract.validate(blank).any { it.rule == "V13" })

        val wrongKey = validRect.replace("\"rect_header\":", "\"rect_wrong\":")
        assertTrue(BackendContract.validate(wrongKey).any { it.rule == "V15" })
    }

    @Test
    fun `sync payload key uses the core slug implementation`() {
        val payload = JsonMapper.shared().readTree("""{"type":"rect","name":"CTA button 2"}""")
        assertEquals("rect_ctaButton2", BackendContract.derivedKey(payload))
    }

    @Test
    fun `structural decode failures never validate cleanly`() {
        val missingCheckpoint = validRect.replace("\"checkpoint\":\"cp_001\",", "")
        val invalidEnum = validImage.replace("\"contentScale\":\"crop\"", "\"contentScale\":\"zoom\"")

        assertTrue(BackendContract.validate(missingCheckpoint).any { it.rule == "V18" })
        assertTrue(BackendContract.validate(invalidEnum).any { it.rule == "V11" })
    }

    @Test
    fun `raw and typed validation agree for a valid contract`() {
        val raw = ContractJson.format.parseToJsonElement(validRect)
        val typed = BackendContract.decode(validRect)
        assertEquals(emptyList(), RawContractValidator.validate(raw))
        assertEquals(emptyList(), BackendContract.validate(typed))
    }

    private val validRect = """
        {
          "schemaVersion":1,
          "checkpoint":"cp_001",
          "screen":"Home",
          "reference":{"w":375,"h":667,"unit":"dp"},
          "layout":"relative",
          "components":{
            "rect_header":{
              "id":"n1","type":"rect","name":"Header","z":0,"visible":true,
              "opacity":1.0,"rect":{"x":0.0,"y":0.0,"w":10.0,"h":10.0,"unit":"%"},
              "fill":"#65558F","stroke":null,"radius":0,"text":null,"version":1,
              "updatedAt":"2026-09-20T14:22:31Z"
            }
          }
        }
    """.trimIndent()

    private val validImage = """
        {
          "schemaVersion":1,
          "checkpoint":"cp_001",
          "screen":"Home",
          "reference":{"w":375,"h":667,"unit":"dp"},
          "layout":"relative",
          "components":{
            "image_photo":{
              "id":"n1","type":"image","name":"Photo","z":0,"visible":true,
              "opacity":1.0,"rect":{"x":0.0,"y":0.0,"w":10.0,"h":10.0,"unit":"%"},
              "fill":null,"stroke":null,"radius":0,"text":null,"source":null,
              "contentScale":"crop","alt":"","version":1,
              "updatedAt":"2026-09-20T14:22:31Z"
            }
          }
        }
    """.trimIndent()
}
