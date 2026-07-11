package com.sportsapp.presentation.virtualqueue.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.virtualqueue.dto.QueueEntryResponse
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val TYPE_SLUG = "limited-drop"

/**
 * `VirtualQueueApiController` — API 계약(TDD "FE/외부 계약 — API 명세" §1~4) 매핑 검증(BE-08).
 *
 * `virtual-queue.enabled` 플래그를 `beforeSpec`에서 MySQL에 직접 seed 한다 — `FeatureFlag.create`의
 * `FLAG_KEY_PATTERN`(`^[a-z0-9]+(\.[a-z0-9-]+)*$`)이 첫 세그먼트 하이픈(`virtual-queue`)을 허용하지
 * 않아 엔티티 팩토리로는 이 키를 생성할 수 없다(featureflag 도메인의 기존 결함, BE-08 범위 밖) —
 * `jdbcTemplate` 직접 INSERT로 우회한다. Admission Pump는 `virtual-queue.admission.enabled`를
 * seed하지 않아 default(false)로 항상 스킵되므로, admittedCount는 테스트 동안 0으로 고정되고
 * WAITING 상태가 안정적으로 유지된다.
 */
@AutoConfigureMockMvc
class VirtualQueueApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val redisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    init {
        beforeSpec {
            jdbcTemplate.update(
                """
                INSERT INTO feature_flags (flag_key, flag_type, status, description, strategy_config, version, created_at, updated_at)
                VALUES (?, 'OPERATIONAL', 'ACTIVE', NULL, '{"strategyType":"GLOBAL_TOGGLE","enabled":true}', 0, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE strategy_config = VALUES(strategy_config), status = 'ACTIVE'
                """.trimIndent(),
                VirtualQueueFeatureFlagKeys.ENABLED,
            )
        }

