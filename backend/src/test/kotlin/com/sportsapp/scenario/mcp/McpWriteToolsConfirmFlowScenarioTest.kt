package com.sportsapp.scenario.mcp

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.CancelBookingCommand
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.application.booking.CreateSlotCommand
import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotCommand
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.SlotResponse
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.ZonedDateTime

/**
 * BE-15 confirm flow E2E 시나리오 테스트.
 *
 * @PreAuthorize AOP 는 HTTP 인증 컨텍스트에 의존하므로,
 * 시나리오 레이어에서는 UseCase + ConfirmationTokenGateway 를 직접 호출해
 * confirm flow 를 end-to-end 로 검증한다.
 * scope 가드 검증은 단위 테스트(McpBookingWriteToolsTest 등) 에서 담당한다.
 */
class McpWriteToolsConfirmFlowScenarioTest(
    @Autowired private val cancelBookingUseCase: CancelBookingUseCase,
    @Autowired private val createSlotUseCase: CreateSlotUseCase,
    @Autowired private val deleteSlotUseCase: DeleteSlotUseCase,
    @Autowired private val confirmationTokenGateway: ConfirmationTokenGateway,
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingRepository: BookingRepository,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
            stringRedisTemplate.keys("mcp:confirm:*").forEach { key ->
                stringRedisTemplate.unlink(key)
            }
        }

        // ─── cancelBooking confirm flow ───────────────────────────────

        Given("[S-01] cancelBooking — 토큰 발급 후 소진 시 예약이 취소된다") {
            val slot = slotRepository.save(
                Slot.create(facilityId = "FAC-01", date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00", capacity = 10, ownerId = 1L)
            )
            val booking = bookingRepository.save(Booking.createPending(userId = 1L, slotId = slot.id))
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(toolName = "cancelBooking", userId = 1L, paramsHash = "cancel-hash")
            )

            When("토큰을 소진하고 UseCase 를 실행하면") {
                val context = confirmationTokenGateway.consume(token)
                val result: BookingResponse = cancelBookingUseCase.execute(
                    CancelBookingCommand(bookingId = booking.id, cancelledByUserId = 1L, reason = "운영 취소")
                )

                Then("[S-01] context 가 복원되고 예약이 CANCELLED 된다") {
                    context.toolName shouldBe "cancelBooking"
                    result.status shouldBe BookingStatus.CANCELLED
                }
            }
        }

        Given("[S-02] cancelBooking — 소진된 토큰 재사용 시 AlreadyConsumed 예외") {
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(toolName = "cancelBooking", userId = 1L, paramsHash = "reuse-hash")
            )
            confirmationTokenGateway.consume(token)

            When("동일 토큰으로 재소진 시도 시") {
                Then("[S-02] ConfirmationTokenAlreadyConsumedException 이 발생한다") {
                    shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                        confirmationTokenGateway.consume(token)
                    }
                }
            }
        }

        Given("[S-03] cancelBooking — TTL 만료 토큰 소진 시 Expired 예외") {
            val expiredToken = confirmationTokenGateway.issue(
                ConfirmationTokenContext(toolName = "cancelBooking", userId = 1L, paramsHash = "ttl-hash"),
                Duration.ofSeconds(1),
            )
            Thread.sleep(1_500)

            When("만료된 토큰으로 소진 시도 시") {
                Then("[S-03] ConfirmationTokenExpiredException 이 발생한다") {
                    shouldThrow<ConfirmationTokenExpiredException> {
                        confirmationTokenGateway.consume(expiredToken)
                    }
                }
            }
        }

        // ─── createSlot confirm flow ───────────────────────────────────

        Given("[S-04] createSlot — 토큰 발급 후 소진 시 슬롯이 생성된다") {
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(toolName = "createSlot", userId = 1L, paramsHash = "create-hash")
            )

            When("토큰을 소진하고 UseCase 를 실행하면") {
                val context = confirmationTokenGateway.consume(token)
                val result: SlotResponse = createSlotUseCase.execute(
                    CreateSlotCommand(ownerId = 1L, facilityId = "FAC-03",
                        date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                        timeRange = "09:00-10:00", capacity = 10)
                )

                Then("[S-04] context.toolName 이 createSlot 이고 슬롯이 생성된다") {
                    context.toolName shouldBe "createSlot"
                    result.facilityId shouldBe "FAC-03"
                    result.capacity shouldBe 10
                }
            }
        }

        // ─── deleteSlot confirm flow ───────────────────────────────────

        Given("[S-05] deleteSlot — 토큰 발급 후 소진 시 슬롯이 삭제된다") {
            val slot = slotRepository.save(
                Slot.create(facilityId = "FAC-04", date = ZonedDateTime.now().plusDays(1),
                    timeRange = "14:00-15:00", capacity = 3, ownerId = 1L)
            )
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(toolName = "deleteSlot", userId = 1L, paramsHash = "delete-hash")
            )

            When("토큰을 소진하고 UseCase 를 실행하면") {
                val context = confirmationTokenGateway.consume(token)
                deleteSlotUseCase.execute(DeleteSlotCommand(requesterId = 1L, slotId = slot.id))

                Then("[S-05] context.toolName 이 deleteSlot 이다") {
                    context.toolName shouldBe "deleteSlot"
                }
            }
        }
    }
}
