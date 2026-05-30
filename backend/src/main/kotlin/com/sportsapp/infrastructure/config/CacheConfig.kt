package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): RedisCacheManager {
        val redisValueSerializer = GenericJackson2JsonRedisSerializer(buildRedisObjectMapper())
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer),
            )
            .disableCachingNullValues()

        val b2bDashboardConfig = defaultConfig
            .entryTtl(Duration.ofSeconds(60))
            .computePrefixWith { cacheName -> "b2b:$cacheName:" }

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("b2bDashboardSummary", b2bDashboardConfig)
            .build()
    }

    /**
     * Redis 캐시 값 직렬화용 ObjectMapper.
     * 기본 GenericJackson2JsonRedisSerializer()는 Kotlin 모듈이 없어 data class
     * (DashboardSummaryResponse 등)를 역직렬화하지 못한다 (no Creators 에러).
     * Kotlin/JavaTime 모듈 + 다형성 타이핑(@class)을 등록해 round-trip을 보장한다.
     */
    private fun buildRedisObjectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            // Kotlin data class는 final이라 NON_FINAL 타이핑은 @class를 쓰지 않는다.
            // 캐시 값 round-trip(쓰기/읽기 모두 @class 필요)을 위해 EVERYTHING으로 강제한다.
            activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY,
            )
        }
}
