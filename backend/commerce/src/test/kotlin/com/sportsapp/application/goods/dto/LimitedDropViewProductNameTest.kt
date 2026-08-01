package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.Product
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 한정판 회차 응답이 상품명을 담는지 검증한다.
 *
 * 회귀 배경: 한정판 상세 화면에 상품명이 어디에도 없었다(유즈케이스 캡쳐 14-한정판-드롭).
 * 회차 응답이 productId 만 담고 사람이 읽는 이름을 내려주지 않은 것이 원인이다.
 */
class LimitedDropViewProductNameTest : BehaviorSpec({

    Given("상품과 연결된 한정판 회차가 있는 상황") {
        val product = mockk<Product> {
            every { name } returns "실내 클라이밍 초크백"
            every { imageUrl } returns "https://cdn.example.com/121.jpg"
            every { price } returns BigDecimal("119000")
        }
        val productWithStock = ProductWithStock(product = product, stockQuantity = 300)

        val drop = mockk<LimitedDrop> {
            every { id } returns 7L
            every { productId } returns 121L
            every { openAt } returns ZonedDateTime.now().minusHours(1)
            every { closeAt } returns ZonedDateTime.now().plusHours(1)
            every { perUserLimit } returns 2
            every { limitedQuantity } returns 300
            every { effectiveStatus(any()) } returns
                com.sportsapp.domain.goods.entity.LimitedDropStatus.OPEN
        }

        When("회차 응답을 만들면") {
            val view = LimitedDropView.of(
                drop = drop,
                remaining = 300,
                productName = productWithStock.productName,
                productImageUrl = productWithStock.productImageUrl,
                price = productWithStock.price,
            )

            Then("사람이 읽는 상품명이 담긴다") {
                view.productName shouldBe "실내 클라이밍 초크백"
            }

            Then("상품 이미지와 단가도 함께 담긴다") {
                view.productImageUrl shouldBe "https://cdn.example.com/121.jpg"
                view.price shouldBe BigDecimal("119000")
            }

            Then("기존 재고 필드는 그대로 유지된다") {
                view.remaining shouldBe 300
                view.totalQuantity shouldBe 300
            }
        }
    }

    Given("상품 래퍼가 주어진 상황") {
        val product = mockk<Product> {
            every { name } returns "카본 배드민턴 라켓"
            every { imageUrl } returns ""
            every { price } returns BigDecimal("29000")
        }

        When("상품 정보를 래퍼 메서드로 읽으면") {
            val productWithStock = ProductWithStock(product = product, stockQuantity = 10)

            Then("래퍼가 상품명·이미지를 자기 프로퍼티로 노출한다") {
                productWithStock.productName shouldBe "카본 배드민턴 라켓"
                productWithStock.productImageUrl shouldBe ""
            }
        }
    }
})
