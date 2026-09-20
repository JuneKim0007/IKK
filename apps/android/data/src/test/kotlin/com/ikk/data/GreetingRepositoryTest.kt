package com.ikk.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingRepositoryTest {

    @Test
    fun `returns a greeting`() {
        assertEquals("Hello, World!", DefaultGreetingRepository().greetingFor("World"))
    }
}
