package com.ikk.crypto

/**
 * A single cryptographic operation under measurement, bound to one algorithm
 * and one provider backend.
 */
interface CryptoOperation {

    /** Stable identifier used to key results, e.g. "aes-256-gcm". */
    val id: String

    val category: OperationCategory

    /** Runs the operation once. Called repeatedly by the benchmark runner. */
    fun execute(input: ByteArray): ByteArray
}
