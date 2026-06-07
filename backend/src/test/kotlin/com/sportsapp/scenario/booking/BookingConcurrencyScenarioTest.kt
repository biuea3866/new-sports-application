package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@AutoConfigureMockMvc
class BookingConcurrencyScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mockMvc: MockMvc,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE payments")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[S-01] capacity=1 슬롯에 두 사용자 동시 예약 요청") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CONCUR-01",
                    date = ZonedDateTime.now().plusDays(1),
                    timeRange = "09:00-10:00",
                    capacity = 1,
                    ownerId = 1L,
                )
            )

            When("두 사용자가 동시에 POST /bookings 호출") {
                val successCount = AtomicInteger(0)
                val conflictCount = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(2)

                val requestBody = buildRequestBody(slot.id)

                val tasks = listOf(1L, 2L).map { userId ->
                    executor.submit {
                        val result = mockMvc.post("/bookings") {
                            contentType = MediaType.APPLICATION_JSON
                            header("X-User-Id", userId.toString())
                            content = requestBody
                        }.andReturn()
                        val status = result.response.status
                        if (status == 202) successCount.incrementAndGet()
                        else if (status == 409) conflictCount.incrementAndGet()
                    }
                }
                tasks.forEach { it.get(10, TimeUnit.SECONDS) }
                executor.shutdown()

                Then("[S-01] 1건은 202, 1건은 409 응답이 반환된다") {
                    successCount.get() shouldBe 1
                    conflictCount.get() shouldBe 1
                }
            }
        }

        Given("[S-02] capacity=2 슬롯에 두 사용자 동시 예약 요청") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CONCUR-02",
                    date = ZonedDateTime.now().plusDays(1),
                    timeRange = "10:00-11:00",
                    capacity = 2,
                    ownerId = 1L,
                )
            )

            When("두 사용자가 동시에 POST /bookings 호출") {
                val successCount = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(2)

                val requestBody = buildRequestBody(slot.id)

                val tasks = listOf(3L, 4L).map { userId ->
                    executor.submit {
                        val result = mockMvc.post("/bookings") {
                            contentType = MediaType.APPLICATION_JSON
                            header("X-User-Id", userId.toString())
                            content = requestBody
                        }.andReturn()
                        if (result.response.status == 202) successCount.incrementAndGet()
                    }
                }
                tasks.forEach { it.get(10, TimeUnit.SECONDS) }
                executor.shutdown()

                Then("[S-03] capacity=2이므로 두 사용자 모두 202 응답이 반환된다") {
                    successCount.get() shouldBe 2
                    val bookingCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM bookings WHERE slot_id = ?",
                        Long::class.java,
                        slot.id,
                    )
                    bookingCount shouldBe 2L
                }
            }
        }

        Given("[S-02] Payment 연동 — 예약 후 paymentId가 반환된다") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-PAYMENT-01",
                    date = ZonedDateTime.now().plusDays(2),
                    timeRange = "14:00-15:00",
                    capacity = 10,
                    ownerId = 1L,
                )
            )

            When("POST /bookings 단건 호출") {
                val result = mockMvc.post("/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", "5")
                    content = buildRequestBody(slot.id)
                }.andReturn()

                Then("[S-02] 202 응답과 paymentId가 포함된 응답 본문이 반환된다") {
                    result.response.status shouldBe 202
                    val body = result.response.contentAsString
                    val paymentIdExists = body.contains("paymentId")
                    paymentIdExists shouldBe true
                }
            }
        }
    }

    private fun buildRequestBody(slotId: Long): String = """
        {
            "slotId": $slotId,
            "paymentMethod": "CREDIT_CARD",
            "amount": "30000",
            "currency": "KRW"
        }
    """.trimIndent()
}
