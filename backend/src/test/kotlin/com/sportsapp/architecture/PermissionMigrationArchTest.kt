package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * [PH0-05] Permission 공유커널→user 이관 회귀 스펙.
 * 근거: PH0-05 티켓 "반드시 지킬 제약", TDD 항목 2 / 방안 B.
 *
 * domain/common 이 더 이상 물리 테이블(Entity)을 소유하지 않고, mcp(subsystem)가
 * user(core)를 동기 의존하지 않음(R1·R3 회귀 방지)을 고정한다.
 */
class PermissionMigrationArchTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    test("domain.common 패키지에 @Entity/@Table 선언이 0건이다 (Permission 이관 완료 검증)") {
        val commonEntityClasses = importedClasses
            .filter { javaClass -> javaClass.packageName.startsWith("com.sportsapp.domain.common") }
            .filter { javaClass -> javaClass.isAnnotatedWith(Entity::class.java) || javaClass.isAnnotatedWith(Table::class.java) }
            .map { javaClass -> javaClass.fullName }

        commonEntityClasses.shouldBeEmpty()
    }

    test("domain.mcp 패키지는 domain.user 패키지를 import하지 않는다 (R1·R3 회귀 방지)") {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain.mcp..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.domain.user..")
            .because("mcp(subsystem)는 user(core)를 동기 의존할 수 없다 — McpPermissionGateway ACL로 경유한다")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
})
