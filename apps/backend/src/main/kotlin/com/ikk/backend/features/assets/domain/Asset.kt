package com.ikk.backend.features.assets.domain

import java.time.Instant

data class Asset(
    val ref: String,
    val projectId: String,
    val originalName: String,
    val mime: String,
    val bytes: Long,
    val content: ByteArray,
    val createdAt: Instant,
)
