package com.ikk.backend.features.codegen.domain

import java.time.Instant

data class Artifact(
    val projectId: String,
    val name: String,
    val target: String,
    val mime: String,
    val bytes: Long,
    val content: String,
    val createdAt: Instant,
)
