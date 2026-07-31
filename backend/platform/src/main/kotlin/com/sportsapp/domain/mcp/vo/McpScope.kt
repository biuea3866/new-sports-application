package com.sportsapp.domain.mcp.vo

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

    // toPermissionName()의 역변환 — verify 응답 등 외부에 scope를 다시 문자열로 노출할 때 쓴다.
    fun asString(): String = listOfNotNull(verb, domain, qualifier).joinToString(":")

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

        /**
         * permission name(`mcp.{domain}.{verb}.{qualifier}`) → [McpScope] 역변환.
         *
         * 기존 `McpTokenAuthenticationFilter.parseScopeFromPermissionName`(bootstrap, 미수정)과
         * 동일한 규칙이다 — 이 메서드가 platform 쪽 정본이며, 필터의 사본은 W1-06b가 필터를
         * 옮길 때 이 메서드 호출로 대체될 예정이다.
         */
        fun fromPermissionName(permissionName: String): McpScope? {
            val parts = permissionName.split(".")
            if (parts.size < 4 || parts[0] != "mcp") return null
            return McpScope(
                verb = parts[2],
                domain = parts[1],
                qualifier = parts[3].takeIf { it != "own" },
            )
        }
    }
}
