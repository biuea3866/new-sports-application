package com.sportsapp.scenario.mcp.confirm

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.mcp.exception.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.dto.ConfirmationTokenContext
import com.sportsapp.domain.mcp.exception.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.gateway.ConfirmationTokenGateway
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class ConfirmationTokenGatewayScenarioTest(
    @Autowired private val confirmationTokenGateway: ConfirmationTokenGateway,
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
            val token = confirmationTokenGateway.issue(context)

            When("consume 을 호출하면") {
                val consumedContext = confirmationTokenGateway.consume(token)

                Then("[S-01] context 가 정상적으로 반환된다") {
                    consumedContext.toolName shouldBe context.toolName
                    consumedContext.userId shouldBe context.userId
                    consumedContext.paramsHash shouldBe context.paramsHash
                }
            }
        }

        Given("[S-02] 토큰을 이미 consume 한 상태") {
            val token = confirmationTokenGateway.issue(context)
            confirmationTokenGateway.consume(token)

            When("동일 토큰으로 consume 을 재시도하면") {
                Then("[S-02] ConfirmationTokenAlreadyConsumedException 이 발생한다") {
                    shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                        confirmationTokenGateway.consume(token)
                    }
                }
            }
        }

        Given("[S-03] TTL 을 2초로 지정하여 발급한 토큰") {
            val token = confirmationTokenGateway.issue(context, Duration.ofSeconds(2))

            When("TTL 초과 후 consume 을 시도하면") {
                Thread.sleep(2_500)

                Then("[S-03] ConfirmationTokenExpiredException 이 발생한다") {
                    shouldThrow<ConfirmationTokenExpiredException> {
                        confirmationTokenGateway.consume(token)
                    }
                }
            }
        }

        Given("[S-04] 유효한 토큰과 일치하는 context 로 consume 후 비교") {
            val token = confirmationTokenGateway.issue(context)

            When("consume 후 동일 context 와 비교하면") {
                val consumedContext = confirmationTokenGateway.consume(token)

                Then("[S-04] 반환된 context 가 발급 시 context 와 일치하고 토큰은 소진된다") {
                    consumedContext shouldBe context
                    shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                        confirmationTokenGateway.consume(token)
                    }
                }
            }
        }

        Given("[S-05] 유효한 토큰과 불일치하는 context 를 호출자가 직접 비교") {
            val token = confirmationTokenGateway.issue(context)
            val differentContext = ConfirmationTokenContext(
                toolName = "cancelBooking",
                userId = 100L,
                paramsHash = "different-hash",
            )

            When("consume 후 다른 context 와 비교하면") {
                val consumedContext = confirmationTokenGateway.consume(token)

                Then("[S-05] 반환된 context 가 다른 context 와 불일치하고 토큰은 이미 소진되어 재시도 불가") {
                    consumedContext shouldNotBe differentContext
                    shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                        confirmationTokenGateway.consume(token)
                    }
                }
            }
        }
    }
}
