package com.sportsapp.infrastructure.persistence.dashboard

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockCustomRepositoryImpl
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.EventCustomRepositoryImpl
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class DashboardCountQueryTest(
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val stockCustomRepositoryImpl: StockCustomRepositoryImpl,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val eventCustomRepositoryImpl: EventCustomRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        Given("[R-01] ownerUserId=100 이벤트 SCHEDULED 2건, OPEN 1건, CLOSED 3건이 저장된 상태") {
            jdbcTemplate.execute("DELETE FROM events WHERE owner_id = 100")

            repeat(2) { i ->
                eventJpaRepository.save(
                    Event(
                        id = 0L,
                        title = "예정 이벤트 $i",
                        venue = "서울 경기장",
                        startsAt = ZonedDateTime.now().plusDays(i.toLong() + 1),
                        status = EventStatus.SCHEDULED,
                        ownerId = 100L,
                    )
                )
            }
            eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "오픈 이벤트",
                    venue = "부산 경기장",
                    startsAt = ZonedDateTime.now().plusDays(3),
                    status = EventStatus.OPEN,
                    ownerId = 100L,
                )
            )
            repeat(3) { i ->
                eventJpaRepository.save(
                    Event(
                        id = 0L,
                        title = "종료 이벤트 $i",
                        venue = "인천 경기장",
                        startsAt = ZonedDateTime.now().minusDays(i.toLong() + 1),
                        status = EventStatus.CLOSED,
                        ownerId = 100L,
                    )
                )
            }

            When("[R-01] countByOwnerIdGroupByStatus 쿼리 실행 시") {
                val result = eventCustomRepositoryImpl.countByOwnerIdGroupByStatus(100L)

                Then("[R-01] SCHEDULED 2, OPEN 1, CLOSED 3이 정확히 반환된다") {
                    result[EventStatus.SCHEDULED] shouldBe 2L
                    result[EventStatus.OPEN] shouldBe 1L
                    result[EventStatus.CLOSED] shouldBe 3L
                }
            }
        }

        Given("[R-02] ownerUserId=200의 상품: ACTIVE 4건(stock>0 3건, stock=0 1건), INACTIVE 2건") {
            jdbcTemplate.execute("DELETE FROM stocks WHERE product_id IN (SELECT id FROM products WHERE owner_id = 200)")
            jdbcTemplate.execute("DELETE FROM products WHERE owner_id = 200")

            repeat(3) { i ->
                val product = productJpaRepository.save(
                    Product(
                        name = "활성 재고있음 $i",
                        category = ProductCategory.APPAREL,
                        price = BigDecimal("10000"),
                        description = "desc",
                        imageUrl = "https://img",
                        status = ProductStatus.ACTIVE,
                        ownerId = 200L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 5))
            }

            val outOfStockProduct = productJpaRepository.save(
                Product(
                    name = "활성 재고없음",
                    category = ProductCategory.APPAREL,
                    price = BigDecimal("10000"),
                    description = "desc",
                    imageUrl = "https://img",
                    status = ProductStatus.ACTIVE,
                    ownerId = 200L,
                )
            )
            stockJpaRepository.save(Stock(productId = outOfStockProduct.id, quantity = 0))

            repeat(2) { i ->
                val product = productJpaRepository.save(
                    Product(
                        name = "비활성 $i",
                        category = ProductCategory.APPAREL,
                        price = BigDecimal("10000"),
                        description = "desc",
                        imageUrl = "https://img",
                        status = ProductStatus.INACTIVE,
                        ownerId = 200L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 10))
            }

            When("[R-02] countByOwnerIdAndStatus + countOutOfStockByOwnerId 쿼리 실행 시") {
                val activeCount = productJpaRepository.countByOwnerIdAndStatusAndDeletedAtIsNull(200L, ProductStatus.ACTIVE)
                val outOfStockCount = stockCustomRepositoryImpl.countOutOfStockByOwnerId(200L)

                Then("[R-02] ACTIVE 4건, outOfStock(stock=0) 1건이 반환된다") {
                    activeCount shouldBe 4L
                    outOfStockCount shouldBe 1L
                }
            }
        }
    }
}
