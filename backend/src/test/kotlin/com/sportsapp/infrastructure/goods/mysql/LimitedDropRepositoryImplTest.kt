package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
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

                Then("동일한 LimitedDrop을 복원한다") {
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

                Then("status=OPEN 회차를 반환한다") {
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

                Then("null을 반환한다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("SCHEDULED·OPEN·CLOSED 회차가 섞여 있는 경우") {
            val scheduledProductId = System.nanoTime()
            val openProductId = scheduledProductId + 1
            val closedProductId = scheduledProductId + 2
            val scheduledDrop = LimitedDrop.create(
                productId = scheduledProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 10,
                perUserLimit = 1,
            )
            val openDrop = LimitedDrop.create(
                productId = openProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 20,
                perUserLimit = 1,
            ).also { it.open() }
            val closedDrop = LimitedDrop.create(
                productId = closedProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(2).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 30,
                perUserLimit = 1,
            ).also { it.open(); it.close() }
            limitedDropJpaRepository.saveAndFlush(scheduledDrop)
            limitedDropJpaRepository.saveAndFlush(openDrop)
            limitedDropJpaRepository.saveAndFlush(closedDrop)

            When("findAllActive를 호출하면") {
                val result = limitedDropRepository.findAllActive()
                val resultProductIds = result.map { it.productId }

                Then("SCHEDULED·OPEN 회차만 포함하고 CLOSED 회차는 제외한다") {
                    resultProductIds shouldContain scheduledProductId
                    resultProductIds shouldContain openProductId
                    resultProductIds shouldNotContain closedProductId
                }
            }
        }

        Given("두 상품에 각각 OPEN 회차, 한 상품엔 CLOSED 회차만 존재하는 경우") {
            val openProductId = System.nanoTime()
            val otherOpenProductId = openProductId + 1
            val closedOnlyProductId = openProductId + 2
            val openDrop = LimitedDrop.create(
                productId = openProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 10,
                perUserLimit = 1,
            ).also { it.open() }
            val otherOpenDrop = LimitedDrop.create(
                productId = otherOpenProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 20,
                perUserLimit = 1,
            ).also { it.open() }
            val closedDrop = LimitedDrop.create(
                productId = closedOnlyProductId,
                openAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(2).truncatedTo(ChronoUnit.MICROS),
                closeAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.MICROS),
                limitedQuantity = 30,
                perUserLimit = 1,
            ).also { it.open(); it.close() }
            limitedDropJpaRepository.saveAndFlush(openDrop)
            limitedDropJpaRepository.saveAndFlush(otherOpenDrop)
            limitedDropJpaRepository.saveAndFlush(closedDrop)

            When("findOpenByProductIds를 세 상품 id로 호출하면") {
                val result = limitedDropRepository.findOpenByProductIds(
                    listOf(openProductId, otherOpenProductId, closedOnlyProductId)
                )
                val resultProductIds = result.map { it.productId }

                Then("활성 회차 2건만 반환하고 CLOSED 회차는 제외한다") {
                    result shouldHaveSize 2
                    resultProductIds shouldContain openProductId
                    resultProductIds shouldContain otherOpenProductId
                    resultProductIds shouldNotContain closedOnlyProductId
                }
            }

            When("findOpenByProductIds를 빈 목록으로 호출하면") {
                val result = limitedDropRepository.findOpenByProductIds(emptyList())

                Then("빈 목록을 반환한다") {
                    result.shouldBeEmpty()
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

                Then("status·openAt·closeAt이 손실 없이 왕복된다") {
                    found.currentStatus shouldBe LimitedDropStatus.SCHEDULED
                    found.openAt.toInstant() shouldBe openAt.toInstant()
                    found.closeAt.toInstant() shouldBe closeAt.toInstant()
                }
            }
        }
    }
}
