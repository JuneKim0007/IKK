package com.ikk.export

import com.ikk.benchmark.BenchmarkResult

/** Writes results to device storage for off-device analysis to collect. */
interface ResultExporter {
    // TODO: fix the output format once the analysis side is settled.
    fun export(results: List<BenchmarkResult>)
}
