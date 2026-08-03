package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InviteGuestCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.message.vo.InvitationResult
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class InviteGuestUseCaseTest : BehaviorSpec({

    // InvitationResponse.of 가 읽는 JPA auditing lateinit 필드를 실제 영속화 없이 채운다.
    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    val userDomainService = mockk<UserDomainService>(relaxed = true)
    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val inviteGuestUseCase = InviteGuestUseCase(guestInvitationDomainService, userDomainService)

    Given("방장이 게스트를 초대하는 커맨드") {
        val room = Room.createGroup("축구 모임").also { initAuditFields(it) }
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L).also { initAuditFields(it) }
        val invitationResult = InvitationResult(invitation = invitation, reused = false)
        val command = InviteGuestCommand(
            roomId = 10L,
            inviterUserId = 1L,
            inviteeUserId = 2L,
            canSpeak = true,
            expiresInDays = 7L,
        )
        every {
            guestInvitationDomainService.invite(
                roomId = 10L,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                canSpeak = true,
                expiresInDays = 7L,
            )
        } returns invitationResult

        When("execute 를 호출하면") {
            val result = inviteGuestUseCase.execute(command)

            Then("GuestInvitationDomainService.invite 결과가 초대자 표시 이름과 함께 반환된다") {
                result.id shouldBe invitation.id
                result.inviterUserId shouldBe invitation.inviterUserId
                result.reused shouldBe false
                verify(exactly = 1) {
                    guestInvitationDomainService.invite(
                        roomId = 10L,
                        inviterUserId = 1L,
                        inviteeUserId = 2L,
                        canSpeak = true,
                        expiresInDays = 7L,
                    )
                }
            }
        }
    }
})
