package com.ikk.backend.features.projects.domain

import java.time.Instant

data class Project(
    val id: String,
    val name: String,
    val screen: String,
    val schemaVersion: Int,
    val currentCheckpoint: String,
    val referenceWidth: Int,
    val referenceHeight: Int,
    val referenceUnit: String,
    val layout: String,
    /** Raw §3.1 background JSON, or null when the contract carried none. */
    val background: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
