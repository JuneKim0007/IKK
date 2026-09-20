package com.ikk.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingRepositoryTest {

    @Test
    fun `delegates to core`() {
        assertEquals("Hello, World!", DefaultGreetingRepository().greetingFor("World"))
    }
}
