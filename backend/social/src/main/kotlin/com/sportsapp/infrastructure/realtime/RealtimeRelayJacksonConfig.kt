package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * social 릴레이(Redis pub/sub) 직렬화 전용 ObjectMapper 빈 (W1-07).
 *
 * - 이름: `realtimeRelayObjectMapper` — [RealtimeRelayPublisher]/[RealtimeRelaySubscriber]가
 *   `@Qualifier`로 명시적으로 참조한다.
 * - `SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE`을 끈다 — 기본값(true)이면
 *   `ZonedDateTime` 직렬화 시 원래 zone(id/offset)을 ObjectMapper 기본 타임존(UTC)으로 정규화해
 *   버려, 발행 시각의 zone 표현이 왕복(round-trip) 후 달라진다(`BroadcastMessage.createdAt` 비교 시
 *   인스턴트는 같아도 zone이 달라 값 불일치).
 * - `SerializationFeature.WRITE_DATES_WITH_ZONE_ID`를 켠다 — 기본값(false)이면 zone **region
 *   id**(`Asia/Seoul`)를 버리고 offset(`+09:00`)만 남겨, `ZonedDateTime.equals()`가 offset은
 *   같아도 zone 타입이 달라(`ZoneRegion` vs `ZoneOffset`) 불일치로 판정한다.
 * - `ADJUST_DATES_TO_CONTEXT_TIME_ZONE`(역직렬화 측)도 꺼서 양쪽 경로 모두 원본 zone을 보존한다.
 *   `KafkaJsonObjectMapper`와 동일하게 전용 빈으로 분리해 Spring MVC 기본 ObjectMapper(REST 응답
 *   직렬화)에는 영향을 주지 않는다.
 * - **@Primary 사용 금지** — 위와 동일한 이유.
 */
@Configuration
class RealtimeRelayJacksonConfig {

    @Bean("realtimeRelayObjectMapper")
    fun realtimeRelayObjectMapper(): ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
            .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
}
