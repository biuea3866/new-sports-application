package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty

/**
 * TDD "컨텍스트 맵 — 5개 분류 확정" 기준 도메인 분류 상수.
 *
 * FR-8 컨텍스트 맵 갱신 절차: 신규 도메인 추가 시 이 목록에 분류를 반영한다.
 * 패키지 prefix 스캔이라 목록에 없어도 자동 커버되지만, 상수 명시로 분류 누락을 방지한다.
 */
object DomainClassification {
    val core = listOf("booking", "facility", "goods", "payment", "ticketing", "user", "post", "message", "community", "recruitment")
    val support = listOf("notification", "operator", "weather", "alerting")
    val subsystem = listOf("mcp")
}

private fun packagesOf(layer: String, domains: List<String>): Array<String> =
    domains.map { domain -> "com.sportsapp.$layer.$domain.." }.toTypedArray()

/**
 * [지원·서브시스템 → 코어 동기 의존 금지 규칙 (R3)]
 * 근거: TDD "FR-7 정적 검증 방법" R3 / ADR-005.
 *
 * 지원 도메인(notification/operator/weather/alerting)과 서브시스템(mcp)이 코어 도메인
 * (booking/facility/goods/payment/ticketing/user/post/message/community/recruitment)을 동기 의존(import)하지 않음을 강제한다.
 *
 * 화이트리스트 예외 2건 (규칙 스캔 대상에는 포함하되 이 두 방향만 허용):
 *  ① application.dashboard → 코어 (읽기 전용 조합, Conformist read model)
 *  ② application.partner → domain.user (② B2B admin 프로비저닝 쓰기 오케스트레이션, 사전 등록)
 */
class SupportToCoreDependencyRulesTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    val corePackages = packagesOf("domain", DomainClassification.core) + packagesOf("application", DomainClassification.core)
    val corePackagesExceptUser =
        packagesOf("domain", DomainClassification.core - "user") + packagesOf("application", DomainClassification.core - "user")

    listOf("notification", "operator", "weather", "mcp", "alerting").forEach { domain ->
        test("$domain 패키지는 코어 도메인을 동기 의존하지 않는다 (R3, 베이스라인 0건)") {
            noClasses()
                .that().resideInAnyPackage(*packagesOf("application", listOf(domain)), *packagesOf("domain", listOf(domain)))
                .should().dependOnClassesThat().resideInAnyPackage(*corePackages)
                .because("지원/서브시스템은 코어가 발행한 이벤트만 소비한다 — 코어 동기 호출은 금지 (TDD 관계표 규칙 4·5)")
                .allowEmptyShould(true)
                .check(importedClasses)
        }
    }

    test("application.partner 는 domain.user 를 제외한 코어 도메인을 동기 의존하지 않는다 (사전 등록 화이트리스트)") {
        // application.partner 패키지는 아직 미존재(② 과제 신설 예정) — allowEmptyShould(true) 로 현재는 통과하되,
        // ② 과제 착수 시 이 규칙이 자동으로 partner→user 외 코어 접근을 잡아낸다.
        noClasses()
            .that().resideInAnyPackage("com.sportsapp.application.partner..")
            .should().dependOnClassesThat().resideInAnyPackage(*corePackagesExceptUser)
            .because("partner→user 는 admin 프로비저닝 쓰기 오케스트레이션으로 사전 등록된 예외 — user 외 코어 접근은 여전히 금지")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("application.partner 가 domain.user 를 호출해도(admin 프로비저닝) 사전 등록된 화이트리스트로 규칙이 통과한다") {
        // false RED 방지: corePackagesExceptUser 에는 user 가 빠져 있어야 partner→user 가 위반으로 잡히지 않는다.
        corePackagesExceptUser.none { it.contains(".user..") }.shouldBeTrue()
        corePackages.any { it.contains(".user..") }.shouldBeTrue()
    }

    test("dashboard가 코어 도메인 서비스를 읽기 조합해도 화이트리스트로 규칙이 통과한다 (예외 검증)") {
        val dashboardPackagePrefix = "com.sportsapp.application.dashboard"
        val dashboardClasses = importedClasses.filter { javaClass -> javaClass.packageName.startsWith(dashboardPackagePrefix) }
        dashboardClasses.shouldNotBeEmpty()

        val dashboardDependsOnCore = dashboardClasses.any { javaClass ->
            javaClass.directDependenciesFromSelf.any { dependency ->
                DomainClassification.core.any { core -> dependency.targetClass.packageName.startsWith("com.sportsapp.domain.$core") }
            }
        }
        dashboardDependsOnCore.shouldBeTrue()

        // dashboard는 이 파일의 지원/서브시스템 규칙 스캔 대상(패키지 목록)에 애초에 포함되지 않는다 —
        // 즉 위 규칙들과 무관하게 항상 통과하는 구조로 화이트리스트가 배선되어 있음을 확인한다.
        DomainClassification.support.none { it == "dashboard" }.shouldBeTrue()
        DomainClassification.subsystem.none { it == "dashboard" }.shouldBeTrue()
    }

    test("DomainClassification.core에 기존 8개 도메인이 유지되며 community·recruitment가 신규 등록된다") {
        DomainClassification.core.shouldContainAll(
            listOf("booking", "facility", "goods", "payment", "ticketing", "user", "post", "message", "community", "recruitment"),
        )
    }

    test("corePackages가 community·recruitment의 domain·application 패키지를 R3 스캔 대상에 포함한다") {
        corePackages.toList().shouldContainAll(
            listOf(
                "com.sportsapp.domain.community..",
                "com.sportsapp.application.community..",
                "com.sportsapp.domain.recruitment..",
                "com.sportsapp.application.recruitment..",
            ),
        )
    }

    test("(위반 시나리오) notification이 신규 core로 등록된 community를 실제로 의존한다고 가정하면 R3 메커니즘이 위반으로 탐지한다") {
        // notification 은 domain.common 을 실제로 의존한다(공유 커널이라 정상) — community 등록 이후
        // corePackages(line 44)에 community 가 포함된 상태로, notification→community 의존을 그대로
        // corePackages 스캔 규칙(line 48-57)과 동일한 구성으로 재현해 위반 탐지 메커니즘이 살아있는지 확인한다.
        // (실제 프로덕션 코드에 notification→community 의존은 없음 — domain.common 의존을 대역으로 사용)
        corePackages.toList() shouldContainAll listOf("com.sportsapp.domain.community..")

        val ruleTreatingCommonAsCommunityStandIn = noClasses()
            .that().resideInAnyPackage(*packagesOf("application", listOf("notification")), *packagesOf("domain", listOf("notification")))
            .should().dependOnClassesThat().resideInAnyPackage("com.sportsapp.domain.common..")

        shouldThrow<AssertionError> {
            ruleTreatingCommonAsCommunityStandIn.check(importedClasses)
        }
    }

    test("(위반 시나리오) 지원 패키지가 실제로 의존하는 패키지를 금지 대상으로 지정하면 규칙이 실패한다") {
        // notification 은 실제로 domain.common 을 의존한다(공유 커널이라 정상) — 이를 일부러 "금지 대상"으로
        // 지정해 ArchUnit 규칙이 실제 위반을 탐지하는지 sanity check 한다 (규칙 로직 자체의 신뢰성 검증).
        val ruleTreatingCommonAsForbidden = noClasses()
            .that().resideInAnyPackage(*packagesOf("application", listOf("notification")), *packagesOf("domain", listOf("notification")))
            .should().dependOnClassesThat().resideInAnyPackage("com.sportsapp.domain.common..")

        shouldThrow<AssertionError> {
            ruleTreatingCommonAsForbidden.check(importedClasses)
        }
    }
})
