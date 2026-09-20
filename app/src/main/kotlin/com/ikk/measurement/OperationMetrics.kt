package com.ikk.measurement

/**
 * On-device metrics for a single measured iteration.
 *
 * Energy is deliberately absent: it is derived off-device from power traces
 * collected in parallel with a run, not sampled by the app itself.
 */
data class OperationMetrics(
    val runtimeNanos: Long,
    val allocatedBytes: Long,
)
