package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RedisRefreshTokenRepositoryTest(
    @Autowired private val redisRefreshTokenRepository: RedisRefreshTokenRepository,
) : BaseIntegrationTest() {

    init {
        Given("Redis 에 Refresh Token 저장 시") {
            When("save 후 find 를 호출하면") {
                redisRefreshTokenRepository.save(101L, "my-refresh-token")
                val result = redisRefreshTokenRepository.find(101L)

                Then("[R-01] refresh:{userId} 키로 정확히 저장된다") {
                    result shouldBe "my-refresh-token"
                }
            }
        }

        Given("동일 userId 로 재저장 시") {
            When("save 를 두 번 호출하면") {
                redisRefreshTokenRepository.save(102L, "old-token")
                redisRefreshTokenRepository.save(102L, "new-token")
                val result = redisRefreshTokenRepository.find(102L)

                Then("[R-02] 기존 키가 덮어쓰기로 갱신된다") {
                    result shouldBe "new-token"
                }
            }
        }

        Given("저장된 Refresh Token 삭제 시") {
            When("remove 를 호출하면") {
                redisRefreshTokenRepository.save(103L, "token-to-remove")
                redisRefreshTokenRepository.remove(103L)
                val result = redisRefreshTokenRepository.find(103L)

                Then("[U-03] find 결과가 null 이다") {
                    result.shouldBeNull()
                }
            }
        }
    }
}
