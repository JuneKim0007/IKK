package com.ikk.backend.features.checkpoints.domain

import java.time.Instant

data class Checkpoint(val checkpoint: String, val createdAt: Instant)
