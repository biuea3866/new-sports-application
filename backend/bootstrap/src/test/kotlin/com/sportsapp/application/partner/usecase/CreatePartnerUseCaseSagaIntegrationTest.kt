package com.sportsapp.application.partner.usecase

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerCommand
import com.sportsapp.domain.common.security.UserPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * PH0-08 T1 SAGA — `execute()` 전체가 하나의 트랜잭션으로 묶이지 않는다는 본질을
 * 실 DB(Testcontainers MySQL)로 검증한다. tx2(Partner 생성)를 도메인 유효성 실패로
 * 강제한 뒤, tx1(연동 User 생성)이 이미 커밋되어 INACTIVE 로 잔존함을 확인한다.
 * 단일 @Transactional 이었다면 tx1도 함께 롤백되어 User row 자체가 존재하지 않았을 것이다.
 */
class CreatePartnerUseCaseSagaIntegrationTest(
    @Autowired private val createPartnerUseCase: CreatePartnerUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun authenticateAsAdmin(adminId: Long) {
        val principal = UserPrincipal(id = adminId, email = "saga-admin@example.com", roles = listOf("ADMIN"))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
    }

    private fun latestIntegrationAccountStatus(): String =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE email LIKE 'partner+%@integration.local' ORDER BY id DESC LIMIT 1",
                String::class.java,
            ),
        ) { "연동 계정이 tx1에서 생성되지 않았다" }

    init {
        afterEach { SecurityContextHolder.clearContext() }

        Given("tx2(Partner 생성)가 도메인 유효성 검증에 실패할 때") {
            authenticateAsAdmin(9001L)
            val invalidCommand = CreatePartnerCommand(name = "")

            When("execute를 호출하면") {
                Then("예외가 전파되지만 tx1에서 생성된 연동 User는 별도 트랜잭션으로 이미 커밋되어 INACTIVE로 남는다") {
                    shouldThrow<IllegalArgumentException> { createPartnerUseCase.execute(invalidCommand) }

                    latestIntegrationAccountStatus() shouldBe "INACTIVE"
                }
            }
        }
    }
}
