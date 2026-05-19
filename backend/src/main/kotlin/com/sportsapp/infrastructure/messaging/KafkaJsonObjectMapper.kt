package com.sportsapp.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kafka 직렬화 전용 ObjectMapper 빈.
 * - 이름: `kafkaObjectMapper` — Kafka Producer/Consumer 가 `@Qualifier` 로 명시적으로 참조.
 * - **@Primary 사용 금지** — Spring MVC 의 기본 ObjectMapper 를 덮어쓰면 REST 응답 직렬화가 Kafka 설정으로 오염됨.
 */
@Configuration
class KafkaJsonObjectMapper {

    @Bean("kafkaObjectMapper")
    fun kafkaObjectMapper(): ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
