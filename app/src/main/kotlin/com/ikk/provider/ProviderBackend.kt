package com.ikk.provider

/** The provider backends each operation is evaluated under. */
enum class ProviderBackend {
    /** Software provider (BoringSSL via Conscrypt). */
    CONSCRYPT,

    /** Hardware-backed keys via the Android Keystore. */
    KEYSTORE,

    /** TEE-backed keys via Strongbox. Not present on every device. */
    STRONGBOX,
}
