package com.ikk.benchmark

import com.ikk.crypto.OperationCategory
import com.ikk.provider.ProviderBackend

/** One point in the benchmark matrix. */
data class BenchmarkConfig(
    val operationId: String,
    val category: OperationCategory,
    val backend: ProviderBackend,
    val payloadSizeBytes: Int,
    val warmupIterations: Int,
    val measuredIterations: Int,
)
