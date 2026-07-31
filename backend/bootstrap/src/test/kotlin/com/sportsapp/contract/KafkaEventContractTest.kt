package com.sportsapp.contract

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.ticketing.event.TicketEvent
import com.sportsapp.infrastructure.messaging.KafkaJsonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Kafka 이벤트 payload 계약을 커밋된 스냅샷으로 고정한다 (W1-10 / 실행설계 §4-3·§9-2).
 *
 * **이 테스트가 존재하는 이유는 사고 이력이다.** 이 레포에서는 payload 역직렬화 정합이 깨져
 * 결제 완료 → 주문 확정이 **장기간 유실**됐다(돈은 받고 서비스 미제공). 계약 테스트가 있었으면
 * 잡혔을 사고이며, 장기 잠복이라 롤백으로도 되돌릴 수 없었다.
 *
 * 검증은 세 겹이다:
 *  1. **와이어 스냅샷 대조** — 프로덕션 Kafka ObjectMapper 로 직렬화한 결과가 커밋된 계약과 같은지.
 *     스냅샷을 갱신하는 것이 곧 "계약을 바꿨다"는 명시적 선언이 된다.
 *  2. **하위 호환 판정** — 필드 제거·타입 변경·필수화는 실패, optional 추가만 통과 ([ContractCompatibility]).
 *  3. **역직렬화 왕복** — 직렬화한 payload 를 sealed 베이스 타입으로 되읽어 변이가 보존되는지.
 *     사고의 실패면이 정확히 여기였다. `@JsonSubTypes` 등록 누락도 이 단계에서 잡힌다.
 *
 * 현재 소비자(`platform` 등)는 공급자의 이벤트 클래스를 직접 참조한다. 2단계에는 소비자가 자기
 * DTO 를 갖는 형태로 분리해야 하고, **그 분리의 안전망이 이 스냅샷이다** — 분리 자체는 이 티켓
 * 범위가 아니다(계약을 먼저 고정한다).
 */
