package com.sportsapp.infrastructure.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.mock.env.MockEnvironment
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date

/**
 * W1-05: JWT RS256 전환 회귀·신규 테스트.
 *
 * 키는 매 실행마다 [KeyPairGenerator] 로 생성한다 — 고정 키 파일을 저장소에 두지 않기 위함이다
 * (티켓 "로컬 개발용 기본 키를 커밋하지 않는다").
 */
class JwtTokenProviderTest : BehaviorSpec({

    val hmacSecret = "test-secret-key-for-jwt-token-provider-unit-tests-at-least-32chars"

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
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

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
                provider.extractExpiration(token).isAfter(ZonedDateTime.now(ZoneOffset.UTC)) shouldBe true
            }

            Then("accessTokenExpiresInSeconds 는 기존과 동일하게 1800(30분)을 반환한다") {
                provider.accessTokenExpiresInSeconds() shouldBe 1800L
            }
        }
    }

    Given("공개키만 주입되고 사설키가 없는 인스턴스 (platform 외 서비스의 실제 배포 상태 — algorithm=HS256)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val platformProvider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))
        // platform 외 서비스는 발급하지 않으므로(검증 전용) algorithm은 기본값 HS256 그대로 둔다 —
        // RS256을 발급하지 않을 인스턴스에 algorithm=RS256을 주는 것은 그 자체로 오설정이며,
        // 그 경우(부팅 시점 거부)는 별도 Given("사설키 없이 algorithm=RS256로 잘못 구성된 인스턴스")에서 다룬다.
        val environment = environmentWithAlgorithm("HS256")
        val publicOnlyProvider = JwtTokenProvider(hmacSecret, publicKeyPem, "", environment)

        When("platform 이 발급한 RS256 토큰을 검증하면") {
            val tokenFromPlatform = platformProvider.generateAccessToken(1L, "a@example.com", listOf("USER"))

            Then("공개키만 주입된 인스턴스도 검증에 성공한다") {
                publicOnlyProvider.validateToken(tokenFromPlatform) shouldBe true
            }
        }

        When("부팅 이후 이 인스턴스의 algorithm 설정이 (오설정으로) RS256으로 바뀐 상태에서 발급을 시도하면") {
            // 실제 운영에서 algorithm은 컨테이너 재기동 전엔 바뀌지 않는다(JwtTokenProvider KDoc) —
            // 이 테스트는 "생성 시점엔 정상이었지만 그 이후 호출 시점에 사설키가 여전히 없는" 방어
            // 분기(requirePrivateKeyForIssuance)를 MockEnvironment로 인위적으로 재현한 것이다.
            environment.setProperty("app.jwt.algorithm", "RS256")

            Then("사설키가 없으므로 발급이 거부된다") {
                shouldThrow<IllegalStateException> {
                    publicOnlyProvider.generateAccessToken(1L, "a@example.com", listOf("USER"))
                }
            }
        }
    }

    Given("사설키 없이 algorithm=RS256로 잘못 구성된 인스턴스 (오설정)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()

        When("JwtTokenProvider 를 생성하면") {
            val exceptionMessage = shouldThrow<IllegalStateException> {
                JwtTokenProvider(hmacSecret, publicKeyPem, "", environmentWithAlgorithm("RS256"))
            }.message.orEmpty()

            Then("부팅 시점에 즉시 실패한다 — 조용히 HS256으로 계속 발급하는 사고 방지") {
                exceptionMessage.shouldNotBeBlank()
            }

            Then("예외 메시지에 시크릿·키 값이 그대로 노출되지 않는다 — 보안") {
                exceptionMessage.contains(privateKeyPem) shouldBe false
                exceptionMessage.contains(publicKeyPem) shouldBe false
                exceptionMessage.contains(hmacSecret) shouldBe false
            }
        }
    }

    Given("전환기 설정 (검증은 HS256+RS256 이중 지원)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val hs256IssuingProvider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("HS256"))
        val rs256IssuingProvider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

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

    Given("동일 인스턴스가 발급 시점마다 app.jwt.algorithm 값을 다시 읽는지 (캐싱 여부 검증)") {
        // 주의: 이 테스트는 provider가 알고리즘 값을 필드에 캐싱하지 않고 매 호출 시 Environment를
        // 다시 조회한다는 코드 수준 사실만 검증한다 — "운영에서 재기동 없이 전환 가능하다"는 뜻이
        // 아니다. MockEnvironment는 테스트 편의를 위한 가변 구현일 뿐이며, 실제 운영(docker compose,
        // config server·@RefreshScope 미도입)에서 APP_JWT_ALGORITHM 값은 컨테이너 재기동 시에만
        // 바뀐다(JwtTokenProvider KDoc 참고). 이 값이 프로세스 도중 바뀌는 것은 이 테스트에서만
        // 성립하는 인공적 상황이다.
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val environment = environmentWithAlgorithm("RS256")
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environment)

        When("설정값을 (테스트 안에서만) RS256 → HS256 으로 바꾸고 각각 발급하면") {
            val rs256Token = provider.generateAccessToken(9L, "rollback@example.com", listOf("USER"))
            environment.setProperty("app.jwt.algorithm", "HS256")
            val hs256TokenAfterChange = provider.generateAccessToken(9L, "rollback@example.com", listOf("USER"))

            Then("provider는 값을 캐싱하지 않고 매 호출 시 Environment를 다시 읽는다") {
                jwtHeaderAlg(rs256Token) shouldBe "RS256"
                jwtHeaderAlg(hs256TokenAfterChange) shouldBe "HS256"
            }
        }
    }

    Given("다른 키페어로 서명된 토큰") {
        val (publicKeyPem, _) = generateRsaPemPair()
        val foreignKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        // 검증 전용 인스턴스(사설키 없음) — algorithm은 발급하지 않는 인스턴스의 기본값 HS256을 쓴다.
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, "", environmentWithAlgorithm("HS256"))
        val forgedToken = Jwts.builder()
            .subject("999")
            .id("forged-jti")
            .issuedAt(Date.from(ZonedDateTime.now(ZoneOffset.UTC).toInstant()))
            .expiration(Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(600).toInstant()))
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
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("만료 시각이 지난 RS256 토큰을 검증하면") {
            // provider 가 실제로 소유한 사설키로 서명하되 만료 시각만 과거로 강제한다
            val privateKey = decodePrivateKeyFromPem(privateKeyPem)
            val expiredToken = Jwts.builder()
                .subject("5")
                .id("expired-jti")
                .issuedAt(Date.from(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(3600).toInstant()))
                .expiration(Date.from(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(10).toInstant()))
                .signWith(privateKey)
                .compact()

            Then("검증에 실패한다 — 기존 계약 보존") {
                provider.validateToken(expiredToken) shouldBe false
            }
        }
    }

    Given("공개키·사설키가 모두 없는 인스턴스 (RSA 키 프로비저닝 이전의 1단계 배포 — 정상 상태)") {
        When("JwtTokenProvider 를 생성하면") {
            Then("부팅이 성공한다 — 키 미주입이 곧 부팅 실패가 아니다") {
                shouldNotThrowAny {
                    JwtTokenProvider(hmacSecret, "", "", environmentWithAlgorithm("HS256"))
                }
            }
        }

        val provider = JwtTokenProvider(hmacSecret, "", "", environmentWithAlgorithm("HS256"))

        When("HS256 으로 토큰을 발급하고 검증하면") {
            val token = provider.generateAccessToken(3L, "hs-only@example.com", listOf("USER"))

            Then("정상적으로 발급·검증된다 — RS256 미구성이 HS256 경로에 영향을 주지 않는다") {
                jwtHeaderAlg(token) shouldBe "HS256"
                provider.validateToken(token) shouldBe true
            }
        }

        When("(외부에서) RS256 으로 서명된 토큰을 이 인스턴스가 검증하면") {
            val (_, foreignPrivateKeyPem) = generateRsaPemPair()
            val foreignPrivateKey = decodePrivateKeyFromPem(foreignPrivateKeyPem)
            val rs256SignedToken = Jwts.builder()
                .subject("4")
                .id("rs256-while-unconfigured-jti")
                .issuedAt(Date.from(ZonedDateTime.now(ZoneOffset.UTC).toInstant()))
                .expiration(Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(600).toInstant()))
                .signWith(foreignPrivateKey, Jwts.SIG.RS256)
                .compact()

            Then("공개키가 없어 RS256 검증을 시도조차 하지 않고, HS256 검증도 실패해 최종 실패한다") {
                provider.validateToken(rs256SignedToken) shouldBe false
            }
        }
    }

    Given("실서비스에 이미 발급된 레거시 HS384/HS512 토큰 (JJWT 키 길이 기반 알고리즘 자동선택 결함이 있던 시절의 실제 운영 토큰 형태)") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("HS256"))
        val hmacKey = Keys.hmacShaKeyFor(hmacSecret.toByteArray(Charsets.UTF_8))

        listOf(Jwts.SIG.HS384, Jwts.SIG.HS512).forEach { legacyAlgorithm ->
            When("${legacyAlgorithm.id} 로 서명된 레거시 토큰을 검증하면") {
                val legacyToken = Jwts.builder()
                    .subject("11")
                    .id("legacy-${legacyAlgorithm.id}-jti")
                    .issuedAt(Date.from(ZonedDateTime.now(ZoneOffset.UTC).toInstant()))
                    .expiration(Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(600).toInstant()))
                    .signWith(hmacKey, legacyAlgorithm)
                    .compact()

                Then("HS256 검증 경로가 다른 HMAC 서명 강도의 레거시 토큰도 허용해 통과한다 — 강제 로그아웃 방지") {
                    jwtHeaderAlg(legacyToken) shouldBe legacyAlgorithm.id
                    provider.validateToken(legacyToken) shouldBe true
                }
            }
        }
    }

    Given("빈 토큰 문자열") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

        When("validateToken 을 호출하면") {
            Then("false 를 반환한다") {
                provider.validateToken("") shouldBe false
            }
        }
    }

    Given("Refresh Token 생성 시") {
        val (publicKeyPem, privateKeyPem) = generateRsaPemPair()
        val provider = JwtTokenProvider(hmacSecret, publicKeyPem, privateKeyPem, environmentWithAlgorithm("RS256"))

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
