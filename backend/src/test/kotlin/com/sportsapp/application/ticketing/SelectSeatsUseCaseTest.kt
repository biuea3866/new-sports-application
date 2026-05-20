package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.ticketing.exception.SeatAlreadyLockedException
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SelectSeatsUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val selectSeatsUseCase = SelectSeatsUseCase(ticketingDomainService)
    val releaseSeatsUseCase = ReleaseSeatsUseCase(ticketingDomainService)

    Given("이벤트 1번에 좌석 [10, 20]이 비어 있을 때") {
        val command = SelectSeatsCommand(eventId = 1L, seatIds = listOf(10L, 20L), userId = 7L)
        every { ticketingDomainService.tryLockSeats(1L, listOf(10L, 20L), 7L) } returns "1:10,1:20"

        When("[U-01 happy path] selectSeatsUseCase.execute를 호출하면") {
            val response = selectSeatsUseCase.execute(command)

            Then("lockId와 expiresAt이 반환된다") {
                response.lockId shouldBe "1:10,1:20"
                response.expiresAt shouldNotBe null
            }
        }
    }

    Given("이벤트 1번의 좌석 20이 이미 다른 사용자에게 잠겨 있을 때") {
        val command = SelectSeatsCommand(eventId = 1L, seatIds = listOf(10L, 20L), userId = 9L)
        every {
            ticketingDomainService.tryLockSeats(1L, listOf(10L, 20L), 9L)
        } throws SeatAlreadyLockedException(1L, 20L)

        When("[U-01 rollback] 락 획득 실패 시") {
            Then("SeatAlreadyLockedException이 전파되고 이미 획득한 좌석이 롤백된다") {
                shouldThrow<SeatAlreadyLockedException> {
                    selectSeatsUseCase.execute(command)
                }
                verify(exactly = 1) { ticketingDomainService.tryLockSeats(1L, listOf(10L, 20L), 9L) }
            }
        }
    }

    Given("좌석 ID 목록이 비어 있을 때") {
        When("[U-03] 빈 seatIds로 SelectSeatsCommand 생성 시") {
            Then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    SelectSeatsCommand(eventId = 1L, seatIds = emptyList(), userId = 7L)
                }
            }
        }

        When("[U-03b] 빈 seatIds로 ReleaseSeatsCommand 생성 시") {
            Then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    ReleaseSeatsCommand(eventId = 1L, seatIds = emptyList(), userId = 7L)
                }
            }
        }
    }

    Given("사용자 7번이 이벤트 1번 좌석 [10, 20] 락을 보유하고 있을 때") {
        val releaseCommand = ReleaseSeatsCommand(eventId = 1L, seatIds = listOf(10L, 20L), userId = 7L)
        every { ticketingDomainService.releaseSeats(1L, listOf(10L, 20L), 7L) } returns Unit

        When("[U-02] releaseSeatsUseCase.execute를 호출하면") {
            releaseSeatsUseCase.execute(releaseCommand)

            Then("DomainService.releaseSeats가 호출된다") {
                verify(exactly = 1) { ticketingDomainService.releaseSeats(1L, listOf(10L, 20L), 7L) }
            }
        }
    }

    Given("좌석 10이 다른 사용자(userId=99)가 소유한 락인 상태에서") {
        val releaseCommand = ReleaseSeatsCommand(eventId = 1L, seatIds = listOf(10L), userId = 7L)
        every {
            ticketingDomainService.releaseSeats(1L, listOf(10L), 7L)
        } throws SeatNotLockOwnerException(1L, 10L)

        When("[U-04] 다른 사용자가 release 호출 시") {
            Then("SeatNotLockOwnerException(FORBIDDEN)이 전파된다") {
                shouldThrow<SeatNotLockOwnerException> {
                    releaseSeatsUseCase.execute(releaseCommand)
                }
            }
        }
    }
})
