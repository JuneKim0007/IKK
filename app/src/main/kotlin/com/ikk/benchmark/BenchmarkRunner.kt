package com.ikk.benchmark

/**
 * Executes one configuration: warmup to let ART reach steady state, then the
 * measured iterations.
 */
interface BenchmarkRunner {
    fun run(config: BenchmarkConfig): BenchmarkResult
}
