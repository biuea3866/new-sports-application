package com.sportsapp.infrastructure.partner

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class ApiKeyGeneratorImplTest : BehaviorSpec({

    val apiKeyGenerator = ApiKeyGeneratorImpl(BCryptPasswordEncoder())

    Given("평문 키를 hash한다") {
        val plainKey = "plain-partner-key-value"
        val keyHash = apiKeyGenerator.hash(plainKey)

        When("동일한 평문으로 matches를 호출하면") {
            val matched = apiKeyGenerator.matches(plainKey, keyHash)

            Then("true를 반환한다") {
                matched.shouldBeTrue()
            }
        }

        When("다른 평문으로 matches를 호출하면") {
            val matched = apiKeyGenerator.matches("wrong-key-value", keyHash)

            Then("false를 반환한다") {
                matched.shouldBeFalse()
            }
        }
    }

    Given("generateRandomPart를 여러 번 호출한다") {
        val first = apiKeyGenerator.generateRandomPart()
        val second = apiKeyGenerator.generateRandomPart()

        Then("호출마다 다른 값을 생성한다") {
            first shouldNotBe second
        }
    }
})
