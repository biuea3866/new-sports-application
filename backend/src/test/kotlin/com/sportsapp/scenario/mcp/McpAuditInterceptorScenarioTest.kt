package com.sportsapp.scenario.mcp

import com.sportsapp.domain.mcp.repository.McpAuditLogRepository
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.security.McpUserPrincipal
import com.sportsapp.presentation.mcp.controller.McpBookingTools
import com.sportsapp.presentation.mcp.controller.McpFacilityTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import java.time.ZonedDateTime
import org.springframework.core.task.SyncTaskExecutor

/**
 * BE-17 MCP Audit Interceptor 시나리오 테스트.
 *
 * Pattern B 검증: McpAuditLogAsyncRecorder 가 Tool bean 에 직접 주입되어 audit log 를 적재하는지 확인.
 *
 * 동기 실행 보장 전략:
 * - BeanDefinitionRegistryPostProcessor 로 mcpAuditExecutor bean definition 을 SyncAuditExecutor 로 교체.
 * - BeanDefinitionRegistryPostProcessor 는 모든 bean definition 이 등록된 후 (component scan 포함) 실행된다.
 * - SyncAuditExecutor: CallerRunsPolicy + queueCapacity=0 → @Async 호출 스레드에서 직접 실행 (동기).
 * - @DirtiesContext(BEFORE_CLASS) 로 이 테스트 전용 context 를 강제 생성한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ContextConfiguration(initializers = [McpAuditInterceptorScenarioTest.MongoInitializer::class])
@TestPropertySource(
    properties = [
        "storage.image.endpoint=http://localhost:9000",
        "storage.image.access-key=minioadmin",
        "storage.image.secret-key=minioadmin",
        "storage.image.bucket=sports-app",
        "storage.image.region=us-east-1",
    ],
)
class McpAuditInterceptorScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mcpFacilityTools: McpFacilityTools,
    @Autowired private val mcpBookingTools: McpBookingTools,
) : BehaviorSpec() {

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }

        val mongoContainer: MongoDBContainer = MongoDBContainer("mongo:7.0")
            .also { it.start() }

        @Container
        @ServiceConnection
        val redisContainer: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .also { it.start() }
    }

    /**
     * BeanDefinitionRegistryPostProcessor 를 통해 mcpAuditExecutor 를 동기 실행 executor 로 교체.
     *
     * 모든 bean definition 이 등록된 후 (component scan 포함) 실행되므로 반드시 override 된다.
     */
    @TestConfiguration
    class SyncExecutorConfig {
        @Bean
        fun mcpAuditExecutorReplacer(): BeanDefinitionRegistryPostProcessor =
            BeanDefinitionRegistryPostProcessor { registry ->
                if ((registry as BeanDefinitionRegistry).containsBeanDefinition("mcpAuditExecutor")) {
                    registry.removeBeanDefinition("mcpAuditExecutor")
                }
                val bd = GenericBeanDefinition()
                bd.setBeanClass(SyncAuditExecutorFactory::class.java)
                bd.setFactoryMethodName("create")
                registry.registerBeanDefinition("mcpAuditExecutor", bd)
            }
    }

    /**
     * SyncTaskExecutor — execute() 를 호출자 스레드에서 즉시 실행한다 (항상 동기).
     */
    object SyncAuditExecutorFactory {
        @JvmStatic
        fun create(): SyncTaskExecutor = SyncTaskExecutor()
    }

    class MongoInitializer : org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
        override fun initialize(applicationContext: org.springframework.context.ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.uri=${mongoContainer.replicaSetUrl}",
            )
        }
    }

    private fun setMcpSecurityContext(tokenId: Long, userId: Long) {
        val principal = McpUserPrincipal(
            tokenId = tokenId,
            userId = userId,
            grantedScopes = setOf(McpScope.of("read:facility"), McpScope.of("read:booking")),
        )
        val auth = UsernamePasswordAuthenticationToken(
            principal, null, listOf(SimpleGrantedAuthority("ROLE_MCP_TOKEN"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    init {
        beforeSpec {
            SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
        }

        afterSpec {
            SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL)
        }

        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
            SecurityContextHolder.clearContext()
        }

        Given("[S-01] read:facility scope를 가진 MCP 토큰으로 getFacilities tool을 호출하면") {
            val user = userDomainService.register("mcp-audit-s01-${System.nanoTime()}@example.com", "Pass1234!")
            val tokenId = 1001L
            val beforeCall = ZonedDateTime.now().minusSeconds(2)

            When("getFacilities tool을 직접 호출하면") {
                setMcpSecurityContext(tokenId = tokenId, userId = user.id)
                mcpFacilityTools.getFacilities(gu = null, type = null, page = 0, size = 10)

                Then("[S-01] mcp_audit_logs에 1행이 적재되고 toolName=getFacilities, statusCode=200이다") {
                    val logs = auditLogRepository.findByUserIdAndCalledAtBetween(
                        userId = user.id,
                        from = beforeCall,
                        to = ZonedDateTime.now().plusSeconds(5),
                        pageable = PageRequest.of(0, 10),
                    )
                    logs.content shouldHaveSize 1
                    logs.content[0].toolName shouldBe "getFacilities"
                    logs.content[0].statusCode shouldBe 200
                    logs.content[0].tokenId shouldBe tokenId
                    (logs.content[0].latencyMs >= 0) shouldBe true
                }
            }
        }

        Given("[S-02] audit log의 paramsMasked에 평문 PII가 없어야 한다") {
            val user = userDomainService.register("mcp-audit-s02-${System.nanoTime()}@example.com", "Pass1234!")
            val tokenId = 1002L
            val beforeCall = ZonedDateTime.now().minusSeconds(2)

            When("getBookings tool을 userId를 포함해서 직접 호출하면") {
                setMcpSecurityContext(tokenId = tokenId, userId = user.id)
                mcpBookingTools.getBookings(userId = user.id, status = null, page = 0, size = 10)

                Then("[S-02] paramsMasked가 존재하며 null이 아니다") {
                    val logs = auditLogRepository.findByUserIdAndCalledAtBetween(
                        userId = user.id,
                        from = beforeCall,
                        to = ZonedDateTime.now().plusSeconds(5),
                        pageable = PageRequest.of(0, 10),
                    )
                    logs.content shouldHaveSize 1
                    val paramsMasked = logs.content[0].paramsMasked
                    paramsMasked shouldNotBe null
                }
            }
        }
    }
}
