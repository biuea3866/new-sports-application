package com.sportsapp.scenario.mcp.confirm

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.presentation.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.presentation.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.presentation.mcp.confirm.ConfirmationTokenService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class ConfirmationTokenScenarioTest(
    @Autowired private val confirmationTokenService: ConfirmationTokenService,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    private val context = ConfirmationTokenContext(
        toolName = "cancelBooking",
        userId = 100L,
        paramsHash = "hash-abc",
    )

    init {
        afterEach {
            stringRedisTemplate.keys("mcp:confirm:*").forEach { key ->
                stringRedisTemplate.unlink(key)
            }
        }

        Given("[S-01] 유효한 context 로 토큰을 발급한 상태") {
            val token = confirmationTokenService.issue(context)

            When("consume 을 호출하면") {
                val consumedContext = confirmationTokenService.consume(token)

                Then("[S-01] context 가 정상적으로 반환된다") {
                    consumedContext.toolName shouldBe context.toolName
                    consumedContext.userId shouldBe context.userId
                    consumedContext.paramsHash shouldBe context.paramsHash
                }
            }
        }

        Given("[S-02] 토큰을 이미 consume 한 상태") {
            val token = confirmationTokenService.issue(context)
            confirmationTokenService.consume(token)

            When("동일 토큰으로 consume 을 재시도하면") {
                Then("[S-02] ConfirmationTokenAlreadyConsumedException 이 발생한다") {
                    shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                        confirmationTokenService.consume(token)
                    }
                }
            }
        }

        Given("[S-03] TTL 을 2초로 지정하여 발급한 토큰") {
            val token = confirmationTokenService.issue(context, Duration.ofSeconds(2))

            When("TTL 초과 후 consume 을 시도하면") {
                Thread.sleep(2_500)

                Then("[S-03] ConfirmationTokenExpiredException 이 발생한다") {
                    shouldThrow<ConfirmationTokenExpiredException> {
                        confirmationTokenService.consume(token)
                    }
                }
            }
        }

        Given("[S-04] 유효한 토큰과 일치하는 context 로 verify 호출") {
            val token = confirmationTokenService.issue(context)

            When("동일 context 로 verify 를 호출하면") {
                val result = confirmationTokenService.verify(token, context)

                Then("[S-04] true 를 반환하고 토큰은 소진된다") {
                    result.shouldBeTrue()
                }
            }
        }

        Given("[S-05] 유효한 토큰과 불일치하는 context 로 verify 호출") {
            val token = confirmationTokenService.issue(context)
            val differentContext = ConfirmationTokenContext(
                toolName = "cancelBooking",
                userId = 100L,
                paramsHash = "different-hash",
            )

            When("다른 context 로 verify 를 호출하면") {
                val result = confirmationTokenService.verify(token, differentContext)

                Then("[S-05] false 를 반환한다") {
                    result.shouldBeFalse()
                }
            }
        }
    }
}
