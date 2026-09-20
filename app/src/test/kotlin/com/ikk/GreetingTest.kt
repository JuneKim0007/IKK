package com.ikk

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun `greets by name`() {
        assertEquals("Hello, World!", greeting("World"))
    }
}