class KafkaEventContractTest : DescribeSpec({

    // 프로덕션 빈 팩토리를 그대로 쓴다 — 테스트용 mapper 를 새로 만들면 와이어와 어긋난 계약을 고정한다.
    val kafkaObjectMapper: ObjectMapper = KafkaJsonObjectMapper().kafkaObjectMapper()

    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }

    val snapshotFile = File(
        requireNotNull(repositoryRoot),
        "bootstrap/src/test/resources/contract/kafka-events.json",
    )

    val fixedOccurredAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    /** 각 토픽의 변이별 대표 인스턴스. 새 변이를 추가하면 여기에도 넣어야 스냅샷이 커버한다. */
    val eventSamples: Map<String, Map<String, Any>> = mapOf(
        PaymentEvent.TOPIC to mapOf(
            PaymentEvent.TYPE_CONFIRMED to PaymentEvent.Confirmed(
                paymentId = 1L,
                orderType = OrderType.BOOKING,
                orderId = 2L,
                recipientUserId = 3L,
                amount = 10_000L,
                eventId = "fixed-event-id",
                occurredAt = fixedOccurredAt,
            ),
            PaymentEvent.TYPE_CANCELLED to PaymentEvent.Cancelled(
                paymentId = 1L,
                orderType = OrderType.BOOKING,
                orderId = 2L,
                eventId = "fixed-event-id",
                occurredAt = fixedOccurredAt,
            ),
        ),
        BookingEvent.TOPIC to mapOf(
            BookingEvent.TYPE_CONFIRMED to BookingEvent.Confirmed(
                bookingId = 1L,
                paymentId = 2L,
                recipientUserId = 3L,
                eventId = "fixed-event-id",
                occurredAt = fixedOccurredAt,
            ),
        ),
        TicketEvent.TOPIC to mapOf(
            TicketEvent.TYPE_ISSUED to TicketEvent.Issued(
                ticketOrderId = 1L,
                recipientUserId = 2L,
                eventTitle = "주말 리그 결승",
                eventId = "fixed-event-id",
                occurredAt = fixedOccurredAt,
            ),
        ),
    )

    val sealedRootByTopic = mapOf(
        PaymentEvent.TOPIC to PaymentEvent::class,
        BookingEvent.TOPIC to BookingEvent::class,
        TicketEvent.TOPIC to TicketEvent::class,
    )

    describe("커밋된 계약 스냅샷") {
        it("파일이 존재한다 — 파일이 곧 계약이다") {
            snapshotFile.isFile shouldBe true
        }
    }

    val snapshot: Map<String, Map<String, ContractFields>> = if (snapshotFile.isFile) {
        kafkaObjectMapper.readValue(
            snapshotFile,
            object : TypeReference<Map<String, Map<String, ContractFields>>>() {},
        )
    } else {
        emptyMap()
    }

    describe("토픽 이름 규약 (§9-2)") {
        eventSamples.keys.forEach { topic ->
            it("$topic 이 event.{domain}.{sub-domain}.v{N} 형식이다") {
                TOPIC_NAMING.matches(topic) shouldBe true
            }
        }

        it("스냅샷이 다루는 토픽 집합이 코드의 토픽 집합과 정확히 일치한다") {
            snapshot.keys shouldContainExactlyInAnyOrder eventSamples.keys
        }
    }

    describe("와이어 계약 대조 — 스냅샷과 다르면 실패한다") {
        eventSamples.forEach { (topic, variants) ->
            variants.forEach { (variantName, sample) ->
                it("$topic / $variantName payload 가 계약과 일치한다") {
                    val baseline = snapshot[topic]?.get(variantName)
                    baseline.shouldNotBeNull()

                    val current = ContractShape.of(sample, kafkaObjectMapper)
                    val violations = ContractCompatibility.violationsOf(baseline, current)

                    violations.map { "${it.field}: ${it.reason}" }.shouldBeEmpty()
                    current shouldBe baseline
                }
            }
        }
    }

    describe("sealed 변이 등록 누락 방지") {
        sealedRootByTopic.forEach { (topic, sealedRoot) ->
            it("$topic 의 sealed 하위 타입이 모두 @JsonSubTypes 에 등록돼 있다") {
                // 등록이 빠지면 그 변이는 발행은 되지만 **역직렬화에서 전면 실패**한다 —
                // 과거 결제 확정 유실 사고와 같은 실패면이다. 컴파일은 통과하므로 테스트만이 잡는다.
                val declaredSubtypes = sealedRoot.java.getAnnotation(JsonSubTypes::class.java)
                    ?.value?.map { it.value.simpleName }
                    ?: emptyList()
                val sealedSubclasses = sealedRoot.sealedSubclasses.map { it.simpleName }

                declaredSubtypes shouldContainExactlyInAnyOrder sealedSubclasses
            }
        }

        it("스냅샷의 변이 이름이 코드의 판별자 값과 일치한다") {
            eventSamples.forEach { (topic, variants) ->
                snapshot[topic]?.keys shouldContainExactlyInAnyOrder variants.keys
            }
        }
    }

    describe("역직렬화 왕복 — 결제 확정 유실 사고의 실패면") {
        eventSamples.forEach { (topic, variants) ->
            variants.forEach { (variantName, sample) ->
                it("$topic / $variantName 이 sealed 베이스 타입으로 되읽힌다") {
                    val sealedRoot = requireNotNull(sealedRootByTopic[topic]).java
                    val payload = kafkaObjectMapper.writeValueAsString(sample)

                    val restored = kafkaObjectMapper.readValue(payload, sealedRoot)

                    restored.shouldBeInstanceOf<Any>()
                    restored::class shouldBe sample::class
                    kafkaObjectMapper.readTree(payload).get("eventType").asText() shouldBe variantName
                }
            }
        }
    }
}) {
    private companion object {
        /** `event.{domain}.{sub-domain}.v{N}` — 버전 접미사는 생략 불가다 (§9-2). */
        val TOPIC_NAMING = """^event\.[a-z0-9-]+\.[a-z0-9-]+\.v\d+$""".toRegex()
    }
}
