package com.sportsapp.domain.common

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-01] AggregateRoot.pullDomainEvents 단위 테스트
 */
class AggregateRootTest : BehaviorSpec({

    data class TestEvent(
        override val aggregateId: Long,
        override val topic: String? = null,
        override val eventId: String = "test-event-id",
        override val occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : DomainEvent

    class TestAggregate : AggregateRoot() {
        fun addEvent(event: DomainEvent) {
            registerEvent(event)
        }
    }

    Given("이벤트가 2개 등록된 AggregateRoot") {
        val aggregate = TestAggregate()
        aggregate.addEvent(TestEvent(aggregateId = 1L))
        aggregate.addEvent(TestEvent(aggregateId = 1L, eventId = "second-event-id"))

        When("pullDomainEvents 를 호출하면") {
            val events = aggregate.pullDomainEvents()

            Then("[U-01] 적재된 이벤트 목록을 반환한다") {
                events shouldHaveSize 2
            }

            Then("[U-01] 이후 재호출 시 빈 리스트를 반환한다") {
                aggregate.pullDomainEvents().shouldBeEmpty()
            }
        }
    }

    Given("이벤트가 없는 AggregateRoot") {
        val aggregate = TestAggregate()

        When("pullDomainEvents 를 호출하면") {
            val events = aggregate.pullDomainEvents()

            Then("[U-01] 빈 리스트를 반환한다") {
                events.shouldBeEmpty()
            }
        }
    }

    Given("이벤트 1개가 등록된 AggregateRoot") {
        val aggregate = TestAggregate()
        val event = TestEvent(aggregateId = 42L)
        aggregate.addEvent(event)

        When("pullDomainEvents 를 호출하면") {
            val events = aggregate.pullDomainEvents()

            Then("[U-01] 반환된 이벤트의 aggregateId 가 일치한다") {
                events.first().aggregateId shouldBe 42L
            }
        }
    }
})
