package com.ikk.data

/**
 * Supplies text to the UI. Trivial today; this is the seam where a real
 * source (network, database, preferences) would go.
 */
interface GreetingRepository {
    fun greetingFor(name: String): String
}

class DefaultGreetingRepository : GreetingRepository {
    override fun greetingFor(name: String): String = "Hello, $name!"
}
