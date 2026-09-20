package com.ikk.core.contract

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The shared corpus. Kotlin, TypeScript and Python all run these same files.
 * If this diverges from codegen/generate.test.mjs or the Python suite, the
 * three implementations have drifted — which is the whole reason it exists.
 */
class CorpusTest {

    private fun repoRoot(): File {
        var d = File(".").absoluteFile
        while (!File(d, "docs/fixtures").isDirectory && d.parentFile != null) d = d.parentFile
        return d
    }

    @Test
    fun `every valid fixture parses`() {
        val dir = File(repoRoot(), "docs/fixtures/valid")
        val files = dir.listFiles { f -> f.extension == "json" }?.sorted().orEmpty()
        assertTrue("no fixtures found in $dir", files.isNotEmpty())
        for (f in files) {
            runCatching { ContractJson.decode(f.readText()) }
                .onFailure { fail("${f.name} should parse but failed: ${it.message}") }
        }
    }

    @Test
    fun `every valid fixture validates clean`() {
        val dir = File(repoRoot(), "docs/fixtures/valid")
        for (f in dir.listFiles { x -> x.extension == "json" }.orEmpty()) {
            val c = ContractJson.decode(f.readText())
            val r = ContractValidator.validate(c)
            assertTrue("${f.name}: $r", r is ValidationResult.Ok)
        }
    }

    @Test
    fun `every invalid fixture is rejected`() {
        val dir = File(repoRoot(), "docs/fixtures/invalid")
        val files = dir.listFiles { f -> f.extension == "json" }?.sorted().orEmpty()
        assertTrue("no invalid fixtures found", files.isNotEmpty())
        for (f in files) {
            val parsed = runCatching { ContractJson.decode(f.readText()) }
            if (parsed.isFailure) continue // rejected at parse time — fine
            val r = ContractValidator.validate(parsed.getOrThrow())
            assertTrue("${f.name} should have been rejected", r is ValidationResult.Invalid)
        }
    }

    @Test
    fun `the codegen example is a valid contract`() {
        val f = File(repoRoot(), "codegen/examples/home.json")
        assertTrue("missing $f", f.exists())
        val c = ContractJson.decode(f.readText())
        assertTrue(ContractValidator.validate(c) is ValidationResult.Ok)
    }
}
