package com.sportsapp.domain.mcp

/**
 * MCP scope 문자열 값 객체.
 *
 * 형식: `{verb}:{domain}` 또는 `{verb}:{domain}:{qualifier}`
 * 예: `read:facility`, `write:booking:any`
 *
 * permission name 변환 규칙:
 * - qualifier 있음: `mcp.{domain}.{verb}.{qualifier}`
 * - qualifier 없음: `mcp.{domain}.{verb}.own` (own이 기본값)
 */
data class McpScope(
    val verb: String,
    val domain: String,
    val qualifier: String?,
) {
    fun toPermissionName(): String {
        val resolvedQualifier = qualifier ?: "own"
        return "mcp.$domain.$verb.$resolvedQualifier"
    }

    companion object {
        fun of(raw: String): McpScope {
            val parts = raw.split(":")
            require(parts.size >= 2) {
                "Invalid scope format '$raw'. Expected '{verb}:{domain}' or '{verb}:{domain}:{qualifier}'"
            }
            return McpScope(
                verb = parts[0],
                domain = parts[1],
                qualifier = if (parts.size >= 3) parts[2] else null,
            )
        }
    }
}
