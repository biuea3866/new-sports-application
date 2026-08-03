package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * 초대 수신함이 `초대자 #71` 대신 초대자 표시 이름을 보여주기 위한 조합. message 도메인은 user 를
 * 모른 채로 두고, application 레이어가 UserDomainService 로 조회해 응답에 싣는다.
 */
class InvitationDisplayNameTest : BehaviorSpec({

    // InvitationResponse.of 가 읽는 JPA auditing lateinit 필드를 실제 영속화 없이 채운다.
    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val userDomainService = mockk<UserDomainService>()
    val listMyInvitationsUseCase = ListMyInvitationsUseCase(guestInvitationDomainService, userDomainService)

    fun displayNamesOf(vararg pairs: Pair<Long, String>): UserDisplayNames =
        UserDisplayNames.from(
            pairs.map { (userId, displayName) ->
                mockk<User>().also {
                    every { it.id } returns userId
                    every { it.displayName } returns displayName
                }
            },
        )

    Given("서로 다른 초대자에게 받은 PENDING 초대 2건") {
        val room = Room.createGroup("축구 모임").also { initAuditFields(it) }
        every { guestInvitationDomainService.findMyPendingInvitations(2L) } returns listOf(
            RoomInvitation.create(room, 71L, 2L, true, 7L).also { initAuditFields(it) },
            RoomInvitation.create(room, 68L, 2L, false, 3L).also { initAuditFields(it) },
        )
        every { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) } returns
            displayNamesOf(71L to "김철수", 68L to "박영희")

        When("수신함을 조회하면") {
            val invitations = listMyInvitationsUseCase.execute(2L)

            Then("PENDING 초대 목록이 반환된다") {
                invitations shouldHaveSize 2
            }

            Then("초대자 표시 이름이 함께 반환된다") {
                invitations.map { it.inviterDisplayName } shouldBe listOf("김철수", "박영희")
            }

            Then("표시 이름 조회는 초대 건수와 무관하게 1회다 (N+1 없음)") {
                verify(exactly = 1) { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) }
            }
        }
    }

    Given("닉네임을 설정하지 않은 초대자") {
        val room = Room.createGroup("농구 모임").also { initAuditFields(it) }
        every { guestInvitationDomainService.findMyPendingInvitations(9L) } returns listOf(
            RoomInvitation.create(room, 99L, 9L, true, 7L).also { initAuditFields(it) },
        )
        every { userDomainService.findDisplayNamesBy(listOf(99L)) } returns UserDisplayNames.from(emptyList())

        When("수신함을 조회하면") {
            val invitations = listMyInvitationsUseCase.execute(9L)

            Then("이메일·내부 식별자 대신 기본 표시 이름을 반환한다") {
                invitations.single().inviterDisplayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }

    Given("받은 초대가 없는 사용자") {
        every { guestInvitationDomainService.findMyPendingInvitations(3L) } returns emptyList()
        every { userDomainService.findDisplayNamesBy(emptyList()) } returns UserDisplayNames.from(emptyList())

        When("수신함을 조회하면") {
            Then("빈 목록을 반환한다") {
                listMyInvitationsUseCase.execute(3L) shouldHaveSize 0
            }
        }
    }
})
