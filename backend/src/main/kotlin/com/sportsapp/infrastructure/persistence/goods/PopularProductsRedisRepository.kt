package com.sportsapp.infrastructure.persistence.goods

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.goods.PopularProductSnapshot
import com.sportsapp.domain.goods.PopularProductsCache
import com.sportsapp.domain.goods.ProductCategory
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class PopularProductsRedisRepository(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : PopularProductsCache {

    private val dtoListType = object : TypeReference<List<PopularProductCacheDto>>() {}

    override fun get(category: ProductCategory): List<PopularProductSnapshot>? {
        val json = stringRedisTemplate.opsForValue().get(cacheKey(category)) ?: return null
        return objectMapper.readValue(json, dtoListType).map { it.toSnapshot() }
    }

    override fun put(category: ProductCategory, snapshots: List<PopularProductSnapshot>) {
        val json = objectMapper.writeValueAsString(snapshots.map { PopularProductCacheDto.of(it) })
        stringRedisTemplate.opsForValue().set(cacheKey(category), json, CACHE_TTL)
    }

    override fun invalidate(category: ProductCategory) {
        stringRedisTemplate.unlink(cacheKey(category))
    }

    private fun cacheKey(category: ProductCategory): String = "popular:products:${category.name}"

    companion object {
        private val CACHE_TTL: Duration = Duration.ofSeconds(60)
    }
}
