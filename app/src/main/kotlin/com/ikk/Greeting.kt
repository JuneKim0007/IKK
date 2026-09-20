package com.ikk

/**
 * Pure function so it can be unit tested on the desktop JVM, with no emulator.
 */
fun greeting(name: String): String = "Hello, $name!"
