package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class GoodsDomainServiceTest : BehaviorSpec({

    val productRepository = mockk<ProductRepository>()
    val stockRepository = mockk<StockRepository>()
    val service = GoodsDomainService(productRepository, stockRepository)

    Given("재고가 충분한 Product가 존재할 때") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://example.com/image.jpg",
            status = ProductStatus.ACTIVE,
        )
        val stock = Stock(productId = 1L, quantity = 10)

        every { productRepository.findById(1L) } returns product
        every { stockRepository.findByProductId(1L) } returns stock
        every { stockRepository.save(any()) } returns stock

        When("deductStock을 호출하면") {
            service.deductStock(productId = 1L, quantity = 3)

            Then("[U-05] StockRepository.save가 1회 호출된다") {
                verify(exactly = 1) { stockRepository.save(any()) }
                stock.quantity shouldBe 7
            }
        }
    }

    Given("재고가 부족한 Product가 존재할 때") {
        val product = Product(
            name = "배드민턴 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/badminton.jpg",
            status = ProductStatus.ACTIVE,
        )
        val stock = Stock(productId = 2L, quantity = 2)

        every { productRepository.findById(2L) } returns product
        every { stockRepository.findByProductId(2L) } returns stock

        When("5개를 차감 시도하면") {
            Then("[U-05] OutOfStockException이 발생한다") {
                shouldThrow<OutOfStockException> {
                    service.deductStock(productId = 2L, quantity = 5)
                }
            }
        }
    }

    Given("존재하지 않는 Product에 대해") {
        every { productRepository.findById(99L) } returns null

        When("deductStock을 호출하면") {
            Then("[U-05] ResourceNotFoundException이 발생한다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.ResourceNotFoundException> {
                    service.deductStock(productId = 99L, quantity = 1)
                }
            }
        }
    }
})
