package com.sportsapp.scenario.mcp

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.infrastructure.security.McpUserPrincipal
import com.sportsapp.presentation.mcp.toolregistry.McpBookingTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityTools
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZonedDateTime

/**
 * BE-17 MCP Audit Interceptor 시나리오 테스트.
 *
 * Spring AI의 MethodToolCallback은 setAccessible(true) + Method.invoke로 CGLIB proxy를 우회하므로
 * @Around AOP가 동작하지 않습니다. 따라서 tool 메서드를 직접 호출하여 audit log 적재를 검증합니다.
 * (Pattern B: McpAuditLogAsyncRecorder를 tool bean에 직접 주입)
 *
 * - tool 호출 후 mcp_audit_logs 테이블에 행이 삽입되는지 검증
 * - paramsMasked가 존재하는지 검증 (PII 마스킹은 ToolParamsMaskerTest에서 단위 검증)
 */
class McpAuditInterceptorScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mcpFacilityTools: McpFacilityTools,
    @Autowired private val mcpBookingTools: McpBookingTools,
) : BaseIntegrationTest() {

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
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
            SecurityContextHolder.clearContext()
        }

        Given("[S-01] read:facility scope를 가진 MCP 토큰으로 getFacilities tool을 호출하면") {
            val user = userDomainService.register("mcp-audit-s01@example.com", "Pass1234!")
            val tokenId = 1001L
            setMcpSecurityContext(tokenId = tokenId, userId = user.id)
            val beforeCall = ZonedDateTime.now().minusSeconds(2)

            When("getFacilities tool을 직접 호출하면") {
                mcpFacilityTools.getFacilities(gu = null, type = null, page = 0, size = 10)
                Thread.sleep(500) // @Async 완료 대기

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
            val user = userDomainService.register("mcp-audit-s02@example.com", "Pass1234!")
            val tokenId = 1002L
            setMcpSecurityContext(tokenId = tokenId, userId = user.id)
            val beforeCall = ZonedDateTime.now().minusSeconds(2)

            When("getBookings tool을 userId를 포함해서 직접 호출하면") {
                mcpBookingTools.getBookings(userId = user.id, status = null, page = 0, size = 10)
                Thread.sleep(500)

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
