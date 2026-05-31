package com.sportsapp.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Test

class PackageStructureRulesTest {

    private val importedClasses = ClassFileImporter()
        .importPackages("com.sportsapp")

    @Test
    fun `SportsApplication 은 com_sportsapp 루트 패키지에 위치한다`() {
        classes()
            .that().haveSimpleName("SportsApplication")
            .should().resideInAPackage("com.sportsapp")
            .check(importedClasses)
    }
}
