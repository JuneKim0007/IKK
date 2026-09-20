package com.ikk.data

import com.ikk.core.contract.ContractJson
import com.ikk.core.contract.ContractValidator
import com.ikk.core.contract.ValidationResult
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Proves the `:packages:design-contract` dependency (added to this module's
 * build.gradle.kts) is real, not just declared: Android decodes and
 * validates the same cross-language corpus Kotlin's own CorpusTest,
 * codegen/generate.test.mjs and the backend's test_fixtures.py all run.
 *
 * If this ever regresses, `apps/android` has drifted from
 * `packages/design-contract` — see docs/architecture/repository-layout.md.
 */
class ContractWiringTest {

    private fun repoRoot(): File {
        var d = File(".").absoluteFile
        while (!File(d, "docs/fixtures").isDirectory && d.parentFile != null) d = d.parentFile
        return d
    }

    @Test
    fun `Android decodes and validates the shared fixture corpus`() {
        val dir = File(repoRoot(), "docs/fixtures/valid")
        val files: List<File> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray()).sorted()
        assertTrue("no fixtures found in $dir — is docs/fixtures/ present?", files.isNotEmpty())

        for (f in files) {
            val contract = runCatching { ContractJson.decode(f.readText()) }
                .getOrElse { fail("${f.name} should parse but failed: ${it.message}"); return }
            val result = ContractValidator.validate(contract)
            assertTrue("${f.name}: $result", result is ValidationResult.Ok)
        }
    }
}
