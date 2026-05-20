package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.SeatJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@AutoConfigureMockMvc
class SeatLockConcurrencyScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        afterEach {
            val keys = redisTemplate.keys("seat:lock:*")
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
        }

        Given("[S-01] 동일 좌석에 두 사용자가 동시에 선택 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Concurrency Test Concert", "Seoul Arena", baseTime, EventStatus.OPEN)
            )
            val seat = seatJpaRepository.save(
                Seat(0L, event.id, "A", "1", "1", BigDecimal("50000"))
            )

            When("userId=1, userId=2가 동시에 POST /events/{id}/seats/select") {
                val successCount = AtomicInteger(0)
                val conflictCount = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(2)

                val requestBody = """{"seatIds":[${seat.id}]}"""

                val tasks = listOf(1L, 2L).map { userId ->
                    executor.submit {
                        val result = mockMvc.post("/events/${event.id}/seats/select") {
                            contentType = MediaType.APPLICATION_JSON
                            header("X-User-Id", userId.toString())
                            content = requestBody
                        }.andReturn()
                        when (result.response.status) {
                            200 -> successCount.incrementAndGet()
                            409 -> conflictCount.incrementAndGet()
                        }
                    }
                }
                tasks.forEach { it.get(10, TimeUnit.SECONDS) }
                executor.shutdown()

                Then("[S-01] 1명만 200, 나머지 1명은 409 응답이 반환된다") {
                    successCount.get() shouldBe 1
                    conflictCount.get() shouldBe 1
                }
            }
        }

        Given("[S-01-100] 동일 좌석에 100명이 동시에 선택 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "100 Concurrency Concert", "Busan Arena", baseTime.plusDays(1), EventStatus.OPEN)
            )
            val seat = seatJpaRepository.save(
                Seat(0L, event.id, "B", "1", "1", BigDecimal("70000"))
            )

            When("100명이 동시에 POST /events/{id}/seats/select") {
                val successCount = AtomicInteger(0)
                val conflictCount = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(100)

                val requestBody = """{"seatIds":[${seat.id}]}"""

                val tasks = (1..100).map { userId ->
                    executor.submit {
                        val result = mockMvc.post("/events/${event.id}/seats/select") {
                            contentType = MediaType.APPLICATION_JSON
                            header("X-User-Id", userId.toString())
                            content = requestBody
                        }.andReturn()
                        when (result.response.status) {
                            200 -> successCount.incrementAndGet()
                            409 -> conflictCount.incrementAndGet()
                        }
                    }
                }
                tasks.forEach { it.get(30, TimeUnit.SECONDS) }
                executor.shutdown()

                Then("[S-01] 100명 중 정확히 1명만 락을 획득한다") {
                    successCount.get() shouldBe 1
                    conflictCount.get() shouldBe 99
                }
            }
        }

        Given("[S-02] 다중 좌석 선택 중 일부 실패 시 롤백") {
            val event = eventJpaRepository.save(
                Event(0L, "Partial Rollback Concert", "Incheon", baseTime.plusDays(2), EventStatus.OPEN)
            )
            val seat1 = seatJpaRepository.save(Seat(0L, event.id, "C", "1", "1", BigDecimal("40000")))
            val seat2 = seatJpaRepository.save(Seat(0L, event.id, "C", "1", "2", BigDecimal("40000")))

            // userId=99가 seat2를 먼저 점유
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat2.id}", "99")

            When("userId=7이 [seat1, seat2] 동시 선택 시") {
                val requestBody = """{"seatIds":[${seat1.id},${seat2.id}]}"""
                val result = mockMvc.post("/events/${event.id}/seats/select") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", "7")
                    content = requestBody
                }.andReturn()

                Then("[S-02] 409 응답이 반환되고 seat1 락도 롤백되어 해제된다") {
                    result.response.status shouldBe 409
                    val seat1LockValue = redisTemplate.opsForValue().get("seat:lock:${event.id}:${seat1.id}")
                    seat1LockValue shouldBe null
                }
            }
        }

        Given("[S-03] 락 해제 API 호출 시") {
            val event = eventJpaRepository.save(
                Event(0L, "Release Test Concert", "Daegu", baseTime.plusDays(3), EventStatus.OPEN)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "D", "1", "1", BigDecimal("60000")))

            // userId=5가 seat 락 획득
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", "5")

            When("userId=5가 POST /events/{id}/seats/release 호출 시") {
                val requestBody = """{"seatIds":[${seat.id}]}"""
                val result = mockMvc.post("/events/${event.id}/seats/release") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", "5")
                    content = requestBody
                }.andReturn()

                Then("[S-03] 204 응답이 반환되고 Redis 키가 삭제된다") {
                    result.response.status shouldBe 204
                    val lockValue = redisTemplate.opsForValue().get("seat:lock:${event.id}:${seat.id}")
                    lockValue shouldBe null
                }
            }
        }
    }
}
