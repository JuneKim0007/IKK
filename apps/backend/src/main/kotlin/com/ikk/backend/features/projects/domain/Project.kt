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
    val createdAt: Instant,
    val updatedAt: Instant,
)