        fun cleanupTarget(targetId: Long) {
            val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)
            redisTemplate.delete(listOf(target.waitingKey(), target.heartbeatKey(), target.seqKey(), target.admittedCountKey()))
        }

        fun enterUrl(targetId: Long) = "/virtual-queues/$TYPE_SLUG/$targetId/entries"
        fun statusUrl(targetId: Long) = "/virtual-queues/$TYPE_SLUG/$targetId/entries/me"
        fun statsUrl(targetId: Long) = "/virtual-queues/$TYPE_SLUG/$targetId/stats"

        fun enter(targetId: Long, userId: Long) = mockMvc.perform(post(enterUrl(targetId)).header("X-User-Id", userId.toString()))

        fun parseEntry(bodyJson: String): QueueEntryResponse = objectMapper.readValue(bodyJson, QueueEntryResponse::class.java)

        Given("대기열이 활성화된 대상에 신규 사용자가") {
            val targetId = 910_001L
            cleanupTarget(targetId)

            When("POST entries로 진입하면") {
                val result = enter(targetId, userId = 1L)
                    .andExpect(status().isOk)
                    .andReturn()

                Then("200과 WAITING·1-based position을 반환한다") {
                    val response = parseEntry(result.response.contentAsString)
                    response.status shouldBe "WAITING"
                    response.position shouldBe 1L
                    response.aheadCount shouldBe 0L
                    response.entryToken.shouldBeNull()
                }
            }
        }

        Given("이미 진입한 사용자가 동일 userId로") {
            val targetId = 910_002L
            cleanupTarget(targetId)
            enter(targetId, userId = 2L).andExpect(status().isOk)

            When("다시 POST entries를 호출하면 (멱등)") {
                val result = enter(targetId, userId = 2L).andExpect(status().isOk).andReturn()

                Then("새 순번 없이 기존 position(1)을 그대로 반환한다") {
                    parseEntry(result.response.contentAsString).position shouldBe 1L
                }
            }
        }

        Given("대상 대기열이 포화(ZCARD>=maxCapacity, FR-7)된 상태에서") {
            val targetId = 910_003L
            cleanupTarget(targetId)
            val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)
            // enter.lua를 100_000회 왕복 호출하지 않고, waiting ZSET을 직접 maxCapacity(기본 100000)만큼 채운다.
            val fillScript = DefaultRedisScript(
                """
                local key = KEYS[1]
                local count = tonumber(ARGV[1])
                for i = 1, count do
                    redis.call('ZADD', key, i, 'dummy-' .. i)
                end
                return redis.call('ZCARD', key)
                """.trimIndent(),
                Long::class.java,
            )
            redisTemplate.execute(fillScript, listOf(target.waitingKey()), "100000")

            When("신규 사용자가 POST entries로 진입을 시도하면") {
                val result = enter(targetId, userId = 999_001L)
                    .andExpect(status().isTooManyRequests)
                    .andReturn()

                Then("429와 QUEUE_FULL code를 반환한다") {
                    result.response.status shouldBe 429
                    val bodyJson = objectMapper.readTree(result.response.contentAsString)
                    bodyJson.get("properties").get("code").asText() shouldBe "QUEUE_FULL"
                }
            }
        }

        Given("대기열에 진입해 폴링 중인 사용자가") {
            val targetId = 910_004L
            cleanupTarget(targetId)
            enter(targetId, userId = 3L).andExpect(status().isOk)
            val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)
            val heartbeatBefore = requireNotNull(redisTemplate.opsForZSet().score(target.heartbeatKey(), "3"))

            When("GET entries/me로 상태를 조회하면") {
                Thread.sleep(5)
                val result = mockMvc.perform(get(statusUrl(targetId)).header("X-User-Id", "3"))
                    .andExpect(status().isOk)
                    .andReturn()

                Then("WAITING·ahead·ETA를 반환하고 heartbeat를 갱신한다") {
                    val response = parseEntry(result.response.contentAsString)
                    response.status shouldBe "WAITING"
                    response.aheadCount.shouldNotBeNull()
                    response.etaSeconds.shouldNotBeNull()

                    val heartbeatAfter = requireNotNull(redisTemplate.opsForZSet().score(target.heartbeatKey(), "3"))
                    heartbeatAfter shouldBeGreaterThan heartbeatBefore
                }
            }
        }

        Given("큐에 존재하지 않는(이탈했거나 미진입) 사용자가") {
            val targetId = 910_005L
            cleanupTarget(targetId)

            When("GET entries/me로 상태를 조회하면") {
                val result = mockMvc.perform(get(statusUrl(targetId)).header("X-User-Id", "4"))
                    .andExpect(status().isNotFound)
                    .andReturn()

                Then("404 QUEUE_ENTRY_NOT_FOUND를 반환한다") {
                    val bodyJson = objectMapper.readTree(result.response.contentAsString)
                    bodyJson.get("properties").get("code").asText() shouldBe "QUEUE_ENTRY_NOT_FOUND"
                }
            }
        }

        Given("대기열에 진입한 사용자가 명시적으로 이탈하려는 상황에서") {
            val targetId = 910_006L
            cleanupTarget(targetId)
            enter(targetId, userId = 5L).andExpect(status().isOk)
            val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)

            When("DELETE entries/me를 호출하면") {
                mockMvc.perform(delete(statusUrl(targetId)).header("X-User-Id", "5"))
                    .andExpect(status().isNoContent)

                Then("204를 반환하고 waiting ZSET에서 제거된다") {
                    redisTemplate.opsForZSet().score(target.waitingKey(), "5").shouldBeNull()
                }
            }
        }

        Given("운영자가 대기열 통계를 조회하는 상황에서") {
            val targetId = 910_007L
            cleanupTarget(targetId)
            enter(targetId, userId = 6L).andExpect(status().isOk)
            enter(targetId, userId = 7L).andExpect(status().isOk)

            When("GET stats를 호출하면") {
                val result = mockMvc.perform(get(statsUrl(targetId)))
                    .andExpect(status().isOk)
                    .andReturn()

                Then("200과 waitingCount·admittedCount를 반환한다 (FR-11)") {
                    val bodyJson = objectMapper.readTree(result.response.contentAsString)
                    bodyJson.get("waitingCount").asLong() shouldBe 2L
                    bodyJson.get("admittedCount").asLong() shouldBe 0L
                    bodyJson.has("admissionRatePerSec") shouldBe true
                }
            }
        }

        Given("잘못된 {type} 경로 값으로") {
            val targetId = 910_008L

            When("POST entries를 호출하면") {
                val result = mockMvc.perform(post("/virtual-queues/not-a-real-type/$targetId/entries").header("X-User-Id", "8"))
                    .andExpect(status().isBadRequest)
                    .andReturn()

                Then("400을 반환한다 (enum 파싱 실패)") {
                    result.response.status shouldBe 400
                }
            }
        }
    }
}
