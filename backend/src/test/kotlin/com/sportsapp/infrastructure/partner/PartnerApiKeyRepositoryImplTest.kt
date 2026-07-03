package com.sportsapp.infrastructure.partner

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.PartnerApiKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class PartnerApiKeyRepositoryImplTest(
    @Autowired private val partnerApiKeyRepositoryImpl: PartnerApiKeyRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM partner_api_key")
        }

        Given("신규 PartnerApiKey를 save한다") {
            val apiKey = PartnerApiKey.create(partnerId = 1L, keyHash = "hash-1")
            val saved = partnerApiKeyRepositoryImpl.save(apiKey)

            When("findById로 조회하면") {
                val found = partnerApiKeyRepositoryImpl.findById(requireNotNull(saved.id))

                Then("save한 것과 동일한 PartnerApiKey가 복원된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                    found.partnerId shouldBe 1L
                    found.keyHash shouldBe "hash-1"
                    found.status shouldBe ApiKeyStatus.ACTIVE
                }
            }
        }

        Given("한 파트너에 ACTIVE 키 1개와 REVOKED 키 1개가 있다") {
            val partnerId = 10L
            val revokedKey = partnerApiKeyRepositoryImpl.save(
                PartnerApiKey.create(partnerId = partnerId, keyHash = "hash-revoked"),
            )
            revokedKey.revoke()
            partnerApiKeyRepositoryImpl.save(revokedKey)
            val activeKey = partnerApiKeyRepositoryImpl.save(
                PartnerApiKey.create(partnerId = partnerId, keyHash = "hash-active"),
            )

            When("findActiveByPartnerId를 호출하면") {
                val found = partnerApiKeyRepositoryImpl.findActiveByPartnerId(partnerId)

                Then("REVOKED 키를 제외하고 ACTIVE 키만 반환된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe activeKey.id
                    found.status shouldBe ApiKeyStatus.ACTIVE
                }
            }
        }

        Given("파트너의 모든 키가 REVOKED 상태다") {
            val partnerId = 20L
            val key = partnerApiKeyRepositoryImpl.save(
                PartnerApiKey.create(partnerId = partnerId, keyHash = "hash-only-revoked"),
            )
            key.revoke()
            partnerApiKeyRepositoryImpl.save(key)

            When("findActiveByPartnerId를 호출하면") {
                val found = partnerApiKeyRepositoryImpl.findActiveByPartnerId(partnerId)

                Then("ACTIVE 키가 없으므로 null이 반환된다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("ACTIVE 상태 PartnerApiKey가 저장돼 있다") {
            val saved = partnerApiKeyRepositoryImpl.save(
                PartnerApiKey.create(partnerId = 30L, keyHash = "hash-revoke-target"),
            )

            When("revoke 후 save하면") {
                saved.revoke()
                partnerApiKeyRepositoryImpl.save(saved)

                Then("findById 결과의 status와 revokedAt이 반영된다") {
                    val found = partnerApiKeyRepositoryImpl.findById(requireNotNull(saved.id))
                    found.shouldNotBeNull()
                    found.status shouldBe ApiKeyStatus.REVOKED
                    found.revokedAt.shouldNotBeNull()
                }
            }
        }
    }
}
