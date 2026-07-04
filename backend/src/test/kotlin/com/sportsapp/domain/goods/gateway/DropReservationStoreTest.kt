package com.sportsapp.domain.goods.gateway

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.time.Duration

class DropReservationStoreTest : BehaviorSpec({

    Given("DropReservationStore mock 이 각 판정 결과를 반환할 때") {
        val store: DropReservationStore = mockk()
        val dropId = 1L
        val userId = 100L
        val quantity = 1
        val perUserLimit = 2
        val idempotencyKey = "idem-key-1"

        When("reserve 가 ReservationResult 의 4개 하위 타입을 각각 반환하면") {
            val results = listOf(
                ReservationResult.Admitted,
                ReservationResult.AlreadyReserved,
                ReservationResult.SoldOut,
                ReservationResult.PerUserLimitExceeded(limit = perUserLimit),
            )

            Then("when 전수 분기(else 없이)로 4개 분기 모두 처리된다") {
                results.forEach { result: ReservationResult ->
                    every {
                        store.reserve(dropId, userId, quantity, perUserLimit, idempotencyKey)
                    } returns result

                    val actual = store.reserve(dropId, userId, quantity, perUserLimit, idempotencyKey)

                    // else 브랜치 없는 전수 분기 — 컴파일 타임에 누락 분기를 잡는다
                    // 완충(FR-7)은 reserve()의 판정 결과가 아니라 tryAcquireThrottle()로 별도 판정한다(코드 리뷰 p1).
                    val branch: String = when (actual) {
                        is ReservationResult.Admitted -> "ADMITTED"
                        is ReservationResult.AlreadyReserved -> "ALREADY_RESERVED"
                        is ReservationResult.SoldOut -> "SOLD_OUT"
                        is ReservationResult.PerUserLimitExceeded -> "PER_USER_LIMIT_EXCEEDED"
                    }

                    branch shouldNotBe null
                }
            }
        }
    }

    Given("PerUserLimitExceeded 결과") {
        When("한도값 3을 담아 생성하면") {
            val result = ReservationResult.PerUserLimitExceeded(limit = 3)

            Then("limit 프로퍼티로 한도값을 노출한다") {
                result.limit shouldBeExactly 3
            }
        }
    }

    Given("DropReservationStore interface 시그니처") {
        val store: DropReservationStore = mockk()

        When("seedIfAbsent/confirmSuccess/cancel/remaining 을 호출하면") {
            Then("컴파일이 성공해 시그니처가 계약과 일치함을 확인한다") {
                every { store.seedIfAbsent(any(), any(), any()) } returns Unit
                every { store.confirmSuccess(any(), any(), any()) } returns Unit
                every { store.cancel(any(), any(), any(), any()) } returns Unit
                every { store.remaining(any()) } returns 10

                store.seedIfAbsent(1L, 100, Duration.ofHours(1))
                store.confirmSuccess(1L, 100L, "idem-key-2")
                store.cancel(1L, 100L, 1, "idem-key-2")
                val remaining = store.remaining(1L)

                remaining shouldBe 10
            }
        }

        When("tryAcquireThrottle/releaseThrottle 을 호출하면") {
            Then("완충(FR-7) 전용 시그니처가 reserve()와 독립적으로 존재한다(코드 리뷰 p1)") {
                every { store.tryAcquireThrottle() } returns true
                every { store.releaseThrottle() } returns Unit

                val acquired = store.tryAcquireThrottle()
                store.releaseThrottle()

                acquired shouldBe true
            }
        }
    }

    Given("컴파일된 com.sportsapp 클래스 전체") {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.sportsapp")

        When("DropReservationStore 와 ReservationResult 의 위치·의존을 검사하면") {
            Then("두 타입 모두 domain.goods.gateway 패키지에 위치한다") {
                classes()
                    .that().haveSimpleName("DropReservationStore")
                    .or().haveSimpleName("ReservationResult")
                    .should().resideInAPackage("com.sportsapp.domain.goods.gateway")
                    .check(importedClasses)
            }

            Then("DropReservationStore 는 infrastructure 패키지를 import하지 않는다") {
                noClasses()
                    .that().haveSimpleName("DropReservationStore")
                    .should().dependOnClassesThat().resideInAPackage("com.sportsapp.infrastructure..")
                    .check(importedClasses)
            }
        }
    }
})
