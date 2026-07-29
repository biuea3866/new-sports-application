package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Permission 공유커널→user 이관 회귀 스펙.
 * 근거: TDD 항목 2 / 방안 B.
 *
 * domain/common 이 더 이상 물리 테이블(Entity)을 소유하지 않고, mcp(subsystem)가
 * user(core)를 동기 의존하지 않음(R1·R3 회귀 방지)을 고정한다.
 */
class PermissionMigrationArchTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    test("domain.common 패키지에 @Entity/@Table 선언이 0건이다 (Permission 이관 완료 검증)") {
        val commonClasses = importedClasses
            .filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.domain.common") }

        // 양성 앵커 — 음성 단언만 두면 패키지명 오타·import 스코프 드리프트로 스캔 대상이 0이 돼도 통과한다.
        commonClasses.shouldNotBeEmpty()

        val commonEntityClasses = commonClasses
            .filter { javaClass -> javaClass.isAnnotatedWith(Entity::class.java) || javaClass.isAnnotatedWith(Table::class.java) }
            .map { javaClass -> javaClass.fullName }

        commonEntityClasses.shouldBeEmpty()
    }

    test("이관 도착지인 domain.user.entity.Permission 이 @Entity 로 존재한다 (이관 완료의 양성 단언)") {
        // "common 에 없다"만 단언하면 Permission 이 통째로 삭제돼도 통과한다. 도착지를 함께 고정한다.
        val permission = importedClasses
            .filter { javaClass -> javaClass.fullName == "com.sportsapp.domain.user.entity.Permission" }

        permission.shouldNotBeEmpty()
        permission.single().isAnnotatedWith(Entity::class.java).shouldBeTrue()
    }

    test("domain.mcp 패키지는 domain.user 패키지를 import하지 않는다 (R1·R3 회귀 방지)") {
        // allowEmptyShould(true) 를 붙이지 않는다. archunit.properties 가 없어 fail.on.empty.should
        // 기본값이 true 이므로, domain.mcp 가 리네임·소멸해 대상이 0건이 되면 룰이 **자기 술어 그대로**
        // 실패한다. 별도 양성 앵커를 두면 앵커(문자열 startsWith)와 룰(패키지 경계) 술어가 달라
        // domain.mcpserver 같은 접두사 보존 리네임에서 앵커만 통과하는 구멍이 생긴다.
        // 레포의 다른 allowEmptyShould(true) 는 '아직 없는 패키지'용 관례인데 domain.mcp 는 실재한다.
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain.mcp..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.domain.user..")
            .because("mcp(subsystem)는 user(core)를 동기 의존할 수 없다 — McpPermissionGateway ACL로 경유한다")
            .check(importedClasses)
    }
})
