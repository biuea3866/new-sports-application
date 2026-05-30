package com.sportsapp.scenario.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.mcp.ListMyAnomalyEventsCommand
import com.sportsapp.application.mcp.ListMyAnomalyEventsUseCase
import com.sportsapp.application.mcp.MarkAnomalyFalsePositiveCommand
import com.sportsapp.application.mcp.MarkAnomalyFalsePositiveUseCase
import com.sportsapp.application.mcp.PersistAnomalyEventCommand
import com.sportsapp.application.mcp.PersistAnomalyEventUseCase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.mcp.McpAnomalyEventStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime
import java.util.UUID

class McpAnomalyEventScenarioTest(
    @Autowired private val persistAnomalyEventUseCase: PersistAnomalyEventUseCase,
    @Autowired private val listMyAnomalyEventsUseCase: ListMyAnomalyEventsUseCase,
    @Autowired private val markAnomalyFalsePositiveUseCase: MarkAnomalyFalsePositiveUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun persistCommand(
        tokenId: Long = 1L,
        ownerUserId: Long = 10L,
        sourceEventId: String = UUID.randomUUID().toString(),
    ): PersistAnomalyEventCommand = PersistAnomalyEventCommand(
        sourceEventId = sourceEventId,
        tokenId = tokenId,
        ownerUserId = ownerUserId,
        detectedAt = ZonedDateTime.now(),
        currentHourCount = 200L,
        baselineAverage = 50.0,
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_anomaly_events")
        }

        Given("[S-01] McpAnomalyDetectedEvent 발행 시나리오 — 이벤트 영속화") {
            val command = persistCommand(ownerUserId = 10L, tokenId = 5L)

            When("PersistAnomalyEventUseCase.execute()를 호출하면") {
                val response = persistAnomalyEventUseCase.execute(command)

                Then("[S-01] DB에 OPEN 상태로 저장되고 응답이 반환된다") {
                    response.shouldNotBeNull()
                    response.tokenId shouldBe 5L
                    response.ownerUserId shouldBe 10L
                    response.status shouldBe McpAnomalyEventStatus.OPEN
                    response.falsePositive shouldBe false
                }
            }
        }

        Given("[S-02] 조회 시나리오 — 운영자 본인 이벤트만 반환 (IDOR 차단)") {
            persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 10L))
            persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 10L))
            persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 99L))

            When("ownerUserId=10 으로 ListMyAnomalyEventsUseCase.execute()를 호출하면") {
                val command = ListMyAnomalyEventsCommand(ownerUserId = 10L, page = 0, size = 20)
                val response = listMyAnomalyEventsUseCase.execute(command)

                Then("[S-02] ownerUserId=10 이벤트 2건만 반환된다") {
                    response.totalElements shouldBe 2L
                    response.content.all { it.ownerUserId == 10L } shouldBe true
                }
            }
        }

        Given("[S-03] false positive 마킹 시나리오 — 본인 이벤트 마킹") {
            val saved = requireNotNull(persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 10L)))

            When("ownerUserId=10 이 자신의 이벤트를 false positive로 마킹하면") {
                val markCommand = MarkAnomalyFalsePositiveCommand(
                    anomalyEventId = saved.id,
                    requestUserId = 10L,
                    note = "정상 배치",
                )
                val updated = markAnomalyFalsePositiveUseCase.execute(markCommand)

                Then("[S-03] status=FALSE_POSITIVE, falsePositive=true 로 변경된다") {
                    updated.status shouldBe McpAnomalyEventStatus.FALSE_POSITIVE
                    updated.falsePositive shouldBe true
                    updated.note shouldBe "정상 배치"
                }
            }
        }

        Given("[S-04] false positive 마킹 시나리오 — 타인 이벤트 마킹 시도 (IDOR 차단 — DB 레벨)") {
            val saved = requireNotNull(persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 10L)))

            When("다른 사용자(userId=99)가 false positive 마킹을 시도하면") {
                val markCommand = MarkAnomalyFalsePositiveCommand(
                    anomalyEventId = saved.id,
                    requestUserId = 99L,
                    note = null,
                )

                Then("[S-04] ResourceNotFoundException이 발생하고 상태가 변경되지 않는다") {
                    shouldThrow<ResourceNotFoundException> {
                        markAnomalyFalsePositiveUseCase.execute(markCommand)
                    }
                }
            }
        }

        Given("[S-05] 페이징 시나리오 — size=2 페이징 조회") {
            repeat(5) { persistAnomalyEventUseCase.execute(persistCommand(ownerUserId = 30L)) }

            When("page=0, size=2 로 조회하면") {
                val command = ListMyAnomalyEventsCommand(ownerUserId = 30L, page = 0, size = 2)
                val response = listMyAnomalyEventsUseCase.execute(command)

                Then("[S-05] totalElements=5, content=2, totalPages=3 이다") {
                    response.totalElements shouldBe 5L
                    response.content.size shouldBe 2
                    response.totalPages shouldBe 3
                }
            }
        }

        Given("[S-06] 멱등성 — 동일 sourceEventId 로 2회 수신") {
            val duplicateEventId = "evt-idempotent-fixed-001"
            val command = persistCommand(ownerUserId = 40L, sourceEventId = duplicateEventId)

            When("동일 command를 2회 실행하면") {
                val first = persistAnomalyEventUseCase.execute(command)
                val second = persistAnomalyEventUseCase.execute(command)

                Then("[S-06] 첫 번째만 영속화되고 두 번째는 null 반환, DB에 1건만 존재한다") {
                    first.shouldNotBeNull()
                    second.shouldBeNull()
                    val listCommand = ListMyAnomalyEventsCommand(ownerUserId = 40L, page = 0, size = 20)
                    listMyAnomalyEventsUseCase.execute(listCommand).totalElements shouldBe 1L
                }
            }
        }
    }
}
