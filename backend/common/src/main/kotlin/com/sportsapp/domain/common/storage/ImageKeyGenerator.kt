package com.sportsapp.domain.common.storage

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ImageKeyGenerator {
    fun generate(domain: String, filename: String): String {
        val extension = filename.substringAfterLast(".", "")
        val uuid = UUID.randomUUID().toString()
        return if (extension.isNotEmpty()) "images/$domain/$uuid.$extension" else "images/$domain/$uuid"
    }
}
