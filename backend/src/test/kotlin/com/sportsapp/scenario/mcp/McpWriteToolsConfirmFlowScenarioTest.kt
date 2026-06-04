package com.sportsapp.scenario.mcp

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.dto.CancelBookingCommand
import com.sportsapp.application.booking.usecase.CancelBookingUseCase
import com.sportsapp.application.booking.dto.CreateSlotCommand
import com.sportsapp.application.booking.usecase.CreateSlotUseCase
import com.sportsapp.application.booking.dto.DeleteSlotCommand
import com.sportsapp.application.booking.usecase.DeleteSlotUseCase
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.mcp.confirm.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.toolregistry.McpBookingWriteTools
import com.sportsapp.presentation.mcp.toolregistry.McpSlotWriteTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * BE-15 confirm flow E2E 시나리오 테스트.
 *
 * scope 가드(@PreAuthorize AOP) 검증은 Spring 컨텍스트 + AOP proxy 를 거치도록
 * McpBookingWriteTools / McpSlotWriteTools bean 을 @Autowired 로 주입받아 호출한다.
 *
 * confirm flow paramsHash 검증은 UseCase + ConfirmationTokenGateway 직접 호출로도
 * 별도 검증한다.
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
    @Autowired private val mcpBookingWriteTools: McpBookingWriteTools,
    @Autowired private val mcpSlotWriteTools: McpSlotWriteTools,
) : BaseIntegrationTest() {

    private val writeBookingScope = McpScope.of("write:booking")
    private val writeSlotScope = McpScope.of("write:slot")

    private fun setSecurityContext(userId: Long, vararg scopes: McpScope) {
        val principal = object : McpAuthenticatedPrincipal {
            override val tokenId: Long = 1L
            override val userId: Long = userId
            override val grantedScopes: Set<McpScope> = scopes.toSet()
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    init {
        afterEach {
            SecurityContextHolder.clearContext()
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
                val result: Booking = cancelBookingUseCase.execute(
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

            When("토큰이 만료된 후 소진 시도 시") {
                await().atMost(3, TimeUnit.SECONDS).until {
                    stringRedisTemplate.opsForValue().get("mcp:confirm:$expiredToken") == null
                }
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
                val result: Slot = createSlotUseCase.execute(
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

        // ─── scope 가드 (@PreAuthorize AOP) ───────────────────────────

        Given("[S-06] write:booking scope 없는 principal 로 cancelBooking 1차 호출 시") {
            When("cancelBooking 을 호출하면") {
                setSecurityContext(userId = 99L) // scope 없음
                Then("[S-06] AccessDeniedException 이 발생한다 (403)") {
                    shouldThrow<AccessDeniedException> {
                        mcpBookingWriteTools.cancelBooking(
                            bookingId = 1L,
                            reason = null,
                            confirmationToken = null,
                        )
                    }
                }
            }
        }

        Given("[S-07] write:slot scope 없는 principal 로 createSlot 1차 호출 시") {
            When("createSlot 을 호출하면") {
                setSecurityContext(userId = 99L) // scope 없음
                Then("[S-07] AccessDeniedException 이 발생한다 (403)") {
                    shouldThrow<AccessDeniedException> {
                        mcpSlotWriteTools.createSlot(
                            facilityId = "FAC-01",
                            date = "2026-07-01T09:00:00+09:00",
                            timeRange = "09:00-10:00",
                            capacity = 5,
                            confirmationToken = null,
                        )
                    }
                }
            }
        }

        // ─── paramsHash 변조 시나리오 ─────────────────────────────────

        Given("[S-08] cancelBooking 2차 호출 시 bookingId 가 변조된 경우") {
            val scenarioCallerId = 10L
            val originalBookingId = 1L
            val tamperedBookingId = 999L

            // 1차: originalBookingId=1 로 토큰 발급
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(
                    toolName = "cancelBooking",
                    userId = scenarioCallerId,
                    paramsHash = McpParamsHasher.hash("cancelBooking", originalBookingId, scenarioCallerId),
                )
            )

            When("변조된 bookingId=999 로 2차 호출하면") {
                setSecurityContext(userId = scenarioCallerId, writeBookingScope)
                Then("[S-08] ConfirmationParamsMismatchException 이 발생한다") {
                    shouldThrow<ConfirmationParamsMismatchException> {
                        mcpBookingWriteTools.cancelBooking(
                            bookingId = tamperedBookingId,
                            reason = null,
                            confirmationToken = token,
                        )
                    }
                }
            }
        }
    }
}
