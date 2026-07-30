package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.partner.audit.PartnerAuditLog
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class PartnerAuditLogRepositoryImplTest(
    @Autowired private val partnerAuditLogRepositoryImpl: PartnerAuditLogRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM partner_audit_log")
        }

        Given("신규 PartnerAuditLog가 주어지면") {
            val auditLog = PartnerAuditLog.of(
                partnerId = 1L,
                userId = 10L,
                httpMethod = "POST",
                requestPath = "/api/goods-seller/products",
                targetResource = "product-100",
                statusCode = 201,
                latencyMs = 120,
                ipAddr = "127.0.0.1",
                clientUserAgent = "partner-client/1.0",
                calledAt = ZonedDateTime.now(),
            )

            When("save를 호출하면") {
                val saved = partnerAuditLogRepositoryImpl.save(auditLog)

                Then("id가 채번되고 partner_audit_log 테이블에 1건 적재된다") {
                    saved.id.shouldNotBeNull()
                    saved.partnerId shouldBe 1L
                    saved.httpMethod shouldBe "POST"
                    saved.requestPath shouldBe "/api/goods-seller/products"
                    saved.statusCode shouldBe 201

                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partner_audit_log WHERE id = ?",
                        Long::class.java,
                        saved.id,
                    )
                    count shouldBe 1L
                }
            }
        }
    }
}
