package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * TDD "컨텍스트 맵 — 5개 분류 확정" 기준 도메인 분류 상수.
 *
 * FR-8 컨텍스트 맵 갱신 절차: 신규 도메인 추가 시 이 목록에 분류를 반영한다.
 * 패키지 prefix 스캔이라 목록에 없어도 자동 커버되지만, 상수 명시로 분류 누락을 방지한다.
 */
object DomainClassification {
    val core = listOf("booking", "facility", "goods", "payment", "ticketing", "user", "post", "message", "community", "recruitment")
    val support = listOf(
        "notification", "operator", "weather", "alerting",
        "featureflag", "virtualqueue", "partner", "airquality", "featuredemo",
        // W1-06b: edge 가 소유한 신원 검증 계약(PlatformMcp/PartnerIdentityVerificationGateway).
        // 코어 도메인을 import 하지 않고 common 만 의존하므로 support 분류가 맞다 — virtualqueue 와 동일하게
        // edge 소유이며, 검증 판정 자체는 platform 이 수행하고 edge 는 계약만 가진다.
        "identity",
        // S2-01: edge 가 소유한 파사드 읽기 계약(CatalogSearchGateway·OrderHistoryGateway)과 그 값 객체.
        // 반환 타입이 전부 edge 소유라 코어 도메인을 import 하지 않는다 — identity 와 같은 성격이며,
        // R3 스캔에 편입돼 "edge 계약이 코어를 동기 의존하지 않는다"를 상시 강제한다.
        // 구현체(로컬 어댑터)는 조립자(bootstrap) 의 infrastructure 소유라 이 축의 대상이 아니다.
        "catalog", "order",
    )
    val subsystem = listOf("mcp")

    /**
     * R3 일반 스캔에서 제외하고 전용 화이트리스트 테스트가 담당하는 도메인 (ADR-002 rule #4 예외 ②).
     * partner → domain.user 는 B2B admin 프로비저닝 쓰기 오케스트레이션으로 사전 등록된 화이트리스트이며,
     * 전용 테스트("application.partner 는 domain.user 를 제외한 코어 도메인을 동기 의존하지 않는다")가 담당한다.
     *
     * featureflag → domain.user 도 같은 카브아웃이다 — `GetFeatureFlagAuditLogsUseCase` 가 감사 로그의
     * 변경자를 내부 PK 대신 표시 이름으로 보이려고 `UserDomainService.findDisplayNamesBy` 를 읽는다
     * (PR #393). 근거: ① 의존이 **`domain.user` 단 하나**이고 읽기 전용 조합이다(dashboard 예외 ①과 동일
     * 성격), ② featureflag 와 user 는 2단계 목표 토폴로지에서 **같은 서비스(`platform`)** 에 속해 이
     * 동기 호출이 서비스 간 결합을 만들지 않는다, ③ 코드 자체는 "크로스 컨텍스트 조합은 application
     * 레이어 책임" 컨벤션을 따른다. 대안(변경자 표시 이름을 감사 행에 비정규화)은 마이그레이션·백필을
     * 수반하는데 R3 의 목적(서비스 간 동기 결합 차단)에는 기여하지 않아 채택하지 않았다.
     * 전용 테스트가 "user 를 제외한 코어 접근"을 여전히 금지한다.
     */
    val r3Whitelisted = listOf("partner", "featureflag")
}

private fun packagesOf(layer: String, domains: List<String>): Array<String> =
    domains.map { domain -> "com.sportsapp.$layer.$domain.." }.toTypedArray()

