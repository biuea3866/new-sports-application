package com.sportsapp.application.facility.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

data class FacilityCriteria(
    val gu: String?,
    val type: String?,
    val page: Int,
    val size: Int,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100
    }

    fun toPageable(): Pageable {
        val cappedSize = minOf(size, MAX_PAGE_SIZE)
        return PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.ASC, "name"))
    }

    fun effectiveGu(): String? = gu?.takeIf { it.isNotBlank() }

    fun effectiveType(): String? = type?.takeIf { it.isNotBlank() }
}
