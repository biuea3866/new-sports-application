package com.sportsapp.domain.featuredemo

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * BE-09: featuredemo는 domain.featureflag를 import하지 않고 common.FeatureFlagEvaluator만으로
 * 게이팅을 수행해야 한다 (도메인 격리·FR-12 보일러플레이트 없음의 정적 증명).
 */
class FeatureDemoDomainIsolationTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp")

    test("featuredemo 소스는 domain.featureflag를 import하지 않는다") {
        noClasses()
            .that().resideInAnyPackage(
                "com.sportsapp.domain.featuredemo..",
                "com.sportsapp.application.featuredemo..",
                "com.sportsapp.presentation.featuredemo..",
            )
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp.domain.featureflag..")
            .because("featuredemo는 common.FeatureFlagEvaluator·FeatureContext만 주입해 도메인 격리를 증명한다")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
})
