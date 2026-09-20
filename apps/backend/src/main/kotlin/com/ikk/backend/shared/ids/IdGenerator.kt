package com.ikk.backend.shared.ids

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class IdGenerator {
    fun next(prefix: String): String =
        prefix + UUID.randomUUID().toString().replace("-", "").take(12)
}
