package com.sportsapp.infrastructure.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

class JwtTokenProviderTest : BehaviorSpec({

    val testSecret = "test-secret-key-for-jwt-token-provider-unit-tests-at-least-32chars"
    val jwtTokenProvider = JwtTokenProvider(testSecret)

    Given("유효한 파라미터로 Access Token 생성 시") {
        When("generateAccessToken 을 호출하면") {
            val token = jwtTokenProvider.generateAccessToken(
                userId = 42L,
                email = "user@example.com",
                roles = listOf("USER", "ADMIN"),
            )

            Then("[U-02] 토큰이 빈 문자열이 아니다") {
                token.shouldNotBeBlank()
            }

            Then("[U-02] validateToken 이 true 를 반환한다") {
                jwtTokenProvider.validateToken(token) shouldBe true
            }

            Then("[U-02] extractUserId 가 올바른 userId 를 반환한다") {
                jwtTokenProvider.extractUserId(token) shouldBe 42L
            }

            Then("[U-02] extractEmail 이 올바른 email 을 반환한다") {
                jwtTokenProvider.extractEmail(token) shouldBe "user@example.com"
            }

            Then("[U-02] extractRoles 가 올바른 roles 를 반환한다") {
                jwtTokenProvider.extractRoles(token) shouldBe listOf("USER", "ADMIN")
            }
        }
    }

    Given("위조된 토큰") {
        When("validateToken 을 호출하면") {
            Then("[U-02] false 를 반환한다") {
                jwtTokenProvider.validateToken("forged.token.here") shouldBe false
            }
        }
    }

    Given("빈 토큰 문자열") {
        When("validateToken 을 호출하면") {
            Then("[U-02] false 를 반환한다") {
                jwtTokenProvider.validateToken("") shouldBe false
            }
        }
    }

    Given("Refresh Token 생성 시") {
        When("generateRefreshToken 을 두 번 호출하면") {
            val token1 = jwtTokenProvider.generateRefreshToken()
            val token2 = jwtTokenProvider.generateRefreshToken()

            Then("두 토큰은 서로 다른 UUID 문자열이다") {
                token1.shouldNotBeBlank()
                token2.shouldNotBeBlank()
                (token1 == token2) shouldBe false
            }
        }
    }

    Given("accessTokenExpiresInSeconds 조회 시") {
        Then("[U-02] 1800 (30분) 을 반환한다") {
            jwtTokenProvider.accessTokenExpiresInSeconds() shouldBe 1800L
        }
    }
})
