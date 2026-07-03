package com.sportsapp.domain.goods

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import com.sportsapp.domain.goods.exception.InvalidLimitedDropStateException
import com.sportsapp.domain.goods.exception.LimitedDropClosedException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class LimitedDropTest : BehaviorSpec({

    Given("openAt이 아직 도래하지 않은 회차") {
        Then("validatePurchasable 호출 시 openAt을 담은 LimitedDropTooEarlyException을 던진다") {
            val openAt = ZonedDateTime.now().plusDays(1)
            val closeAt = openAt.plusDays(1)
            val limitedDrop = LimitedDrop.create(
                productId = 1L,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 100,
                perUserLimit = 1,
            )

            val exception = shouldThrow<LimitedDropTooEarlyException> {
                limitedDrop.validatePurchasable()
            }
            exception.openAt shouldBe openAt
        }
    }

    Given("openAt 이후·closeAt 이전이고 OPEN 상태인 회차") {
        Then("validatePurchasable은 예외 없이 통과한다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.OPEN,
            )

            limitedDrop.validatePurchasable()
        }
    }

    Given("SOLD_OUT 상태인 회차") {
        Then("validatePurchasable은 LimitedDropSoldOutException을 던진다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.SOLD_OUT,
            )

            shouldThrow<LimitedDropSoldOutException> {
                limitedDrop.validatePurchasable()
            }
        }
    }

    Given("closeAt이 지난 회차") {
        Then("validatePurchasable은 LimitedDropClosedException을 던진다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = ZonedDateTime.now().minusDays(2),
                closeAt = ZonedDateTime.now().minusMinutes(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.OPEN,
            )

            shouldThrow<LimitedDropClosedException> {
                limitedDrop.validatePurchasable()
            }
        }
    }

    Given("SCHEDULED 상태인 회차") {
        Then("open → markSoldOut → close 순서 전이는 모두 허용되어 CLOSED가 된다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.SCHEDULED,
            )

            limitedDrop.open()
            limitedDrop.markSoldOut()
            limitedDrop.close()

            limitedDrop.currentStatus shouldBe LimitedDropStatus.CLOSED
        }
    }

    Given("CLOSED 상태인 회차") {
        Then("open 호출 시 InvalidLimitedDropStateException을 던진다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = ZonedDateTime.now().minusDays(2),
                closeAt = ZonedDateTime.now().minusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.CLOSED,
            )

            shouldThrow<InvalidLimitedDropStateException> {
                limitedDrop.open()
            }
        }
    }

    Given("LimitedDropStatus 전이 규칙") {
        Then("허용된 전이는 true, CLOSED→OPEN은 false를 반환한다") {
            LimitedDropStatus.SCHEDULED.canTransitTo(LimitedDropStatus.OPEN) shouldBe true
            LimitedDropStatus.OPEN.canTransitTo(LimitedDropStatus.SOLD_OUT) shouldBe true
            LimitedDropStatus.OPEN.canTransitTo(LimitedDropStatus.CLOSED) shouldBe true
            LimitedDropStatus.SOLD_OUT.canTransitTo(LimitedDropStatus.CLOSED) shouldBe true
            LimitedDropStatus.CLOSED.canTransitTo(LimitedDropStatus.OPEN) shouldBe false
            LimitedDropStatus.SOLD_OUT.canTransitTo(LimitedDropStatus.OPEN) shouldBe false
        }
    }

    Given("openAt이 closeAt보다 늦은 잘못된 생성 파라미터") {
        Then("create 호출 시 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                LimitedDrop.create(
                    productId = 1L,
                    openAt = ZonedDateTime.now().plusDays(2),
                    closeAt = ZonedDateTime.now().plusDays(1),
                    limitedQuantity = 100,
                    perUserLimit = 1,
                )
            }
        }
    }

    Given("limitedQuantity가 0 이하인 잘못된 생성 파라미터") {
        Then("create 호출 시 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                LimitedDrop.create(
                    productId = 1L,
                    openAt = ZonedDateTime.now(),
                    closeAt = ZonedDateTime.now().plusDays(1),
                    limitedQuantity = 0,
                    perUserLimit = 1,
                )
            }
        }
    }

    Given("perUserLimit이 0 이하인 잘못된 생성 파라미터") {
        Then("create 호출 시 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                LimitedDrop.create(
                    productId = 1L,
                    openAt = ZonedDateTime.now(),
                    closeAt = ZonedDateTime.now().plusDays(1),
                    limitedQuantity = 100,
                    perUserLimit = 0,
                )
            }
        }
    }

    Given("영속화 계층에서 복원하는 상황") {
        Then("reconstitute는 검증 없이 필드를 그대로 복원한다") {
            val invalidOpenAt = ZonedDateTime.now().plusDays(2)
            val invalidCloseAt = ZonedDateTime.now().plusDays(1)

            val limitedDrop = LimitedDrop.reconstitute(
                productId = 1L,
                openAt = invalidOpenAt,
                closeAt = invalidCloseAt,
                limitedQuantity = -1,
                perUserLimit = -1,
                status = LimitedDropStatus.CLOSED,
            )

            limitedDrop.currentStatus shouldBe LimitedDropStatus.CLOSED
            limitedDrop.limitedQuantity shouldBe -1
            limitedDrop.perUserLimit shouldBe -1
        }
    }

    Given("OPEN 상태의 회차에서 오버셀이 감지된 상황") {
        Then("recordOversold 호출 시 source=oversell·severity=critical 태그를 가진 LimitedDropOversoldEvent가 적재된다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 42L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.OPEN,
            )

            limitedDrop.recordOversold(5)
            val events = limitedDrop.pullDomainEvents()

            events.size shouldBe 1
            val event = events[0] as LimitedDropOversoldEvent
            event.productId shouldBe 42L
            event.detectedQuantity shouldBe 5
            event.source shouldBe "oversell"
            event.severity shouldBe "critical"
        }

        Then("pullDomainEvents 호출 후에는 내부 이벤트 목록이 비워진다") {
            val limitedDrop = LimitedDrop.reconstitute(
                productId = 42L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.OPEN,
            )

            limitedDrop.recordOversold(5)
            limitedDrop.pullDomainEvents()

            limitedDrop.pullDomainEvents().size shouldBe 0
        }
    }
})
