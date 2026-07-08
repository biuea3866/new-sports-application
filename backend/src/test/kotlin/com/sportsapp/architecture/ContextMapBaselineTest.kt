package com.sportsapp.architecture

import com.sportsapp.domain.common.storage.ImageDomainService
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import jakarta.persistence.Entity

/**
 * [컨텍스트 맵 베이스라인 회귀 스펙 (R1/R2 신규 분류 커버)]
 * 근거: TDD "컨텍스트 맵 5개 분류", "FR-7 정적 검증 방법" R1/R2 / ADR-001.
 *
 * 기존 AggregateAndUseCaseRulesTest(R1)·LayerDependencyRulesTest(R2)가 이미 통과 중(베이스라인 0건)임을
 * 전제로, 신규 분류 컨텍스트(dashboard·image·weather)가 규칙 스캔에서 누락 없이 다뤄지는지 확인한다.
 * 기존 3개 arch 파일은 수정하지 않는다(Single Writer 보호).
 */
class ContextMapBaselineTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    test("domain 하위 12개 컨텍스트가 서로 domain-레이어 import를 하지 않으면 베이스라인이 통과한다 (R1 회귀 확인)") {
        SlicesRuleDefinition.slices()
            .matching("com.sportsapp.domain.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                JavaClass.Predicates.resideInAPackage("com.sportsapp.domain.common.."),
            )
            .because("도메인 컨텍스트 간 참조는 ID(Long)로만 — domain.common 만 예외 (신규 5개 분류 관점 재확인)")
            .check(importedClasses)
    }

    test("ImageDomainService가 domain.common.storage 패키지에 위치하면 통과한다 (image 도메인 로직 위치 고정)") {
        ImageDomainService::class.java.packageName shouldBe "com.sportsapp.domain.common.storage"
    }

    test("weather 컨텍스트에 Entity가 없어도(gateway+service만) 규칙 스캔이 오류 없이 통과한다 (엣지: 빈 Aggregate)") {
        val weatherClasses = importedClasses.filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.domain.weather") }
        weatherClasses.shouldNotBeEmpty()

        val weatherHasNoEntity = weatherClasses.none { javaClass -> javaClass.isAnnotatedWith(Entity::class.java) }
        weatherHasNoEntity.shouldBeTrue()

        // Entity가 없는 컨텍스트에도 R1 슬라이스 규칙이 예외 없이 동작함을 재확인한다.
        SlicesRuleDefinition.slices()
            .matching("com.sportsapp.domain.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                JavaClass.Predicates.resideInAPackage("com.sportsapp.domain.common.."),
            )
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("dashboard가 domain 레이어를 갖지 않으면 조회·유틸 분류 전제가 통과한다") {
        val dashboardDomainClasses = importedClasses.filter { javaClass ->
            javaClass.packageName.startsWith("com.sportsapp.domain.dashboard")
        }

        dashboardDomainClasses.shouldBeEmpty()
    }

    test("(위반 시나리오) common 예외 없이 slices 규칙을 적용하면 도메인→common 의존이 위반으로 잡힌다") {
        // 모든 도메인이 domain.common 을 의존하는 것은 정상(공유 커널)이지만, 이 예외를 빼고 실행하면
        // SlicesRuleDefinition.notDependOnEachOther() 가 그 의존을 위반으로 잡아야 한다 — R1 메커니즘 sanity check.
        val ruleWithoutCommonException = SlicesRuleDefinition.slices()
            .matching("com.sportsapp.domain.(*)..")
            .should().notDependOnEachOther()

        shouldThrow<AssertionError> {
            ruleWithoutCommonException.check(importedClasses)
        }
    }
})
