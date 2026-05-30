package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpAnomalyEvent
import com.sportsapp.domain.mcp.McpAnomalyEventRepository
import com.sportsapp.domain.mcp.McpAnomalyEventStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime
import java.util.UUID

class McpAnomalyEventRepositoryImplTest(
    @Autowired private val mcpAnomalyEventRepository: McpAnomalyEventRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun createEvent(
        ownerUserId: Long = 10L,
        tokenId: Long = 1L,
        detectedAt: ZonedDateTime = ZonedDateTime.now(),
        sourceEventId: String = UUID.randomUUID().toString(),
    ): McpAnomalyEvent = mcpAnomalyEventRepository.save(
        McpAnomalyEvent(
            sourceEventId = sourceEventId,
            tokenId = tokenId,
            ownerUserId = ownerUserId,
            detectedAt = detectedAt,
            currentHourCount = 200L,
            baselineAverage = 50.0,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_anomaly_events")
        }

        Given("[R-01] McpAnomalyEvent save 후 findById 라운드트립") {
            val saved = createEvent(ownerUserId = 10L, tokenId = 5L)

            When("findById로 조회하면") {
                val found = mcpAnomalyEventRepository.findById(saved.id)

                Then("[R-01] 저장된 필드가 정확히 복원된다") {
                    found.shouldNotBeNull()
                    found.tokenId shouldBe 5L
                    found.ownerUserId shouldBe 10L
                    found.status shouldBe McpAnomalyEventStatus.OPEN
                    found.falsePositive shouldBe false
                    found.resolvedAt.shouldBeNull()
                    found.sourceEventId shouldBe saved.sourceEventId
                }
            }
        }

        Given("[R-02] ownerUserId=10 인 이벤트 3건, ownerUserId=99 인 이벤트 1건") {
            val now = ZonedDateTime.now()
            createEvent(ownerUserId = 10L, detectedAt = now.minusHours(3))
            createEvent(ownerUserId = 10L, detectedAt = now.minusHours(2))
            createEvent(ownerUserId = 10L, detectedAt = now.minusHours(1))
            createEvent(ownerUserId = 99L, detectedAt = now.minusHours(1))

            When("findByOwnerUserId(10) 를 page=0, size=10, detectedAt DESC 정렬로 조회하면") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "detectedAt"))
                val page = mcpAnomalyEventRepository.findByOwnerUserId(10L, pageable)

                Then("[R-02] 3건만 반환되고 detectedAt DESC 정렬이다") {
                    page.totalElements shouldBe 3L
                    page.content.size shouldBe 3
                    val detectedAts = page.content.map { it.detectedAt }
                    detectedAts shouldBe detectedAts.sortedDescending()
                    page.content.all { it.ownerUserId == 10L } shouldBe true
                }
            }
        }

        Given("[R-03] soft-delete된 이벤트") {
            val saved = createEvent(ownerUserId = 20L)
            val found = requireNotNull(mcpAnomalyEventRepository.findById(saved.id))
            found.softDelete(userId = 20L)
            mcpAnomalyEventRepository.save(found)

            When("findById 로 조회하면") {
                val result = mcpAnomalyEventRepository.findById(saved.id)

                Then("[R-03] soft-delete 된 이벤트는 조회되지 않는다") {
                    result.shouldBeNull()
                }
            }

            When("findByOwnerUserId 로 조회하면") {
                val pageable = PageRequest.of(0, 10)
                val page = mcpAnomalyEventRepository.findByOwnerUserId(20L, pageable)

                Then("[R-03] soft-delete 된 이벤트는 목록에 포함되지 않는다") {
                    page.totalElements shouldBe 0L
                }
            }
        }

        Given("[R-04] ZonedDateTime 필드 저장 후 조회") {
            val detectedAt = ZonedDateTime.now()
            val saved = createEvent(detectedAt = detectedAt)

            When("findById 로 조회하면") {
                val found = requireNotNull(mcpAnomalyEventRepository.findById(saved.id))

                Then("[R-04] detectedAt instant 값이 일치한다") {
                    found.detectedAt.toInstant() shouldBe detectedAt.toInstant()
                }
            }
        }

        Given("[R-05] 동일 sourceEventId 로 이미 저장된 이벤트가 있는 경우") {
            val existingId = "evt-idempotent-123"
            createEvent(sourceEventId = existingId)

            When("existsBySourceEventId 를 조회하면") {
                val exists = mcpAnomalyEventRepository.existsBySourceEventId(existingId)
                val notExists = mcpAnomalyEventRepository.existsBySourceEventId("evt-unknown-999")

                Then("[R-05] 존재하는 이벤트는 true, 없는 이벤트는 false 반환") {
                    exists shouldBe true
                    notExists shouldBe false
                }
            }
        }

        Given("[R-06a] ownerUserId=10 이벤트 저장 후 ownerUserId=10으로 조회") {
            val eventForUser10 = createEvent(ownerUserId = 10L)

            When("findByIdAndOwnerUserId(id=user10의 id, ownerUserId=10) 를 호출하면") {
                val found = mcpAnomalyEventRepository.findByIdAndOwnerUserId(eventForUser10.id, 10L)

                Then("[R-06a] 본인 이벤트는 반환된다") {
                    found.shouldNotBeNull()
                    found.ownerUserId shouldBe 10L
                }
            }
        }

        Given("[R-06b] ownerUserId=10 이벤트 저장 후 ownerUserId=99(타인)로 조회") {
            val eventForUser10 = createEvent(ownerUserId = 10L)

            When("findByIdAndOwnerUserId(id=user10의 id, ownerUserId=99) 를 호출하면") {
                val notFound = mcpAnomalyEventRepository.findByIdAndOwnerUserId(eventForUser10.id, 99L)

                Then("[R-06b] 타인 이벤트는 null 반환 (IDOR 차단)") {
                    notFound.shouldBeNull()
                }
            }
        }
    }
}
