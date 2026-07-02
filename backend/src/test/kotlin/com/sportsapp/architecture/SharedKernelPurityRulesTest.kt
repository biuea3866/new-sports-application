package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty

/**
 * [common 공유 커널 순수성 규칙 (R4)]
 * 근거: TDD "FR-7 정적 검증 방법" R4 / ADR-002 / ADR-005.
 *
 * 공유 커널 domain.common 이 어떤 도메인 패키지도 import 하지 않음을 강제한다.
 * common이 특정 도메인에 의존하면 그 도메인 변경이 전 도메인에 전파되므로,
 * common 은 순수 공통 계약(AggregateRoot/DomainEvent/OwnershipGuard/storage 등)만 담아야 한다.
 */
class SharedKernelPurityRulesTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    val corePackages = DomainClassification.core.map { "com.sportsapp.domain.$it.." }.toTypedArray()
    val supportAndSubsystemPackages =
        (DomainClassification.support + DomainClassification.subsystem).map { "com.sportsapp.domain.$it.." }.toTypedArray()

    test("domain.common 은 코어 도메인(booking·goods·ticketing 등)을 import하지 않는다 (R4, 베이스라인 0건)") {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain.common..")
            .should().dependOnClassesThat().resideInAnyPackage(*corePackages)
            .because("공유 커널이 코어 도메인에 의존하면 코어 변경이 전 도메인에 전파된다")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("domain.common 은 notification·operator·mcp 등 지원/서브시스템 도메인을 import하지 않는다 (R4)") {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain.common..")
            .should().dependOnClassesThat().resideInAnyPackage(*supportAndSubsystemPackages)
            .because("공유 커널 순수성 — 지원/서브시스템 도메인 의존도 동일하게 금지한다")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("common 하위(exceptions, security, storage) 전체가 규칙 스캔 대상에 포함된다 (엣지: 서브패키지 커버)") {
        val commonClasses = importedClasses.filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.domain.common") }
        commonClasses.shouldNotBeEmpty()

        listOf("exceptions", "security", "storage").forEach { subPackage ->
            val hasSubPackageClasses = commonClasses.any { javaClass ->
                javaClass.packageName.startsWith("com.sportsapp.domain.common.$subPackage")
            }
            hasSubPackageClasses.shouldBeTrue()
        }
    }

    test("(위반 시나리오) common이 domain.user를 import하면 규칙이 실패한다") {
        // common은 domain.user를 실제로 import하지 않는다(베이스라인 0건) — 대신 common.storage가 실제로
        // 의존하는 common.exceptions를 "금지 대상"으로 좁혀 지정해 ArchUnit이 실제 위반을 탐지하는지
        // sanity check 한다 (ImageDomainService → UnsupportedContentTypeException 의존을 이용).
        val ruleTreatingSiblingSubpackageAsForbidden = noClasses()
            .that().resideInAPackage("com.sportsapp.domain.common.storage..")
            .should().dependOnClassesThat().resideInAnyPackage("com.sportsapp.domain.common.exceptions..")

        shouldThrow<AssertionError> {
            ruleTreatingSiblingSubpackageAsForbidden.check(importedClasses)
        }
    }
})
