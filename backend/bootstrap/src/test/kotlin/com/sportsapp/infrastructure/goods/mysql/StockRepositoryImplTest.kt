package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.repository.StockCustomRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class StockRepositoryImplTest : BehaviorSpec({

    val stockJpaRepository = mockk<StockJpaRepository>()
    val stockCustomRepository = mockk<StockCustomRepository>()
    val repository = StockRepositoryImpl(stockJpaRepository, stockCustomRepository)

    Given("StockRepositoryImpl") {

        When("save 를 호출하면") {
            val stock = Stock(productId = 1L, quantity = 10)
            every { stockJpaRepository.save(stock) } returns stock

            val result = repository.save(stock)

            Then("[U-01] JpaRepository.save 로 위임하고 결과를 그대로 반환한다") {
                result shouldBe stock
                verify(exactly = 1) { stockJpaRepository.save(stock) }
            }
        }

        When("findByProductId 를 호출하면") {
            val stock = Stock(productId = 7L, quantity = 3)
            every { stockJpaRepository.findByProductId(7L) } returns stock

            val result = repository.findByProductId(7L)

            Then("[U-02] JpaRepository.findByProductId 로 위임한다") {
                result shouldBe stock
                verify(exactly = 1) { stockJpaRepository.findByProductId(7L) }
            }
        }

        When("countOutOfStockByOwnerId 를 호출하면") {
            every { stockCustomRepository.countOutOfStockByOwnerId(42L) } returns 5L

            val result = repository.countOutOfStockByOwnerId(42L)

            Then("[U-03] StockCustomRepository 로 위임한다 (JpaRepository 에는 해당 메서드가 없다)") {
                result shouldBe 5L
                verify(exactly = 1) { stockCustomRepository.countOutOfStockByOwnerId(42L) }
            }
        }
    }
})
