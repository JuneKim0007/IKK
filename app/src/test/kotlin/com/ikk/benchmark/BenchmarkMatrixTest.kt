package com.ikk.benchmark

import com.ikk.crypto.OperationCategory
import com.ikk.provider.ProviderBackend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the shape of the benchmark matrix. The study is defined over five
 * operation categories evaluated under three provider backends; if either set
 * changes, that is a scope change and this test should fail first.
 */
class BenchmarkMatrixTest {

    @Test
    fun `five operation categories are covered`() {
        assertEquals(5, OperationCategory.entries.size)
    }

    @Test
    fun `three provider backends are covered`() {
        assertEquals(3, ProviderBackend.entries.size)
    }
}
