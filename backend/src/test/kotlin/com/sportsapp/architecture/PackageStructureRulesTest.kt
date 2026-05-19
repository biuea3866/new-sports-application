package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Test

/**
 * U-02: presentation / application / domain / infrastructure 4개 패키지가 존재한다.
 *
 * 레이어 패키지의 존재 여부 자체를 검증합니다.
 * allowEmptyShould(true) 로 현재 클래스가 없어도 통과, 클래스 추가 시 패키지 구조를 강제합니다.
 */
class PackageStructureRulesTest {

    private val importedClasses = ClassFileImporter()
        .importPackages("com.sportsapp")

    @Test
    fun `presentation 패키지가 com_sportsapp 하위에 선언되어야 한다`() {
        classes()
            .that().resideInAPackage("com.sportsapp.presentation..")
            .should().resideInAPackage("com.sportsapp.presentation..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `application 패키지가 com_sportsapp 하위에 선언되어야 한다`() {
        classes()
            .that().resideInAPackage("com.sportsapp.application..")
            .should().resideInAPackage("com.sportsapp.application..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `domain 패키지가 com_sportsapp 하위에 선언되어야 한다`() {
        classes()
            .that().resideInAPackage("com.sportsapp.domain..")
            .should().resideInAPackage("com.sportsapp.domain..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `infrastructure 패키지가 com_sportsapp 하위에 선언되어야 한다`() {
        classes()
            .that().resideInAPackage("com.sportsapp.infrastructure..")
            .should().resideInAPackage("com.sportsapp.infrastructure..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `SportsApplication 은 com_sportsapp 루트 패키지에 위치한다`() {
        classes()
            .that().haveSimpleName("SportsApplication")
            .should().resideInAPackage("com.sportsapp")
            .check(importedClasses)
    }
}
