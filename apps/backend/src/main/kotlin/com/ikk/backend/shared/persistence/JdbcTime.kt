package com.ikk.backend.shared.persistence

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** PostgreSQL JDBC supports OffsetDateTime for TIMESTAMP WITH TIME ZONE. */
fun Instant.toJdbcTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
