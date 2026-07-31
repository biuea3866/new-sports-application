package com.sportsapp.presentation.mcp.confirm

import java.security.MessageDigest

object McpParamsHasher {
    fun hash(vararg parts: Any?): String {
        val raw = parts.joinToString(":") { it?.toString() ?: "" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