/**
 * [지원·서브시스템 → 코어 동기 의존 금지 규칙 (R3)]
 * 근거: TDD "FR-7 정적 검증 방법" R3 / ADR-005.
 *
 * 지원·서브시스템 도메인이 코어 도메인
 * (booking/facility/goods/payment/ticketing/user/post/message/community/recruitment)을 동기 의존(import)하지 않음을 강제한다.
 *
 * 스캔 대상은 `DomainClassification.support + subsystem - r3Whitelisted` 로 상수에서 직접 구동한다 —
 * 상수에 도메인을 추가하면 스캔 대상이 자동으로 늘어난다 (FR-8 컨텍스트 맵 갱신 절차의 실효성 보장).
 *
 * 화이트리스트 예외 3건 — 모두 일반 R3 루프의 스캔 대상이 아니며, 각자 전용 테스트가 담당한다:
 *  ① application.dashboard → 코어 (읽기 전용 조합, Conformist read model).
 *     dashboard 는 support/subsystem 어디에도 속하지 않아 애초에 루프에 들어오지 않는다.
 *  ② application.partner → domain.user (B2B admin 프로비저닝 쓰기 오케스트레이션, ADR-002 rule #4).
 *     partner 는 support 이지만 r3Whitelisted 로 루프에서 제외하고, 아래 전용 규칙이 "user 를 제외한
 *     코어 접근"만 금지한다. 그 전용 규칙의 거부 능력은 stand-in sanity check 로 고정한다.
 *  ③ application.featureflag → domain.user (감사 로그 변경자 표시 이름 읽기 조합, PR #393).
 *     ②와 동일한 처리 — 근거는 [DomainClassification.r3Whitelisted] KDoc 참고.
 */
class SupportToCoreDependencyRulesTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    val corePackages = packagesOf("domain", DomainClassification.core) + packagesOf("application", DomainClassification.core)
    val corePackagesExceptUser =
        packagesOf("domain", DomainClassification.core - "user") + packagesOf("application", DomainClassification.core - "user")

    val r3ScannedDomains = (DomainClassification.support + DomainClassification.subsystem) - DomainClassification.r3Whitelisted.toSet()

    r3ScannedDomains.forEach { domain ->
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
        // application.partner는 CreatePartnerUseCase 등에서 domain.user.service.UserDomainService를 실제로 의존한다
        // (admin 프로비저닝 쓰기 오케스트레이션, ADR-002 rule #4 사전 등록 예외) — user를 제외한 코어 접근만 금지 대상이다.
        noClasses()
            .that().resideInAnyPackage("com.sportsapp.application.partner..")
            .should().dependOnClassesThat().resideInAnyPackage(*corePackagesExceptUser)
            .because("partner→user 는 admin 프로비저닝 쓰기 오케스트레이션으로 사전 등록된 예외 — user 외 코어 접근은 여전히 금지")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("application.featureflag 는 domain.user 를 제외한 코어 도메인을 동기 의존하지 않는다 (사전 등록 화이트리스트)") {
        // GetFeatureFlagAuditLogsUseCase 가 감사 로그 변경자를 표시 이름으로 보이려고
        // domain.user.service.UserDomainService 를 읽기 조합한다 — user 를 제외한 코어 접근만 금지 대상이다.
        noClasses()
            .that().resideInAnyPackage("com.sportsapp.application.featureflag..", "com.sportsapp.domain.featureflag..")
            .should().dependOnClassesThat().resideInAnyPackage(*corePackagesExceptUser)
            .because("featureflag→user 는 감사 로그 표시 이름 읽기 조합으로 사전 등록된 예외 — user 외 코어 접근은 여전히 금지")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("domain.featureflag 는 domain.user 조차 의존하지 않는다 (조합은 application 레이어에만 허용)") {
        // 화이트리스트를 domain 까지 열어 주지 않는다 — 도메인은 user 를 모른 채로 둔다.
        noClasses()
            .that().resideInAnyPackage("com.sportsapp.domain.featureflag..")
            .should().dependOnClassesThat().resideInAnyPackage(*corePackages)
            .because("featureflag 도메인은 user 를 모른다 — 표시 이름 조합은 application UseCase 의 책임")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    test("(위반 시나리오) application.featureflag 가 실제로 의존하는 패키지를 금지 대상으로 지정하면 화이트리스트 규칙이 위반으로 탐지한다") {
        // featureflag 도 r3Whitelisted 로 일반 R3 루프에서 빠졌으므로, 위 두 전용 규칙이 유일한
        // 방어선이다. 두 규칙 모두 allowEmptyShould(true) 라, application.featureflag 가 이동·리네임되면
        // 공허 통과하고 방어가 0 이 된다 — partner 와 같은 사유로 거부 능력을 고정한다.
        importedClasses
            .filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.application.featureflag") }
            .shouldNotBeEmpty()

        val ruleTreatingOwnDomainAsForbidden = noClasses()
            .that().resideInAnyPackage("com.sportsapp.application.featureflag..")
            .should().dependOnClassesThat().resideInAnyPackage("com.sportsapp.domain.featureflag..")

        shouldThrow<AssertionError> {
            ruleTreatingOwnDomainAsForbidden.check(importedClasses)
        }
    }

    test("(위반 시나리오) application.partner 가 실제로 의존하는 패키지를 금지 대상으로 지정하면 화이트리스트 규칙이 위반으로 탐지한다") {
        // partner 가 r3Whitelisted 로 일반 R3 루프에서 빠지면서, 위 "user 외 코어 접근 금지" 규칙이
        // partner 의 유일한 방어선이 됐다. 그 규칙이 실제로 실패할 수 있는지(거부 능력)를 고정한다.
        // application.partner 가 실제로 의존하는 domain.partner 를 금지 대상 대역으로 지정한다.
        //
        // 선행 단언이 필요한 이유: ArchUnit 기본값이 failOnEmptyShould=true 라, 검사 대상이 0개여도
        // AssertionError 가 난다. 선행 단언 없이 shouldThrow 만 쓰면 "위반을 탐지했다"와 "검사 대상이
        // 사라졌다"를 구분하지 못해, application.partner 가 이동·리네임돼도 그대로 통과한다.
        importedClasses
            .filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.application.partner") }
            .shouldNotBeEmpty()

        val ruleTreatingOwnDomainAsForbidden = noClasses()
            .that().resideInAnyPackage("com.sportsapp.application.partner..")
            .should().dependOnClassesThat().resideInAnyPackage("com.sportsapp.domain.partner..")

        shouldThrow<AssertionError> {
            ruleTreatingOwnDomainAsForbidden.check(importedClasses)
        }
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

    test("core + support + subsystem 분류 집합이 domain/ 하위 실제 패키지 집합(common 제외)과 정확히 일치한다 (domain 축 커버리지 항등식)") {
        // domain/ 바로 하위 서브패키지 이름을 ArchUnit importedClasses 에서 직접 도출한다 (파일시스템 스캔 대신
        // 임포트된 클래스 기준으로 도출해야, 컴파일 대상에서 벗어난 죽은 디렉토리가 항등식을 왜곡하지 않는다).
        val actualDomainSubPackages = importedClasses
            .map { javaClass -> javaClass.packageName }
            .filter { packageName -> packageName.startsWith("com.sportsapp.domain.") }
            .mapNotNull { packageName -> packageName.removePrefix("com.sportsapp.domain.").substringBefore(".").ifBlank { null } }
            .toSet()
            .minus("common")

        val classifiedDomains = (DomainClassification.core + DomainClassification.support + DomainClassification.subsystem).toSet()

        classifiedDomains shouldBe actualDomainSubPackages
    }

    test("분류 합계가 23이다") {
        (DomainClassification.core.size + DomainClassification.support.size + DomainClassification.subsystem.size) shouldBe 23
    }

    test("R3 스캔 대상에 기존 5개와 신규 편입 3개를 합한 8개 도메인이 모두 포함된다") {
        // featureflag 는 domain.user 읽기 조합(PR #393)으로 r3Whitelisted 로 이동해 전용 테스트가 담당한다.
        r3ScannedDomains.shouldContainAll(
            listOf("notification", "operator", "weather", "mcp", "alerting", "virtualqueue", "airquality", "featuredemo"),
        )
    }

    test("partner·featureflag는 r3Whitelisted로 일반 R3 루프의 스캔 대상에서 제외된다 (전용 화이트리스트 테스트가 담당)") {
        r3ScannedDomains.shouldNotContain("partner")
        r3ScannedDomains.shouldNotContain("featureflag")
        DomainClassification.r3Whitelisted.shouldContainAll(listOf("partner", "featureflag"))
    }
})
