package com.ikk.benchmark

import com.ikk.measurement.OperationMetrics

/**
 * Per-iteration samples for one [BenchmarkConfig]. Aggregation is left to the
 * off-device analysis, so raw samples are retained rather than summarised.
 */
data class BenchmarkResult(
    val config: BenchmarkConfig,
    val samples: List<OperationMetrics>,
)
