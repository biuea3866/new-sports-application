package com.sportsapp.domain.common

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/**
 * `FeatureFlagEvaluator` 인터페이스 시그니처 검증.
 *
 * isEnabled(String, FeatureContext, Boolean): Boolean
 * variant(String, FeatureContext, String): String
 *
 * 소비 도메인(featuredemo 등 후속 티켓)이 이 시그니처를 안정적으로 사용할 수 있음을 보장한다.
 */
class FeatureFlagEvaluatorInterfaceTest : BehaviorSpec({

    Given("FeatureFlagEvaluator 인터페이스") {
        val cls = FeatureFlagEvaluator::class.java

        Then("isEnabled 메서드가 (String, FeatureContext, Boolean): Boolean 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "isEnabled" }) {
                "isEnabled method not found"
            }
            method.returnType shouldBe Boolean::class.javaPrimitiveType
            method.parameterTypes.toList() shouldBe listOf(
                String::class.java,
                FeatureContext::class.java,
                Boolean::class.javaPrimitiveType,
            )
        }

        Then("variant 메서드가 (String, FeatureContext, String): String 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "variant" }) {
                "variant method not found"
            }
            method.returnType shouldBe String::class.java
            method.parameterTypes.toList() shouldBe listOf(
                String::class.java,
                FeatureContext::class.java,
                String::class.java,
            )
        }

        Then("FeatureFlagEvaluator 는 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
