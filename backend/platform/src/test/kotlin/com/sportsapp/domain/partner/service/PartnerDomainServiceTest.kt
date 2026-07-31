package com.sportsapp.domain.partner.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.exception.PartnerApiKeyInactiveException
import com.sportsapp.domain.partner.exception.PartnerNotFoundException
import com.sportsapp.domain.partner.exception.PartnerSuspendedException
import com.sportsapp.domain.partner.gateway.ApiKeyGenerator
import com.sportsapp.domain.partner.repository.PartnerApiKeyRepository
import com.sportsapp.domain.partner.repository.PartnerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.annotation.Transactional

class PartnerDomainServiceTest : BehaviorSpec({

    val partnerRepository = mockk<PartnerRepository>()
    val partnerApiKeyRepository = mockk<PartnerApiKeyRepository>()
    val apiKeyGenerator = mockk<ApiKeyGenerator>()

    val domainService = PartnerDomainService(
        partnerRepository = partnerRepository,
        partnerApiKeyRepository = partnerApiKeyRepository,
        apiKeyGenerator = apiKeyGenerator,
    )

    fun activeApiKey(
        id: Long,
        partnerId: Long = 1L,
        keyHash: String = "hashed-key",
    ): PartnerApiKey = PartnerApiKey.reconstitute(
        id = id,
        partnerId = partnerId,
        keyHash = keyHash,
        status = ApiKeyStatus.ACTIVE,
        revokedAt = null,
        lastUsedAt = null,
    )

    fun activePartner(id: Long, linkedUserId: Long = 10L): Partner = Partner.reconstitute(
        id = id,
        name = "test-partner",
        status = PartnerStatus.ACTIVE,
        linkedUserId = linkedUserId,
    )

    fun suspendedPartner(id: Long, linkedUserId: Long = 10L): Partner = Partner.reconstitute(
        id = id,
        name = "test-partner",
        status = PartnerStatus.SUSPENDED,
        linkedUserId = linkedUserId,
    )

    // issueKey(2-step)의 placeholder save(id=null) → id=100L 확보 → 최종 save(id=100L)를 재현하는 공통 스텁.
    fun stubIssueKeyPersistence(assignedKeyId: Long = 100L) {
        every { apiKeyGenerator.generateRandomPart() } returns "random-part"
        every { apiKeyGenerator.hash(any()) } answers { "hash-of-${firstArg<String>()}" }
        every { partnerApiKeyRepository.save(match { it.id == null }) } answers {
            val input = firstArg<PartnerApiKey>()
            PartnerApiKey.reconstitute(
                id = assignedKeyId,
                partnerId = input.partnerId,
                keyHash = input.keyHash,
                status = input.status,
                revokedAt = input.revokedAt,
                lastUsedAt = input.lastUsedAt,
            )
        }
        every { partnerApiKeyRepository.save(match { it.id == assignedKeyId }) } answers { firstArg() }
    }

    Given("파트너 생성 요청이 주어지면") {
        stubIssueKeyPersistence(assignedKeyId = 100L)
        every { partnerRepository.save(match { it.id == null }) } answers {
            val input = firstArg<Partner>()
            Partner.reconstitute(id = 1L, name = input.name, status = input.status, linkedUserId = input.linkedUserId)
        }

        When("createPartner를 호출하면") {
            val (partner, issuedApiKey) = domainService.createPartner(name = "acme", linkedUserId = 10L)

            Then("Partner가 저장되고 ACTIVE 키 1개가 발급되며 평문 키가 반환된다") {
                partner.id shouldBe 1L
                partner.status shouldBe PartnerStatus.ACTIVE
                issuedApiKey.apiKey.status shouldBe ApiKeyStatus.ACTIVE
                issuedApiKey.plainKey shouldStartWith "partner_100_"
                verify(exactly = 2) { partnerApiKeyRepository.save(any()) }
            }
        }
    }

    Given("2-step 키 발급이 이루어지면") {
        stubIssueKeyPersistence(assignedKeyId = 100L)

        When("issueKey를 호출하면") {
            val issuedApiKey = domainService.issueKey(partnerId = 1L)

            Then("평문 키는 partner_<keyId>_ 형식으로 발급된다") {
                issuedApiKey.plainKey shouldStartWith "partner_100_"
                issuedApiKey.apiKey.id shouldBe 100L
                issuedApiKey.apiKey.status shouldBe ApiKeyStatus.ACTIVE
            }
        }
    }

    Given("파트너에게 이미 ACTIVE 키가 있는 상태에서 재발급을 요청하면") {
        val oldKey = activeApiKey(id = 50L, partnerId = 1L)
        every { partnerApiKeyRepository.findActiveByPartnerId(1L) } returns oldKey
        every { partnerApiKeyRepository.save(match { it.id == 50L }) } returns oldKey
        stubIssueKeyPersistence(assignedKeyId = 100L)

        When("reissueKey를 호출하면") {
            val issuedApiKey = domainService.reissueKey(partnerId = 1L)

            Then("기존 ACTIVE 키는 REVOKED로 전이되고 새 ACTIVE 키가 발급된다") {
                oldKey.status shouldBe ApiKeyStatus.REVOKED
                issuedApiKey.apiKey.status shouldBe ApiKeyStatus.ACTIVE
                issuedApiKey.apiKey.id shouldBe 100L
                verify(exactly = 1) { partnerApiKeyRepository.save(oldKey) }
            }
        }
    }

    Given("파트너에게 ACTIVE 키가 없는 상태에서 재발급을 요청하면") {
        every { partnerApiKeyRepository.findActiveByPartnerId(1L) } returns null
        stubIssueKeyPersistence(assignedKeyId = 200L)

        When("reissueKey를 호출하면") {
            val issuedApiKey = domainService.reissueKey(partnerId = 1L)

            Then("구 키 조회 없이 새 ACTIVE 키만 발급된다") {
                issuedApiKey.apiKey.id shouldBe 200L
                issuedApiKey.apiKey.status shouldBe ApiKeyStatus.ACTIVE
            }
        }
    }

    Given("자신 소유의 ACTIVE 키를 폐기 요청하면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 1L)
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { partnerApiKeyRepository.save(apiKey) } returns apiKey

        When("revokeKey를 호출하면") {
            domainService.revokeKey(partnerId = 1L, keyId = 50L)

            Then("키 상태가 REVOKED로 전이되고 저장된다") {
                apiKey.status shouldBe ApiKeyStatus.REVOKED
                verify(exactly = 1) { partnerApiKeyRepository.save(apiKey) }
            }
        }
    }

    Given("존재하지 않는 키 ID로 폐기를 요청하면") {
        every { partnerApiKeyRepository.findById(999L) } returns null

        When("revokeKey를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    domainService.revokeKey(partnerId = 1L, keyId = 999L)
                }
            }
        }
    }

    Given("타 파트너 소유의 키 ID로 폐기를 요청하면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 2L)
        every { partnerApiKeyRepository.findById(50L) } returns apiKey

        When("revokeKey를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    domainService.revokeKey(partnerId = 1L, keyId = 50L)
                }
            }
        }
    }

    Given("ACTIVE 파트너를 비활성화 요청하면") {
        val partner = activePartner(id = 1L)
        every { partnerRepository.findById(1L) } returns partner
        every { partnerRepository.save(partner) } returns partner

        When("changeStatus(SUSPENDED)를 호출하면") {
            domainService.changeStatus(partnerId = 1L, status = PartnerStatus.SUSPENDED)

            Then("Partner 상태가 SUSPENDED로 전이된다") {
                partner.status shouldBe PartnerStatus.SUSPENDED
                verify(exactly = 1) { partnerRepository.save(partner) }
            }
        }
    }

    Given("SUSPENDED 파트너를 활성화 요청하면") {
        val partner = suspendedPartner(id = 1L)
        every { partnerRepository.findById(1L) } returns partner
        every { partnerRepository.save(partner) } returns partner

        When("changeStatus(ACTIVE)를 호출하면") {
            domainService.changeStatus(partnerId = 1L, status = PartnerStatus.ACTIVE)

            Then("Partner 상태가 ACTIVE로 전이된다") {
                partner.status shouldBe PartnerStatus.ACTIVE
            }
        }
    }

    Given("존재하지 않는 파트너의 상태 변경을 요청하면") {
        every { partnerRepository.findById(999L) } returns null

        When("changeStatus를 호출하면") {
            Then("PartnerNotFoundException이 발생한다") {
                shouldThrow<PartnerNotFoundException> {
                    domainService.changeStatus(partnerId = 999L, status = PartnerStatus.SUSPENDED)
                }
            }
        }
    }

    Given("유효한 키와 ACTIVE 파트너로 인증을 요청하면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 1L, keyHash = "hashed-key")
        val partner = activePartner(id = 1L, linkedUserId = 77L)
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { apiKeyGenerator.matches("partner_50_random-part", "hashed-key") } returns true
        every { partnerRepository.findById(1L) } returns partner

        When("authenticate를 호출하면") {
            val result = domainService.authenticate(keyId = 50L, plainKey = "partner_50_random-part")

            Then("(partnerId, linkedUserId)가 반환된다") {
                result.partnerId shouldBe 1L
                result.linkedUserId shouldBe 77L
            }
        }
    }

    Given("REVOKED 키로 인증을 요청하면") {
        val apiKey = PartnerApiKey.reconstitute(
            id = 50L,
            partnerId = 1L,
            keyHash = "hashed-key",
            status = ApiKeyStatus.REVOKED,
            revokedAt = null,
            lastUsedAt = null,
        )
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { apiKeyGenerator.matches("partner_50_random-part", "hashed-key") } returns true

        When("authenticate를 호출하면") {
            Then("PartnerApiKeyInactiveException이 발생한다") {
                shouldThrow<PartnerApiKeyInactiveException> {
                    domainService.authenticate(keyId = 50L, plainKey = "partner_50_random-part")
                }
            }
        }
    }

    Given("SUSPENDED 파트너의 유효한 키로 인증을 요청하면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 1L, keyHash = "hashed-key")
        val partner = suspendedPartner(id = 1L)
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { apiKeyGenerator.matches("partner_50_random-part", "hashed-key") } returns true
        every { partnerRepository.findById(1L) } returns partner

        When("authenticate를 호출하면") {
            Then("PartnerSuspendedException이 발생한다") {
                shouldThrow<PartnerSuspendedException> {
                    domainService.authenticate(keyId = 50L, plainKey = "partner_50_random-part")
                }
            }
        }
    }

    Given("평문 키가 저장된 해시와 일치하지 않으면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 1L, keyHash = "hashed-key")
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { apiKeyGenerator.matches("wrong-plain-key", "hashed-key") } returns false

        When("authenticate를 호출하면") {
            Then("예외가 발생한다") {
                shouldThrow<UnauthorizedException> {
                    domainService.authenticate(keyId = 50L, plainKey = "wrong-plain-key")
                }
            }
        }
    }

    Given("존재하지 않는 키 ID로 인증을 요청하면") {
        every { partnerApiKeyRepository.findById(999L) } returns null

        When("authenticate를 호출하면") {
            Then("UnauthorizedException이 발생한다") {
                shouldThrow<UnauthorizedException> {
                    domainService.authenticate(keyId = 999L, plainKey = "partner_999_random-part")
                }
            }
        }
    }

    Given("존재하는 API Key에 사용 기록을 요청하면") {
        val apiKey = activeApiKey(id = 50L, partnerId = 1L)
        every { partnerApiKeyRepository.findById(50L) } returns apiKey
        every { partnerApiKeyRepository.save(apiKey) } returns apiKey

        When("recordKeyUsage를 호출하면") {
            domainService.recordKeyUsage(keyId = 50L)

            Then("lastUsedAt이 갱신되고 저장된다") {
                apiKey.lastUsedAt.shouldNotBeNull()
                verify(exactly = 1) { partnerApiKeyRepository.save(apiKey) }
            }
        }
    }

    Given("존재하지 않는 키 ID에 사용 기록을 요청하면") {
        every { partnerApiKeyRepository.findById(999L) } returns null

        When("recordKeyUsage를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    domainService.recordKeyUsage(keyId = 999L)
                }
            }
        }
    }

    Given("유효한 평문 키와 ACTIVE 파트너로 verifyApiKey를 호출하면") {
        val apiKey = activeApiKey(id = 60L, partnerId = 2L, keyHash = "hashed-key")
        val partner = activePartner(id = 2L, linkedUserId = 88L)
        every { partnerApiKeyRepository.findById(60L) } returns apiKey
        every { apiKeyGenerator.matches("partner_60_random-part", "hashed-key") } returns true
        every { partnerRepository.findById(2L) } returns partner

        When("verifyApiKey를 호출하면") {
            val result = domainService.verifyApiKey("partner_60_random-part")

            Then("파트너 식별자가 담긴 유효한 결과가 반환된다") {
                result.valid shouldBe true
                result.partnerId shouldBe 2L
                result.linkedUserId shouldBe 88L
                result.failureReason shouldBe null
            }
        }
    }

    Given("형식이 partner_ 로 시작하지 않는 문자열로 verifyApiKey를 호출하면") {
        When("verifyApiKey를 호출하면") {
            val result = domainService.verifyApiKey("not-a-partner-key")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
            }
        }
    }

    Given("존재하지 않는 keyId로 verifyApiKey를 호출하면") {
        every { partnerApiKeyRepository.findById(999L) } returns null

        When("verifyApiKey를 호출하면") {
            val result = domainService.verifyApiKey("partner_999_random-part")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
                result.failureReason shouldBe PartnerApiKeyVerificationFailure.INVALID
            }
        }
    }

    Given("REVOKED(폐기된) 키로 verifyApiKey를 호출하면") {
        val apiKey = PartnerApiKey.reconstitute(
            id = 60L,
            partnerId = 2L,
            keyHash = "hashed-key",
            status = ApiKeyStatus.REVOKED,
            revokedAt = null,
            lastUsedAt = null,
        )
        every { partnerApiKeyRepository.findById(60L) } returns apiKey
        every { apiKeyGenerator.matches("partner_60_random-part", "hashed-key") } returns true

        When("verifyApiKey를 호출하면") {
            val result = domainService.verifyApiKey("partner_60_random-part")

            Then("예외 없이 유효하지 않은 결과가 반환된다 (실패 경로)") {
                result.valid shouldBe false
                result.failureReason shouldBe PartnerApiKeyVerificationFailure.INVALID
            }
        }
    }

    Given("SUSPENDED 파트너의 유효한 키로 verifyApiKey를 호출하면") {
        val apiKey = activeApiKey(id = 60L, partnerId = 2L, keyHash = "hashed-key")
        val partner = suspendedPartner(id = 2L)
        every { partnerApiKeyRepository.findById(60L) } returns apiKey
        every { apiKeyGenerator.matches("partner_60_random-part", "hashed-key") } returns true
        every { partnerRepository.findById(2L) } returns partner

        When("verifyApiKey를 호출하면") {
            val result = domainService.verifyApiKey("partner_60_random-part")

            Then("SUSPENDED 사유로 유효하지 않은 결과가 반환된다 (기존 403 구분 보존)") {
                result.valid shouldBe false
                result.failureReason shouldBe PartnerApiKeyVerificationFailure.SUSPENDED
            }
        }
    }

    Given("존재하지 않는 keyId와 폐기된 키가 각각 verifyApiKey에 주어지면") {
        every { partnerApiKeyRepository.findById(998L) } returns null
        val revokedKey = PartnerApiKey.reconstitute(
            id = 997L,
            partnerId = 2L,
            keyHash = "hashed-key",
            status = ApiKeyStatus.REVOKED,
            revokedAt = null,
            lastUsedAt = null,
        )
        every { partnerApiKeyRepository.findById(997L) } returns revokedKey
        every { apiKeyGenerator.matches("partner_997_random-part", "hashed-key") } returns true

        When("각각 verifyApiKey를 호출하면") {
            val notFoundResult = domainService.verifyApiKey("partner_998_random-part")
            val revokedResult = domainService.verifyApiKey("partner_997_random-part")

            Then("두 응답이 동일해 존재 여부를 구분할 수 없다 (보안)") {
                notFoundResult shouldBe revokedResult
            }
        }
    }

    // createPartner 는 partner 저장 + 키 placeholder 저장 + 실 해시 재저장으로 3회 쓴다.
    // 호출부(CreatePartnerUseCase)가 SAGA 구조라 @Transactional 을 갖지 않으므로, 이 경계가
    // 사라지면 3개 쓰기가 독립 auto-commit 되어 마지막 실패 시 아무도 쓸 수 없는 placeholder
    // 해시 ACTIVE 키가 영구 잔존한다. 실제로 한 번 회귀했던 지점이라 애노테이션 자체를 잠근다.
    // (호출부의 '없음'은 CreatePartnerUseCaseTest 가 이미 리플렉션으로 잠가둔 것과 대칭이다.)
    Given("createPartner 의 트랜잭션 경계를 검사할 때") {
        When("리플렉션으로 애노테이션을 조회하면") {
            Then("createPartner 메서드에 @Transactional 이 선언돼 있다") {
                val createPartnerMethod = PartnerDomainService::class.java.getDeclaredMethod(
                    "createPartner",
                    String::class.java,
                    Long::class.java,
                )
                createPartnerMethod.isAnnotationPresent(Transactional::class.java) shouldBe true
            }
        }
    }
})
