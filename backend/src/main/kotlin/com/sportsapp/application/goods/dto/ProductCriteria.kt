package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.exception.InvalidPriceRangeException
import com.sportsapp.domain.goods.vo.ProductCategory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.math.BigDecimal

data class ProductCriteria(
    val category: ProductCategory?,
    val keyword: String?,
    val priceMin: BigDecimal?,
    val priceMax: BigDecimal?,
    val sort: String,
    val page: Int,
    val size: Int,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
    }

    fun validatePriceRange() {
        val min = priceMin ?: return
        val max = priceMax ?: return
        if (min > max) throw InvalidPriceRangeException(min, max)
    }

    fun toPageable(): Pageable {
        val cappedSize = minOf(size, MAX_PAGE_SIZE)
        val sortOrder = if (sort == "price") {
            Sort.by(Sort.Direction.ASC, "price")
        } else {
            Sort.by(Sort.Direction.DESC, "createdAt")
        }
        return PageRequest.of(page, cappedSize, sortOrder)
    }
}
