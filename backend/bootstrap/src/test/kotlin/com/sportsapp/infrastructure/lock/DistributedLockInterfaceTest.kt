package com.sportsapp.infrastructure.lock

import com.sportsapp.domain.common.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.lang.reflect.Modifier
import java.time.Duration

/**
 * U-01: `DistributedLock` 인터페이스 시그니처 검증.
 *
 * tryLock(String, String, Duration) -> Boolean
 * unlock(String, String) -> Boolean
 *
 * 후속 도메인 티켓(BOOKING-03 / TICKETING-04)이 이 시그니처를 안정적으로 사용할 수 있음을 보장한다.
 */
class DistributedLockInterfaceTest : BehaviorSpec({

    Given("DistributedLock 인터페이스") {
        val cls = DistributedLock::class.java

        Then("[U-01] tryLock 메서드가 (String, String, Duration): Boolean 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "tryLock" }) {
                "tryLock method not found"
            }
            method.returnType shouldBe Boolean::class.javaPrimitiveType
            method.parameterTypes.toList() shouldBe listOf(
                String::class.java,
                String::class.java,
                Duration::class.java,
            )
        }

        Then("[U-01b] unlock 메서드가 (String, String): Boolean 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "unlock" }) {
                "unlock method not found"
            }
            method.returnType shouldBe Boolean::class.javaPrimitiveType
            method.parameterTypes.toList() shouldBe listOf(String::class.java, String::class.java)
        }

        Then("[U-01c] DistributedLock 은 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
