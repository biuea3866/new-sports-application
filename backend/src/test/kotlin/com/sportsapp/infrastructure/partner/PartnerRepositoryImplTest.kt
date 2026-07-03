package com.sportsapp.infrastructure.partner

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(PartnerAuditStubConfig::class)
class PartnerRepositoryImplTest(
    @Autowired private val partnerRepositoryImpl: PartnerRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM partner")
        }

        Given("신규 Partner를 save한다") {
            val partner = Partner.create(name = "스포츠몰", linkedUserId = 100L)
            val saved = partnerRepositoryImpl.save(partner)

            When("findById로 조회하면") {
                val found = partnerRepositoryImpl.findById(requireNotNull(saved.id))

                Then("save한 것과 동일한 Partner가 복원된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                    found.name shouldBe "스포츠몰"
                    found.status shouldBe PartnerStatus.ACTIVE
                    found.linkedUserId shouldBe 100L
                }
            }
        }

        Given("linkedUserId로 연동된 Partner가 저장돼 있다") {
            partnerRepositoryImpl.save(Partner.create(name = "굿즈파트너", linkedUserId = 200L))

            When("findByLinkedUserId로 조회하면") {
                val found = partnerRepositoryImpl.findByLinkedUserId(200L)

                Then("해당 Partner가 반환된다") {
                    found.shouldNotBeNull()
                    found.linkedUserId shouldBe 200L
                    found.name shouldBe "굿즈파트너"
                }
            }
        }

        Given("존재하지 않는 partnerId로 조회한다") {
            When("findById를 호출하면") {
                val found = partnerRepositoryImpl.findById(-1L)

                Then("null이 반환된다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("ACTIVE 상태 Partner가 저장돼 있다") {
            val saved = partnerRepositoryImpl.save(Partner.create(name = "정지대상", linkedUserId = 300L))

            When("suspend 후 save하면") {
                saved.suspend()
                partnerRepositoryImpl.save(saved)

                Then("findById 결과의 status가 SUSPENDED로 반영된다") {
                    val found = partnerRepositoryImpl.findById(requireNotNull(saved.id))
                    found.shouldNotBeNull()
                    found.status shouldBe PartnerStatus.SUSPENDED
                }
            }
        }
    }
}
