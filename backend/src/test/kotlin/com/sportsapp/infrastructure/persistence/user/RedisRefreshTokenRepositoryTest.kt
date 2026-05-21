package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

class RedisRefreshTokenRepositoryTest(
    @Autowired private val redisRefreshTokenRepository: RedisRefreshTokenRepository,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    init {
        Given("Redis 에 Refresh Token 저장 시") {
            When("save 후 findUserIdByToken 을 호출하면") {
                redisRefreshTokenRepository.save(101L, "my-refresh-token-r01")
                val result = redisRefreshTokenRepository.findUserIdByToken("my-refresh-token-r01")

                Then("[R-01] refreshtoken:{token} 키로 userId 가 정확히 저장된다") {
                    result shouldBe 101L
                }
            }
        }

        Given("동일 token 으로 재저장 시") {
            When("save 를 두 번 호출하면") {
                redisRefreshTokenRepository.save(102L, "overwrite-token-r02")
                redisRefreshTokenRepository.save(999L, "overwrite-token-r02")
                val result = redisRefreshTokenRepository.findUserIdByToken("overwrite-token-r02")

                Then("[R-02] 기존 키가 덮어쓰기로 갱신된다") {
                    result shouldBe 999L
                }
            }
        }

        Given("저장된 Refresh Token 무효화 시") {
            When("invalidate 를 호출하면") {
                redisRefreshTokenRepository.save(103L, "token-to-invalidate-r03")
                redisRefreshTokenRepository.invalidate("token-to-invalidate-r03")
                val result = redisRefreshTokenRepository.findUserIdByToken("token-to-invalidate-r03")

                Then("[R-03] findUserIdByToken 결과가 null 이다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("Refresh Token 저장 후") {
            When("TTL 을 조회하면") {
                redisRefreshTokenRepository.save(104L, "ttl-check-token-r04")
                val ttlSeconds = stringRedisTemplate.getExpire("refreshtoken:ttl-check-token-r04", TimeUnit.SECONDS)

                Then("[R-04] TTL 이 14일(1209600초) 이하이고 0 보다 크다") {
                    ttlSeconds shouldBeGreaterThan 0L
                    ttlSeconds shouldBeLessThan (14L * 24 * 60 * 60 + 1L)
                }
            }
        }
    }
}
