package com.ikk.backend

import com.ikk.backend.contract.BackendContract
import com.ikk.backend.features.health.ContractCanary
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthReadinessTest {

    /**
     * The canary is only a readiness check if it actually exercises every type.
     * Adding a node type to the spec without adding it here would leave the
     * gap that let triangle and line reach the backend unrecognised.
     */
    @Test
    fun `the canary carries one node of every type`() {
        for (type in ContractCanary.TYPES) {
            assertTrue(
                ContractCanary.JSON.contains("\"type\": \"$type\""),
                "canary is missing a $type node",
            )
        }
        assertEquals(ContractCanary.NODE_COUNT, ContractCanary.TYPES.size)
    }

    @Test
    fun `the canary decodes and validates clean`() {
        val contract = BackendContract.decode(ContractCanary.JSON)
        assertEquals(ContractCanary.NODE_COUNT, contract.components.size)
        assertTrue(BackendContract.validate(contract).isEmpty(), "canary must validate clean")
    }
}
