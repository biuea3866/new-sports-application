package com.sportsapp.domain.featureflag.gateway

import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/**
 * `FeatureFlagCacheStore` 인터페이스 시그니처 검증 — BE-03(Redis 캐시)이 구현할 계약을 동결한다.
 */
class FeatureFlagCacheStoreInterfaceTest : BehaviorSpec({

    Given("FeatureFlagCacheStore 인터페이스") {
        val cls = FeatureFlagCacheStore::class.java

        Then("put 메서드가 (FeatureFlagSnapshot): Unit 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "put" })
            method.parameterTypes.toList() shouldBe listOf(FeatureFlagSnapshot::class.java)
        }

        Then("get 메서드가 (String): FeatureFlagSnapshot? 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "get" })
            method.returnType shouldBe FeatureFlagSnapshot::class.java
            method.parameterTypes.toList() shouldBe listOf(String::class.java)
        }

        Then("evict 메서드가 (String): Unit 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "evict" })
            method.parameterTypes.toList() shouldBe listOf(String::class.java)
        }

        Then("FeatureFlagCacheStore 는 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
