package com.sportsapp.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.jupiter.api.Test

/**
 * BE 코드 검토(2026-05-31)에서 발견된 재발 방지용 정적 규칙.
 * be-code-convention.md 의 다음 항목을 ArchUnit 으로 강제한다.
 *
 * - 도메인 컨텍스트 간 교차 참조 금지 (domain.common 만 허용)
 * - UseCase 는 DomainService 만 주입 (Repository/Gateway/DomainEventPublisher 직접 주입 금지)
 *
 * allowEmptyShould(true): 클래스가 없는 환경에서도 룰 선언을 유지한다.
 */
class AggregateAndUseCaseRulesTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    @Test
    fun `도메인 컨텍스트는 서로 의존하지 않는다 (common 만 허용)`() {
        SlicesRuleDefinition.slices()
            .matching("com.sportsapp.domain.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                JavaClass.Predicates.resideInAPackage("com.sportsapp.domain.common.."),
            )
            .because("도메인 컨텍스트 간 참조는 FK id(Long)로만 — domain.<X>가 domain.<Y>를 import 하면 안 된다 (domain.common 제외)")
            .check(importedClasses)
    }

    @Test
    fun `UseCase 는 Repository 를 직접 주입하지 않는다`() {
        noClasses()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("UseCase 는 DomainService 만 호출한다 — 조회는 DomainService 책임 (be-code-convention 'UseCase 규칙')")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `UseCase 는 Gateway 를 직접 주입하지 않는다`() {
        noClasses()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Gateway")
            .because("UseCase 는 DomainService 만 호출한다 — 외부 시스템 호출은 DomainService 경유")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `UseCase 는 DomainEventPublisher 를 직접 주입하지 않는다`() {
        noClasses()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("DomainEventPublisher")
            .because("도메인 이벤트 발행은 DomainService 책임")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
