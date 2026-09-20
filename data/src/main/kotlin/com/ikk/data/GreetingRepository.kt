package com.ikk.data

import com.ikk.core.greeting

/**
 * Supplies text to the UI. Trivial today; this is the seam where a real
 * source (network, database, preferences) would go.
 */
interface GreetingRepository {
    fun greetingFor(name: String): String
}

class DefaultGreetingRepository : GreetingRepository {
    override fun greetingFor(name: String): String = greeting(name)
}
