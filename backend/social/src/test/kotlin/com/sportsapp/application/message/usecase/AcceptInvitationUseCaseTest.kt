package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class AcceptInvitationUseCaseTest : BehaviorSpec({

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
    val acceptInvitationUseCase = AcceptInvitationUseCase(guestInvitationDomainService, userDomainService)

    Given("초대 대상 본인이 초대를 수락하는 상황") {
        val room = Room.createGroup("축구 모임").also { initAuditFields(it) }
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L).also { initAuditFields(it) }
        invitation.accept()
        every { guestInvitationDomainService.accept(invitationId = 5L, userId = 2L) } returns invitation

        When("execute(invitationId=5, userId=2) 를 호출하면") {
            val result = acceptInvitationUseCase.execute(invitationId = 5L, userId = 2L)

            Then("GuestInvitationDomainService.accept 결과가 초대자 표시 이름과 함께 반환된다") {
                result.id shouldBe invitation.id
                result.inviterUserId shouldBe invitation.inviterUserId
                result.status shouldBe invitation.currentStatus
                verify(exactly = 1) { guestInvitationDomainService.accept(invitationId = 5L, userId = 2L) }
            }
        }
    }
})
