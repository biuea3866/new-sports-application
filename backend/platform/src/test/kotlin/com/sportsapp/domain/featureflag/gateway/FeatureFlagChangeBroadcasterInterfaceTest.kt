package com.sportsapp.domain.featureflag.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/**
 * `FeatureFlagChangeBroadcaster` 인터페이스 시그니처 검증 — BE-03(Redis pub/sub)이 구현할 계약을 동결한다.
 */
class FeatureFlagChangeBroadcasterInterfaceTest : BehaviorSpec({

    Given("FeatureFlagChangeBroadcaster 인터페이스") {
        val cls = FeatureFlagChangeBroadcaster::class.java

        Then("broadcast 메서드가 (String): Unit 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "broadcast" })
            method.parameterTypes.toList() shouldBe listOf(String::class.java)
        }

        Then("FeatureFlagChangeBroadcaster 는 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
