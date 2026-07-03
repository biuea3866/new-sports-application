package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class LimitedDropRepositoryImplTest(
    @Autowired private val limitedDropRepository: LimitedDropRepository,
    @Autowired private val limitedDropJpaRepository: LimitedDropJpaRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("신규 개설된 LimitedDrop") {
            val productId = System.nanoTime()
            val openAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS)
            val closeAt = openAt.plusDays(1)
            val limitedDrop = LimitedDrop.create(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 100,
                perUserLimit = 2,
            )

            When("save 후 findById로 조회하면") {
                val saved = limitedDropRepository.save(limitedDrop)
                val found = limitedDropRepository.findById(saved.id)

                Then("[R-01] 동일한 LimitedDrop을 복원한다") {
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                    found.productId shouldBe productId
                    found.currentStatus shouldBe LimitedDropStatus.SCHEDULED
                }
            }
        }

        Given("특정 상품에 OPEN 상태 회차만 존재하는 경우") {
            val productId = System.nanoTime()
            val openAt = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).truncatedTo(ChronoUnit.MICROS)
            val closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS)
            val drop = LimitedDrop.create(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 50,
                perUserLimit = 1,
            )
            drop.open()
            limitedDropJpaRepository.saveAndFlush(drop)

            When("findOpenByProductId를 호출하면") {
                val result = limitedDropRepository.findOpenByProductId(productId)

                Then("[R-02] status=OPEN 회차를 반환한다") {
                    result.shouldNotBeNull()
                    result.productId shouldBe productId
                    result.currentStatus shouldBe LimitedDropStatus.OPEN
                }
            }
        }

        Given("특정 상품에 CLOSED 상태 회차만 존재하는 경우") {
            val productId = System.nanoTime()
            val openAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(2).truncatedTo(ChronoUnit.MICROS)
            val closeAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.MICROS)
            val drop = LimitedDrop.create(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 30,
                perUserLimit = 1,
            )
            drop.open()
            drop.close()
            limitedDropJpaRepository.saveAndFlush(drop)

            When("findOpenByProductId를 호출하면") {
                val result = limitedDropRepository.findOpenByProductId(productId)

                Then("[R-03] null을 반환한다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("status·시간 컬럼을 가진 LimitedDrop") {
            val productId = System.nanoTime()
            val openAt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).truncatedTo(ChronoUnit.MICROS)
            val closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(4).truncatedTo(ChronoUnit.MICROS)
            val drop = LimitedDrop.create(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 10,
                perUserLimit = 1,
            )

            When("save 후 재조회하면") {
                val saved = limitedDropRepository.save(drop)
                val found = requireNotNull(limitedDropRepository.findById(saved.id))

                Then("[R-04] status·openAt·closeAt이 손실 없이 왕복된다") {
                    found.currentStatus shouldBe LimitedDropStatus.SCHEDULED
                    found.openAt.toInstant() shouldBe openAt.toInstant()
                    found.closeAt.toInstant() shouldBe closeAt.toInstant()
                }
            }
        }
    }
}
