package com.ikk.provider

import com.ikk.crypto.CryptoOperation

/**
 * Supplies [CryptoOperation] instances bound to one backend.
 *
 * Availability is a runtime question: Strongbox in particular is absent on
 * many devices, so callers must check before building a benchmark matrix.
 */
interface CryptoProvider {

    val backend: ProviderBackend

    fun isAvailable(): Boolean

    // TODO: decide how operations are identified once the algorithm set is fixed.
    fun create(operationId: String): CryptoOperation
}
