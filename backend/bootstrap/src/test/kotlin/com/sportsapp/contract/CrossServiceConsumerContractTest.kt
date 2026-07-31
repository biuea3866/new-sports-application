package com.sportsapp.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.infrastructure.messaging.KafkaJsonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 소비자 측 계약 테스트 (W1-10 / 실행설계 §4-3, §4-1 소비 관계).
 *
 * **소비자가 "내가 기대하는 필드"를 선언하고**, 공급자의 실제 형태가 그것을 만족하는지 검증한다.
 * 공급자가 필드를 지우거나 타입을 바꾸면 여기서 먼저 깨지므로, §3-2 금지 ⑥("두 서비스를 동시에
 * 배포해야 동작하는 변경")이 머지 전에 잡힌다.
 *
 * 기대 선언을 JSON 이 아니라 코드로 두는 이유: 소비자가 어느 필드를 왜 쓰는지 주석과 함께 남겨야
 * 나중에 "이 필드 지워도 되나" 질문에 답할 수 있다. 판정 규칙은 [ContractCompatibility] 가 공유한다.
 *
 * 1단계에서 이 소비는 같은 프로세스 안의 직접 호출이다. 2단계에 HTTP 로 바뀌므로 **JSON 형태**를
 * 계약의 렌즈로 쓴다 — 그때 깨질 것을 지금 고정하는 것이 이 테스트의 목적이다.
 *
 * 커버 범위:
 *  - edge → platform 신원 검증 (C10) — W1-06b 의 로컬 어댑터가 2단계에 RestClient 로 교체되는 첫 계약
 *  - commerce·facility-booking·social·recruitment → payment 결제 생존 판정 (C1~C4) — 만료 스위퍼 공유 커널
 *  - platform ← payment·commerce·facility-booking Kafka 이벤트 (C5) — [KafkaEventContractTest] 담당
 *  - edge → commerce·facility-booking·social 읽기 fan-out (C6·C7) — 시그니처 수준은 기존
 *    `ProvidedInterfaceContractTest`(ADR-003) 가 담당한다. 여기서 중복 고정하지 않는다.
 */
class CrossServiceConsumerContractTest : DescribeSpec({

    val objectMapper: ObjectMapper = KafkaJsonObjectMapper().kafkaObjectMapper()
    val fixedAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    /** 소비자 기대 1건 — 어떤 소비자가, 어떤 공급자 타입에서, 어떤 필드를 무슨 이유로 쓰는가. */
    data class Expectation(
        val consumer: String,
        val provider: String,
        val sample: Any,
        val expectedFields: ContractFields,
    )

    val expectations = listOf(
        Expectation(
            consumer = "edge (McpTokenAuthenticationFilter)",
            provider = "platform VerifyMcpTokenResponse",
            sample = VerifyMcpTokenResponse(valid = true, tokenId = 1L, userId = 2L, scopes = listOf("read:facility")),
            // edge 는 valid 로 401 을 판정하고, tokenId 로 사용 기록을, userId·scopes 로 주체·권한을 만든다.
            expectedFields = mapOf(
                "valid" to ContractField(type = "BOOLEAN", nullable = false),
                "tokenId" to ContractField(type = "INTEGER", nullable = true),
                "userId" to ContractField(type = "INTEGER", nullable = true),
                "scopes" to ContractField(type = "ARRAY", nullable = false),
            ),
        ),
        Expectation(
            consumer = "edge (PartnerApiKeyAuthenticationFilter)",
            provider = "platform VerifyPartnerApiKeyResponse",
            sample = VerifyPartnerApiKeyResponse(
                valid = false,
                partnerId = null,
                linkedUserId = null,
                failureReason = PartnerApiKeyVerificationFailure.SUSPENDED,
            ),
            // failureReason 이 SUSPENDED 인지로 403 과 401 을 가른다 — 이 필드가 사라지면 정지된
            // 파트너가 401 을 받게 되어 외부 계약(403)이 조용히 바뀐다.
            expectedFields = mapOf(
                "valid" to ContractField(type = "BOOLEAN", nullable = false),
                "partnerId" to ContractField(type = "NULL", nullable = true),
                "linkedUserId" to ContractField(type = "NULL", nullable = true),
                "failureReason" to ContractField(type = "STRING", nullable = true),
            ),
        ),
        Expectation(
            consumer = "commerce·facility-booking·social 만료 스위퍼",
            provider = "payment OrderPaymentLiveness.Live",
            sample = OrderPaymentLiveness.Live(since = fixedAt, attemptSince = fixedAt.minusMinutes(5)),
            // 소비자는 since(느린 TTL 앵커)와 attemptSince(빠른 TTL 앵커) **양쪽**이 필요하다 —
            // attemptSince 가 사라지면 단조성이 깨져 "재결제를 시도할수록 보호가 줄어드는" 결함이 재발한다.
            expectedFields = mapOf(
                "since" to ContractField(type = "STRING", nullable = false),
                "attemptSince" to ContractField(type = "STRING", nullable = true),
            ),
        ),
        Expectation(
            consumer = "commerce·facility-booking·social 만료 스위퍼",
            provider = "payment OrderPaymentLiveness.Attempting",
            sample = OrderPaymentLiveness.Attempting(since = fixedAt),
            expectedFields = mapOf("since" to ContractField(type = "STRING", nullable = false)),
        ),
        Expectation(
            consumer = "payment 생존 판정 입력 (PaymentLivenessClassifier)",
            provider = "payment PaymentLivenessRow",
            sample = PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = fixedAt),
            // 분류기 입력 형태다. status 가 enum 문자열로 나가야 소비자·공급자 분리 후에도 값이 통한다.
            expectedFields = mapOf(
                "orderId" to ContractField(type = "INTEGER", nullable = false),
                "status" to ContractField(type = "STRING", nullable = false),
                "createdAt" to ContractField(type = "STRING", nullable = false),
            ),
        ),
    )

    describe("소비자가 기대하는 필드가 공급자 형태에 존재한다") {
        expectations.forEach { expectation ->
            it("${expectation.consumer} → ${expectation.provider}") {
                val providerShape = ContractShape.of(expectation.sample, objectMapper)

                // 소비자 기대를 baseline 으로 둔다 — 공급자에 필드가 더 있는 것은 문제가 아니고,
                // 기대한 필드가 없거나 형태가 바뀐 것만 위반이다. 그래서 공급자의 신규 필드가
                // 소비자를 깨뜨리지 않는다(추가는 안전).
                val violations = ContractCompatibility
                    .violationsOf(expectation.expectedFields, providerShape.filterKeys { it in expectation.expectedFields })
                    .map { "${expectation.consumer} 가 기대하는 ${it.field}: ${it.reason}" }

                violations.shouldBeEmpty()
            }
        }
    }

    describe("기대 선언 자체의 위생") {
        it("모든 경계에 소비자·공급자 이름이 적혀 있다 — 실패 시 누가 깨졌는지 즉시 알 수 있어야 한다") {
            expectations.filter { it.consumer.isBlank() || it.provider.isBlank() }.shouldBeEmpty()
        }

        it("기대 필드가 비어 있는 선언이 없다 — 빈 기대는 아무것도 검증하지 않는다") {
            expectations.filter { it.expectedFields.isEmpty() }.shouldBeEmpty()
        }
    }
})
