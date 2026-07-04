package com.sportsapp.domain.featureflag.repository

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/**
 * `FeatureFlagRepository` 인터페이스 시그니처 검증 — BE-02(영속화)가 구현할 계약을 동결한다.
 */
class FeatureFlagRepositoryInterfaceTest : BehaviorSpec({

    Given("FeatureFlagRepository 인터페이스") {
        val cls = FeatureFlagRepository::class.java

        Then("save 메서드가 (FeatureFlag): FeatureFlag 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "save" })
            method.returnType shouldBe FeatureFlag::class.java
            method.parameterTypes.toList() shouldBe listOf(FeatureFlag::class.java)
        }

        Then("findByKey 메서드가 (String): FeatureFlag? 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "findByKey" })
            method.returnType shouldBe FeatureFlag::class.java
            method.parameterTypes.toList() shouldBe listOf(String::class.java)
        }

        Then("findById 메서드가 (Long): FeatureFlag? 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "findById" })
            method.returnType shouldBe FeatureFlag::class.java
            method.parameterTypes.toList() shouldBe listOf(Long::class.javaPrimitiveType)
        }

        Then("findAllActive 메서드가 (): List 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "findAllActive" })
            method.returnType shouldBe List::class.java
            method.parameterTypes.toList() shouldBe emptyList()
        }

        Then("findAll 메서드가 (FeatureFlagStatus?, FeatureFlagType?): List 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "findAll" })
            method.returnType shouldBe List::class.java
            method.parameterTypes.toList() shouldBe listOf(FeatureFlagStatus::class.java, FeatureFlagType::class.java)
        }

        Then("existsByKey 메서드가 (String): Boolean 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "existsByKey" })
            method.returnType shouldBe Boolean::class.javaPrimitiveType
            method.parameterTypes.toList() shouldBe listOf(String::class.java)
        }

        Then("FeatureFlagRepository 는 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
