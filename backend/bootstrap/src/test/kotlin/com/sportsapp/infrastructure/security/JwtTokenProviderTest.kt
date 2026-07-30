package com.sportsapp.infrastructure.security

import io.jsonwebtoken.Jwts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.mock.env.MockEnvironment
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant // private-allow:no-instant — JwtIssuer 계약(Instant) 및 JJWT Date API 정합 테스트, 인터페이스 변경은 범위 밖
import java.util.Base64
import java.util.Date

/**
 * W1-05: JWT RS256 전환 회귀·신규 테스트.
 *
 * 키는 매 실행마다 [KeyPairGenerator] 로 생성한다 — 고정 키 파일을 저장소에 두지 않기 위함이다
 * (티켓 "로컬 개발용 기본 키를 커밋하지 않는다").
 */
class JwtTokenProviderTest : BehaviorSpec({

    val hs256Secret = "test-secret-key-for-jwt-token-provider-unit-tests-at-least-32chars"

    fun generateRsaPemPair(): Pair<String, String> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(keyPair.public.encoded) +
            "\n-----END PUBLIC KEY-----"
        val privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(keyPair.private.encoded) +
            "\n-----END PRIVATE KEY-----"
        return publicKeyPem to privateKeyPem
    }

    fun decodePrivateKeyFromPem(privateKeyPem: String) =
        KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(
                    privateKeyPem
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\n", ""),
                ),
            ),
        )

    fun jwtHeaderAlg(token: String): String {
        val headerJson = String(Base64.getUrlDecoder().decode(token.substringBefore(".")))
        return Regex("\"alg\"\\s*:\\s*\"([^\"]+)\"").find(headerJson)?.groupValues?.get(1)
            ?: error("alg header not found in $headerJson")
    }

    fun environmentWithAlgorithm(algorithm: String): MockEnvironment =
        MockEnvironment().withProperty("app.jwt.algorithm", algorithm)

    Given("사설키·공개키가 모두 주입되고 발급 알고리즘이 RS256인 platform 인스턴스") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("generateAccessToken 으로 토큰을 발급하면") {
            val token = provider.generateAccessToken(userId = 42L, email = "user@example.com", roles = listOf("USER", "ADMIN"))

            Then("RS256 으로 서명된 토큰이 생성된다") {
                jwtHeaderAlg(token) shouldBe "RS256"
            }

            Then("사설키로 발급한 RS256 토큰이 공개키로 검증된다") {
                provider.validateToken(token) shouldBe true
            }

            Then("토큰 claim 구조(sub·roles·만료)가 기존과 동일하다 — 클라이언트 무영향") {
                provider.extractUserId(token) shouldBe 42L
                provider.extractEmail(token) shouldBe "user@example.com"
                provider.extractRoles(token) shouldBe listOf("USER", "ADMIN")
                provider.extractJti(token).shouldNotBeBlank()
                provider.extractExpiration(token).isAfter(Instant.now()) shouldBe true
            }

            Then("accessTokenExpiresInSeconds 는 기존과 동일하게 1800(30분)을 반환한다") {
                provider.accessTokenExpiresInSeconds() shouldBe 1800L
            }
        }
    }

    Given("공개키만 주입되고 사설키가 없는 인스턴스 (platform 외 서비스 상태)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val platformProvider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))
        val publicOnlyProvider = JwtTokenProvider(hs256Secret, publicKeyPem, "", environmentWithAlgorithm("RS256"))

        When("platform 이 발급한 RS256 토큰을 검증하면") {
            val tokenFromPlatform = platformProvider.generateAccessToken(1L, "a@example.com", listOf("USER"))

            Then("공개키만 주입된 인스턴스도 검증에 성공한다") {
                publicOnlyProvider.validateToken(tokenFromPlatform) shouldBe true
            }
        }

        When("이 인스턴스에서 발급을 시도하면") {
            Then("사설키가 없으므로 발급이 거부된다") {
                shouldThrow<IllegalStateException> {
                    publicOnlyProvider.generateAccessToken(1L, "a@example.com", listOf("USER"))
                }
            }
        }
    }

    Given("전환기 설정 (검증은 HS256+RS256 이중 지원)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val hs256IssuingProvider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("HS256"))
        val rs256IssuingProvider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("기존 HS256 토큰과 신규 RS256 토큰이 함께 존재하면") {
            val legacyHs256Token = hs256IssuingProvider.generateAccessToken(7L, "legacy@example.com", listOf("USER"))
            val newRs256Token = rs256IssuingProvider.generateAccessToken(7L, "new@example.com", listOf("USER"))

            Then("기존 HS256 토큰과 신규 RS256 토큰이 모두 검증된다 — 무중단 전환 핵심") {
                jwtHeaderAlg(legacyHs256Token) shouldBe "HS256"
                jwtHeaderAlg(newRs256Token) shouldBe "RS256"
                hs256IssuingProvider.validateToken(legacyHs256Token) shouldBe true
                hs256IssuingProvider.validateToken(newRs256Token) shouldBe true
            }
        }
    }

    Given("런타임 설정값으로 발급 알고리즘을 전환할 수 있는 단일 인스턴스") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val environment = environmentWithAlgorithm("RS256")
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environment)

        When("APP_JWT_ALGORITHM 을 RS256 으로 발급 후, HS256 으로 되돌리면 (재기동 없이)") {
            val rs256Token = provider.generateAccessToken(9L, "rollback@example.com", listOf("USER"))
            environment.setProperty("app.jwt.algorithm", "HS256")
            val hs256TokenAfterRollback = provider.generateAccessToken(9L, "rollback@example.com", listOf("USER"))

            Then("전환 전 발급은 RS256, 롤백 후 발급은 HS256 이다 — 같은 인스턴스가 재기동 없이 즉시 반영한다") {
                jwtHeaderAlg(rs256Token) shouldBe "RS256"
                jwtHeaderAlg(hs256TokenAfterRollback) shouldBe "HS256"
            }
        }
    }

    Given("다른 키페어로 서명된 토큰") {
        val (publicKeyPem, _) = generateRsaPemPair()
        val foreignKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, "", environmentWithAlgorithm("RS256"))
        val forgedToken = Jwts.builder()
            .subject("999")
            .id("forged-jti")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(foreignKeyPair.private)
            .compact()

        When("우리 공개키로 검증하면") {
            Then("검증에 실패한다 — 실패 경로") {
                provider.validateToken(forgedToken) shouldBe false
            }
        }
    }

    Given("만료된 토큰") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("만료 시각이 지난 RS256 토큰을 검증하면") {
            // provider 가 실제로 소유한 사설키로 서명하되 만료 시각만 과거로 강제한다
            val privateKey = decodePrivateKeyFromPem(privateKeyPem)
            val expiredToken = Jwts.builder()
                .subject("5")
                .id("expired-jti")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(privateKey)
                .compact()

            Then("검증에 실패한다 — 기존 계약 보존") {
                provider.validateToken(expiredToken) shouldBe false
            }
        }
    }

    Given("공개키가 주입되지 않은 상태") {
        When("JwtTokenProvider 를 생성하면") {
            Then("키가 주입되지 않으면 부팅이 실패한다 — 조용한 fallback 금지") {
                shouldThrow<IllegalStateException> {
                    JwtTokenProvider(hs256Secret, "", "", environmentWithAlgorithm("HS256"))
                }
            }
        }
    }

    Given("정상적으로 구성된 인스턴스") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("toString 을 호출하거나 잘못된 발급을 시도하면") {
            val exceptionMessage = shouldThrow<IllegalStateException> {
                JwtTokenProvider(hs256Secret, publicKeyPem, "", environmentWithAlgorithm("RS256"))
                    .generateAccessToken(1L, "a@example.com", listOf("USER"))
            }.message.orEmpty()

            Then("키 값이 로그·예외 메시지에 출력되지 않는다 — 보안") {
                provider.toString().contains(hs256Secret) shouldBe false
                provider.toString().contains(privateKeyPem) shouldBe false
                provider.toString().contains(publicKeyPem) shouldBe false
                exceptionMessage.contains(privateKeyPem) shouldBe false
                exceptionMessage.contains(publicKeyPem) shouldBe false
                exceptionMessage.contains(hs256Secret) shouldBe false
            }
        }
    }

    Given("빈 토큰 문자열") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("validateToken 을 호출하면") {
            Then("false 를 반환한다") {
                provider.validateToken("") shouldBe false
            }
        }
    }

    Given("Refresh Token 생성 시") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hs256Secret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("generateRefreshToken 을 두 번 호출하면") {
            val token1 = provider.generateRefreshToken()
            val token2 = provider.generateRefreshToken()

            Then("두 토큰은 서로 다른 UUID 문자열이다") {
                token1.shouldNotBeBlank()
                token2.shouldNotBeBlank()
                (token1 == token2) shouldBe false
            }
        }
    }
})
