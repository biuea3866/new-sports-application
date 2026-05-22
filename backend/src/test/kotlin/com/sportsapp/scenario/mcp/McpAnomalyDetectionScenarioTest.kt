package com.sportsapp.scenario.mcp

import com.sportsapp.application.mcp.DetectMcpAnomalyUseCase
import com.sportsapp.domain.mcp.McpAnomalyDetectedEvent
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenRepository
import com.sportsapp.domain.mcp.McpTokenStatus
import com.sportsapp.infrastructure.messaging.KafkaDomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration
class AnomalyTestConfig {

    @Bean
    fun kafkaDomainEventPublisher(): KafkaDomainEventPublisher = mockk(relaxed = true)

    @Bean
    fun mcpAnomalyEventCaptor(): McpAnomalyEventCaptor = McpAnomalyEventCaptor()
}

class McpAnomalyEventCaptor {
    val capturedEvents: CopyOnWriteArrayList<McpAnomalyDetectedEvent> = CopyOnWriteArrayList()

    @EventListener
    fun onAnomaly(event: McpAnomalyDetectedEvent) {
        capturedEvents.add(event)
    }

    fun clear() = capturedEvents.clear()
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AnomalyTestConfig::class)
@ActiveProfiles("test-jpa")
@TestPropertySource(properties = [
    "spring.data.mongodb.auto-index-creation=false",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration," +
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
        "org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration," +
        "org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration," +
        "org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration," +
        "org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebMvcAutoConfiguration," +
        "org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration," +
        "org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration",
])
class McpAnomalyDetectionScenarioTest(
    @Autowired private val detectMcpAnomalyUseCase: DetectMcpAnomalyUseCase,
    @Autowired private val mcpTokenRepository: McpTokenRepository,
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val eventCaptor: McpAnomalyEventCaptor,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BehaviorSpec() {

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }
    }

    private fun saveToken(
        userId: Long,
        name: String,
        createdAt: ZonedDateTime,
    ): McpToken {
        val token = mcpTokenRepository.save(
            McpToken(
                userId = userId,
                name = name,
                initialTokenHash = "hash-${System.nanoTime()}",
                initialStatus = McpTokenStatus.ACTIVE,
                initialExpiresAt = null,
            )
        )
        jdbcTemplate.update(
            "UPDATE mcp_tokens SET created_at = ? WHERE id = ?",
            createdAt.withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")),
            token.id,
        )
        return token
    }

    private fun saveLog(
        tokenId: Long,
        calledAt: ZonedDateTime,
    ) {
        auditLogRepository.save(
            McpAuditLog(
                tokenId = tokenId,
                userId = 100L,
                toolName = "getBookings",
                paramsMasked = null,
                statusCode = 200,
                latencyMs = 100,
                clientUserAgent = null,
                ipAddr = null,
                asn = null,
                calledAt = calledAt,
            )
        )
    }

    init {
        afterEach {
            eventCaptor.clear()
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
            jdbcTemplate.update("DELETE FROM mcp_token_scopes WHERE 1=1")
            jdbcTemplate.update("DELETE FROM mcp_tokens WHERE user_id IN (1001, 1002, 1003)")
        }

        Given("[S-03] 7일치 정상 패턴 토큰은 이벤트가 발행되지 않는다") {
            val now = ZonedDateTime.now()
            val token = saveToken(
                userId = 1001L,
                name = "normal-token",
                createdAt = now.minusDays(30),
            )

            repeat(7) { dayOffset ->
                saveLog(tokenId = token.id, calledAt = now.minusDays(dayOffset.toLong() + 1).withHour(10))
                saveLog(tokenId = token.id, calledAt = now.minusDays(dayOffset.toLong() + 1).withHour(11))
            }
            saveLog(tokenId = token.id, calledAt = now.minusMinutes(30))
            saveLog(tokenId = token.id, calledAt = now.minusMinutes(45))

            When("detectMcpAnomalyUseCase.execute()를 호출하면 (현재 2, 베이스라인 평균 2.0 → 비율 1.0)") {
                detectMcpAnomalyUseCase.execute()

                Then("[S-03] McpAnomalyDetectedEvent가 발행되지 않는다") {
                    val tokenEvents = eventCaptor.capturedEvents.filter { it.tokenId == token.id }
                    tokenEvents.size shouldBe 0
                }
            }
        }

        Given("[S-04] 7일치 audit log 적재 후 현재 급증 토큰은 이벤트가 발행된다") {
            val now = ZonedDateTime.now()
            val token = saveToken(
                userId = 1002L,
                name = "spike-token",
                createdAt = now.minusDays(30),
            )

            repeat(7) { dayOffset ->
                saveLog(tokenId = token.id, calledAt = now.minusDays(dayOffset.toLong() + 1).withHour(10))
            }

            repeat(25) {
                saveLog(tokenId = token.id, calledAt = now.minusMinutes(it.toLong() + 1))
            }

            When("detectMcpAnomalyUseCase.execute()를 호출하면 (현재 25, 베이스라인 7건 집계 → 비율 ≥2)") {
                detectMcpAnomalyUseCase.execute()

                Then("[S-04] McpAnomalyDetectedEvent가 발행되고 tokenId가 일치한다") {
                    val tokenEvents = eventCaptor.capturedEvents.filter { it.tokenId == token.id }
                    tokenEvents.size shouldBe 1
                    tokenEvents[0].userId shouldBe 1002L
                    tokenEvents[0].currentHourCount shouldBe 25L
                }
            }
        }

        Given("[S-05] cold-start(14일 미만) 토큰은 급증해도 이벤트가 발행되지 않는다") {
            val now = ZonedDateTime.now()
            val token = saveToken(
                userId = 1003L,
                name = "cold-start-token",
                createdAt = now.minusDays(5),
            )

            repeat(25) {
                saveLog(tokenId = token.id, calledAt = now.minusMinutes(it.toLong() + 1))
            }

            When("detectMcpAnomalyUseCase.execute()를 호출하면 (cold-start 토큰, 호출 25건)") {
                detectMcpAnomalyUseCase.execute()

                Then("[S-05] cold-start 기간이므로 McpAnomalyDetectedEvent가 발행되지 않는다") {
                    val tokenEvents = eventCaptor.capturedEvents.filter { it.tokenId == token.id }
                    tokenEvents.size shouldBe 0
                }
            }
        }
    }
}
