package com.sportsapp.presentation.mcp.response

object McpResponseSize {

    const val DEFAULT_PAGE_SIZE: Int = 20
    const val MAX_PAGE_SIZE: Int = 100
    const val MAX_RESPONSE_BYTES: Int = 256 * 1024

    /**
     * 직렬화된 응답이 256KB를 초과하는 경우 LLM이 재호출에 사용할 축소된 page_size를 계산합니다.
     *
     * @return 축소된 page_size. 256KB 이하이면 null.
     */
    fun shouldReducePageSize(serialized: ByteArray, requestedPageSize: Int): Int? {
        if (serialized.size <= MAX_RESPONSE_BYTES) return null
        val reduced = (requestedPageSize.toLong() * MAX_RESPONSE_BYTES / serialized.size).toInt()
        return reduced.coerceAtLeast(1)
    }
}
