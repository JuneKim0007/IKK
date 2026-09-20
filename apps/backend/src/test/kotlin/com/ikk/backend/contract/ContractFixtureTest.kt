package com.ikk.backend.contract

import com.ikk.core.contract.ContractJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper

class ContractFixtureTest {
    private val root = repositoryRoot()

    @TestFactory
    fun `valid fixtures decode validate and round trip`(): List<DynamicTest> =
        fixtures("valid").map { path ->
            DynamicTest.dynamicTest(path.name) {
                val contract = BackendContract.decode(path.readText())
                assertEquals(emptyList(), BackendContract.validate(contract))
                assertEquals(contract, BackendContract.decode(BackendContract.encode(contract)))
                assertEquals(contract, BackendContract.decode(CanonicalJson.canonicalJson(contract)))
            }
        }

    @TestFactory
    fun `invalid fixtures report their named rule`(): List<DynamicTest> =
        fixtures("invalid").map { path ->
            DynamicTest.dynamicTest(path.name) {
                val expectedRule = path.name.substringBefore('_')
                val json = path.readText()
                val violations = BackendContract.validate(json)
                assertTrue(
                    violations.any { it.rule == expectedRule },
                    "$expectedRule missing from $violations",
                )
                val error = assertFailsWith<ContractValidationException> {
                    BackendContract.decode(json)
                }
                assertTrue(error.violations.any { it.rule == expectedRule })
            }
        }

    @Test
    fun `Jackson HTTP tree uses the same validator`() {
        val path = root.resolve("docs/fixtures/invalid/V11_unknown_field.json")
        val tree = JsonMapper.shared().readTree(path)
        assertTrue(BackendContract.validate(tree).any { it.rule == "V11" })
    }

    @Test
    fun `encoding delegates to the core codec`() {
        val json = root.resolve("docs/fixtures/valid/minimal.json").readText()
        val contract = BackendContract.decode(json)
        assertEquals(ContractJson.encode(contract), BackendContract.encode(contract))
    }

    @Test
    fun `codegen example passes the backend boundary`() {
        val path = root.resolve("packages/codegen/examples/home.json")
        val violations = BackendContract.validate(path.readText())
        assertEquals(emptyList(), violations)
        BackendContract.decode(path.readText())
    }

    private fun fixtures(kind: String): List<Path> =
        Files.list(root.resolve("docs/fixtures/$kind")).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.sorted().toList()
        }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isDirectory(it.resolve("docs/fixtures")) }
}
