package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * U-01: domain 패키지는 infrastructure를 import하지 않는다.
 * allowEmptyShould(true) — 아직 구현 클래스가 없는 레이어 패키지에 대해서도 룰을 선언한다.
 * 향후 클래스가 추가되면 자동으로 위반을 잡는다.
 */
class LayerDependencyRulesTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    @Test
    fun `domain 패키지는 infrastructure 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.infrastructure..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `domain 패키지는 presentation 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.presentation..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `domain 패키지는 application 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.application..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `application 패키지는 infrastructure 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.application..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.infrastructure..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `application 패키지는 presentation 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.application..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.presentation..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `presentation 패키지는 infrastructure 패키지를 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.sportsapp.presentation..")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.infrastructure..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
