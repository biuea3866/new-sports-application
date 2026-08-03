package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import java.time.ZonedDateTime

/**
 * REST 응답의 null 필드 포함 계약.
 *
 * 배경 — MCP 전용 매퍼가 앱 전역 직렬화를 가로챈 사고:
 *   `McpObjectMapperConfig` 가 `mcpServerObjectMapper` 를 `@Primary` 로 등록하면서
 *   `serializationInclusion(NON_NULL)` 까지 앱 전체에 적용됐다. 그 결과 **모든 REST 응답에서
 *   null 필드의 키 자체가 사라져**, 클라이언트가 "null" 이 아니라 "필드 없음(undefined)" 을 받았다.
 *   실제 파손: 관리자 콘솔 피처플래그 감사로그 화면이 zod 검증 에러 원문을 노출하며 동작 불능
 *   (`path: ["content",0,"before"]`, `expected object, received undefined` — 최초 CREATED 로그의
 *   `before` 가 null 이라 응답에서 통째로 빠졌다).
 *
 * 계약: 클라이언트 스키마가 nullable 필드를 안정적으로 소비하려면 **null 은 키와 함께 명시적으로**
 * 내려가야 한다. MCP 서버용 매퍼의 NON_NULL 정책이 HTTP 응답 직렬화에 전이되면 안 된다.
 *
 * Spring 컨텍스트 전체를 띄우지 않는다 — 공유 worktree 의 Testcontainers 경합을 피하려고
 * [DatasourceHikariConfigTest] 선례와 동일하게 ApplicationContextRunner 를 쓴다.
 */
class HttpResponseJsonNullInclusionTest : BehaviorSpec({

    // 감사 로그 응답의 실제 형태를 축약한 것 — nullable 필드 하나(before)와 non-null 필드를 함께 갖는다.
    data class AuditLogLike(val changeType: String, val before: Map<String, String>?)

    // 직렬화할 property 가 없는 객체 — FAIL_ON_EMPTY_BEANS 정책 확인용.
    class EmptyBeanLike

    // 날짜 직렬화 형식 확인용.
    data class OccurredAtLike(val occurredAt: ZonedDateTime)

    fun contextRunner() = ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(JacksonAutoConfiguration::class.java)
        )
        .withUserConfiguration(McpObjectMapperConfig::class.java)

    Given("MCP 설정이 포함된 애플리케이션 컨텍스트") {

        When("HTTP 메시지 컨버터가 쓰는 ObjectMapper 로 nullable 필드를 직렬화하면") {
            Then("null 필드가 키와 함께 명시적으로 남는다") {
                contextRunner().run { context ->
                    val converter = MappingJackson2HttpMessageConverter(context.getBean(ObjectMapper::class.java))

                    val json = converter.objectMapper.writeValueAsString(
                        AuditLogLike(changeType = "CREATED", before = null)
                    )

                    json shouldContain "\"before\":null"
                }
            }
        }

        When("MCP 서버용 매퍼를 이름으로 조회하면") {
            Then("여전히 등록돼 있다 (Spring AI 자동 등록 억제 목적은 유지)") {
                contextRunner().run { context ->
                    context.getBean("mcpServerObjectMapper", ObjectMapper::class.java) shouldNotBe null
                }
            }

            Then("MCP 매퍼는 앱 전역(unqualified) 매퍼와 다른 인스턴스다") {
                contextRunner().run { context ->
                    val applicationMapper = context.getBean(ObjectMapper::class.java)
                    val mcpMapper = context.getBean("mcpServerObjectMapper", ObjectMapper::class.java)

                    (applicationMapper === mcpMapper) shouldBe false
                }
            }

            // MCP 매퍼의 NON_NULL 은 이 분리의 전제다 — 누가 지워도 통과하면 안 된다.
            Then("MCP 매퍼는 NON_NULL 을 유지한다 (null 필드 키를 생략한다)") {
                contextRunner().run { context ->
                    val mcpMapper = context.getBean("mcpServerObjectMapper", ObjectMapper::class.java)

                    val json = mcpMapper.writeValueAsString(
                        AuditLogLike(changeType = "CREATED", before = null)
                    )

                    json shouldNotContain "before"
                }
            }
        }

        // 전역 매퍼를 "명시적 설정" 에서 "Boot 기본값" 으로 옮겼으므로, 이전에 명시돼 있던 정책이
        // 조용히 뒤집히지 않는지 고정한다. 이 PR 의 목적은 null 키 유지 하나이고, 나머지 계약은
        // 그대로여야 한다.
        When("전역 매퍼의 나머지 직렬화·역직렬화 정책을 확인하면") {

            // 이전 전역 매퍼는 ACCEPT_EMPTY_STRING_AS_NULL_OBJECT 를 enable 했다.
            // 꺼지면 요청 본문의 "" 가 null 대신 400 이 된다.
            Then("빈 문자열을 객체 자리에서 null 로 받아들인다") {
                contextRunner().run { context ->
                    val applicationMapper = context.getBean(ObjectMapper::class.java)

                    val parsed = applicationMapper.readValue(
                        """{"changeType":"CREATED","before":""}""",
                        AuditLogLike::class.java
                    )

                    parsed.before shouldBe null
                }
            }

            // 이전 전역 매퍼는 FAIL_ON_EMPTY_BEANS 를 disable 했다.
            // 켜지면 직렬화 가능한 property 가 없는 객체가 응답에 섞일 때 {} 대신 500 이 된다.
            Then("직렬화할 property 가 없는 객체를 예외 없이 {} 로 쓴다") {
                contextRunner().run { context ->
                    val applicationMapper = context.getBean(ObjectMapper::class.java)

                    applicationMapper.writeValueAsString(EmptyBeanLike()) shouldBe "{}"
                }
            }

            // 날짜 포맷의 근거가 "명시적 disable" 에서 "Boot 기본값" 으로 옮겨갔다.
            // 프레임워크 기본값에 의존하게 됐다면 그 계약은 테스트가 잡아야 한다.
            Then("날짜를 타임스탬프가 아니라 ISO-8601 문자열로 쓴다") {
                contextRunner().run { context ->
                    val applicationMapper = context.getBean(ObjectMapper::class.java)

                    val json = applicationMapper.writeValueAsString(
                        OccurredAtLike(ZonedDateTime.parse("2026-08-03T09:30:00+09:00"))
                    )

                    json shouldContain "2026-08-03T09:30:00"
                }
            }

            // Boot 기본값이자 이전 전역 매퍼의 명시 설정 — 양쪽이 같은지 확인한다.
            Then("모르는 필드가 있어도 역직렬화에 실패하지 않는다") {
                contextRunner().run { context ->
                    val applicationMapper = context.getBean(ObjectMapper::class.java)

                    val parsed = applicationMapper.readValue(
                        """{"changeType":"CREATED","before":null,"unknownField":1}""",
                        AuditLogLike::class.java
                    )

                    parsed.changeType shouldBe "CREATED"
                }
            }
        }
    }
})
