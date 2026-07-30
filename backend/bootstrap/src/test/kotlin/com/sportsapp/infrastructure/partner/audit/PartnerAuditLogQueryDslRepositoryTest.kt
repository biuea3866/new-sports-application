package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.partner.audit.PartnerAuditLog
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class PartnerAuditLogQueryDslRepositoryTest(
    @Autowired private val partnerAuditLogRepositoryImpl: PartnerAuditLogRepositoryImpl,
    @Autowired private val partnerAuditLogQueryDslRepository: PartnerAuditLogQueryDslRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM partner_audit_log")
        }

        fun saveAuditLog(partnerId: Long, calledAt: ZonedDateTime): PartnerAuditLog =
            partnerAuditLogRepositoryImpl.save(
                PartnerAuditLog.of(
                    partnerId = partnerId,
                    userId = 10L,
                    httpMethod = "GET",
                    requestPath = "/api/goods-seller/products",
                    targetResource = null,
                    statusCode = 200,
                    latencyMs = 10,
                    ipAddr = null,
                    clientUserAgent = null,
                    calledAt = calledAt,
                ),
            )

        Given("파트너 100의 감사 로그가 기간 안팎·타 파트너에 걸쳐 저장돼 있다") {
            val partnerId = 100L
            val now = ZonedDateTime.now()
            val inRangeOld = saveAuditLog(partnerId, now.minusDays(3))
            val inRangeNew = saveAuditLog(partnerId, now.minusDays(1))
            saveAuditLog(partnerId, now.minusDays(10))
            saveAuditLog(999L, now.minusDays(1))

            When("findBy로 범위·페이징 조회하면") {
                val page = partnerAuditLogQueryDslRepository.findBy(
                    partnerId = partnerId,
                    from = now.minusDays(7),
                    to = now,
                    pageable = PageRequest.of(0, 10),
                )

                Then("범위 밖 로그와 타 파트너 로그는 제외되고 called_at DESC로 정렬된다") {
                    page.totalElements shouldBe 2L
                    page.content.map { it.id } shouldBe listOf(inRangeNew.id, inRangeOld.id)
                }
            }
        }

        Given("신규 파트너에게 저장된 감사 로그가 없다") {
            val partnerId = 12345L
            val now = ZonedDateTime.now()

            When("findBy를 호출하면") {
                val page = partnerAuditLogQueryDslRepository.findBy(
                    partnerId = partnerId,
                    from = now.minusDays(7),
                    to = now,
                    pageable = PageRequest.of(0, 10),
                )

                Then("빈 Page가 반환된다") {
                    page.totalElements shouldBe 0L
                    page.content.shouldBeEmpty()
                }
            }
        }
    }
}
