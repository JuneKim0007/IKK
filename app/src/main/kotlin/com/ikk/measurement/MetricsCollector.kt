package com.ikk.measurement

/** Measures one execution of [block]. */
interface MetricsCollector {
    fun collect(block: () -> Unit): OperationMetrics
}
