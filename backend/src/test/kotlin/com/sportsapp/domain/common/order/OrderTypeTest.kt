package com.sportsapp.domain.common.order

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * BE-01: OrderType 공유 커널 이관 회귀 테스트.
 * 근거: 20260708-상품주문-공유상위컨텍스트-tdd.md 방안2 / BE-01 티켓.
 *
 * payment.vo.OrderType → domain.common.order.OrderType 이관 후에도
 * enum 값·displayName·순서가 그대로 보존되는지 검증한다.
 */
class OrderTypeTest : BehaviorSpec({

    Given("공유 커널로 이관된 OrderType") {
        When("enum 값 4개를 확인하면") {
            Then("BOOKING/TICKETING/GOODS/RECRUITMENT 4개 값이 이관 전과 동일하다") {
                OrderType.entries.map { it.name } shouldBe listOf("BOOKING", "TICKETING", "GOODS", "RECRUITMENT")
            }
        }

        When("displayName 을 확인하면") {
            Then("이관 전과 동일한 한글 라벨을 노출한다") {
                OrderType.BOOKING.displayName shouldBe "시설 예약"
                OrderType.TICKETING.displayName shouldBe "티켓 예매"
                OrderType.GOODS.displayName shouldBe "상품 주문"
                OrderType.RECRUITMENT.displayName shouldBe "모집 참가"
            }
        }
    }
})
