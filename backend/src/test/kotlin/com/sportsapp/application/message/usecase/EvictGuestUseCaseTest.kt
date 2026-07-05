package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.EvictGuestCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.service.GuestEvictionDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class EvictGuestUseCaseTest : BehaviorSpec({

    val guestEvictionDomainService = mockk<GuestEvictionDomainService>()
    val evictGuestUseCase = EvictGuestUseCase(guestEvictionDomainService)

    Given("방장이 게스트 수동 방출을 요청하는 경우") {
        val room = Room.createGroup("수동 방출 UseCase 테스트")
        val guest = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)
        every { guestEvictionDomainService.evict(roomId = 1L, userId = 5L, requesterId = 1L) } returns guest

        When("execute 를 호출하면") {
            evictGuestUseCase.execute(EvictGuestCommand(roomId = 1L, userId = 5L, requesterId = 1L))

            Then("GuestEvictionDomainService.evict 가 호출된다") {
                verify { guestEvictionDomainService.evict(roomId = 1L, userId = 5L, requesterId = 1L) }
            }
        }
    }

    Given("방장이 아닌 사용자가 수동 방출을 요청하는 경우") {
        every {
            guestEvictionDomainService.evict(roomId = 1L, userId = 5L, requesterId = 2L)
        } throws NotRoomHostException(userId = 2L, roomId = 1L)

        When("execute 를 호출하면") {
            Then("NotRoomHostException 이 발생한다") {
                shouldThrow<NotRoomHostException> {
                    evictGuestUseCase.execute(EvictGuestCommand(roomId = 1L, userId = 5L, requesterId = 2L))
                }
            }
        }
    }
})
