package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * ObjectMapper 빈 구성 — 앱 전역 매퍼와 MCP 서버 전용 매퍼를 분리한다.
 *
 * ## 왜 분리하는가 (2026-08-01)
 *
 * 이전에는 MCP 전용 매퍼 하나가 `@Primary` 로 등록돼 **앱 전역 HTTP 직렬화까지 담당**했다.
 * 이 레포에 사용자 정의 `ObjectMapper` 빈이 존재하면 Spring Boot 의 Jackson 자동설정이
 * 물러나므로(`JacksonAutoConfiguration` 의 `@ConditionalOnMissingBean`),
 * `MappingJackson2HttpMessageConverter` 가 그 MCP 매퍼를 그대로 집어 갔다.
 *
 * 그 매퍼는 `JsonInclude.Include.NON_NULL` 이라 **모든 REST 응답에서 null 필드의 키가
 * 통째로 사라졌다.** 클라이언트는 "null" 이 아니라 "필드 없음(undefined)" 을 받았고,
 * nullable 필드를 선언한 스키마가 전부 잠재 파손 상태였다 (web 기준 `.nullable()` 22곳).
 *
 * 실제 파손 사례 — 관리자 콘솔 피처플래그 감사로그 화면이 동작 불능:
 * 최초 `CREATED` 로그의 `before` 가 null 이라 응답에서 생략됐고, zod 가
 * `path: ["content",0,"before"] / expected object, received undefined` 로 거부해
 * 화면에 검증 에러 원문과 `다시 시도` 버튼만 남았다.
 *
 * ## 구성
 *
 * - **앱 전역(`@Primary`)**: Spring Boot 가 자동설정한 [Jackson2ObjectMapperBuilder] 로 만든다.
 *   Boot 기본 동작(모듈 자동 등록, `spring.jackson.*` 프로퍼티, 날짜 ISO 직렬화,
 *   `FAIL_ON_UNKNOWN_PROPERTIES` 비활성)을 그대로 따르고 **null 은 키와 함께 남긴다.**
 *   응답 계약이 값의 유무에 따라 모양이 바뀌지 않아야 클라이언트 스키마가 안정된다.
 * - **MCP 전용(`mcpServerObjectMapper`)**: NON_NULL 등 MCP 서버 취향의 설정을 유지하되
 *   `@Primary` 를 떼어 HTTP 응답 직렬화에 전이되지 않게 한다. 빈 이름을 유지하는 이유는
 *   Spring AI 1.1.6 의 자동 등록을 `@ConditionalOnMissingBean` 조건으로 억제하기 위해서다
 *   (Spring AI 는 `defaultCandidate=false` 로 unqualified 주입에서 빠지려 하지만 그 기능은
 *   Spring Framework 6.2+ 이고 본 프로젝트는 6.1.x 라 동작하지 않는다).
 *
 * `kafkaObjectMapper` 는 `@Qualifier` 로 명시 주입되므로 영향 없다.
 * `ConfirmationTokenGatewayImpl`·`PopularProductsRedisRepository` 등 unqualified 주입은
 * 이제 Boot 기본 구성의 앱 전역 매퍼를 받는다.
 */
@Configuration
class McpObjectMapperConfig {

    /**
     * 앱 전역 ObjectMapper — HTTP 요청/응답 직렬화가 이 빈을 쓴다.
     * Boot 가 구성한 빌더를 그대로 써서 자동설정과 동일한 동작을 유지한다(null 포함).
     */
    @Bean
    @Primary
    fun applicationObjectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper = builder.build()

    /**
     * MCP 서버 전용 ObjectMapper. `@Primary` 를 붙이지 않는다 — 붙이면 NON_NULL 정책이
     * 전 REST 응답으로 전이된다(위 KDoc 의 사고 원인).
     */
    @Bean(name = ["mcpServerObjectMapper"])
    fun mcpServerObjectMapper(): ObjectMapper =
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
}
