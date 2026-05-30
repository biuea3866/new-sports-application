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

/**
 * MCP 서버용 ObjectMapper 설정.
 *
 * Spring AI 1.1.6 은 `defaultCandidate=false` (Spring 6.2+) 로 mcpServerObjectMapper 를
 * unqualified 주입 후보에서 제외하려 하지만, 본 프로젝트는 Spring Framework 6.1.x 를
 * 사용하므로 해당 기능이 동작하지 않는다. 항상 활성화되는 @Primary 빈을 명시적으로
 * 등록하고 @ConditionalOnMissingBean 조건으로 Spring AI 의 자동 등록을 억제한다.
 *
 * kafkaObjectMapper 는 @Qualifier 로 명시 주입되므로 영향 없음.
 * ConfirmationTokenGatewayImpl, PopularProductsRedisRepository 등 unqualified 주입은
 * 이 @Primary 빈을 사용한다.
 */
@Configuration
class McpObjectMapperConfig {

    @Bean(name = ["mcpServerObjectMapper"])
    @Primary
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
