package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.vo.InvitationStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RoomInvitationCustomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomInvitationJpaRepository: RoomInvitationJpaRepository,
    @Autowired private val roomInvitationCustomRepositoryImpl: RoomInvitationCustomRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("동일 room 에 PENDING 과 ACCEPTED 초대가 각각 저장된 상태") {
            val room = roomJpaRepository.save(Room.createGroup("축구 모임"))
            val pendingInvitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
            val acceptedInvitation = RoomInvitation.create(room, 1L, 3L, true, 7L)
            acceptedInvitation.accept()
            roomInvitationJpaRepository.save(pendingInvitation)
            roomInvitationJpaRepository.save(acceptedInvitation)

            When("findPendingBy(roomId, 2L) 를 호출하면") {
                val result = roomInvitationCustomRepositoryImpl.findPendingBy(room.id, 2L)

                Then("PENDING 초대가 반환된다") {
                    result.shouldNotBeNull()
                    result.currentStatus shouldBe InvitationStatus.PENDING
                }
            }

            When("findPendingBy(roomId, 3L) 를 호출하면 (이미 ACCEPTED)") {
                val result = roomInvitationCustomRepositoryImpl.findPendingBy(room.id, 3L)

                Then("null 이 반환된다") {
                    result.shouldBeNull()
                }
            }

            When("findPendingByInvitee(2L) 를 호출하면") {
                val result = roomInvitationCustomRepositoryImpl.findPendingByInvitee(2L)

                Then("PENDING 초대 1건만 포함된다") {
                    result shouldHaveSize 1
                    result[0].inviteeUserId shouldBe 2L
                }
            }

            When("findPendingByInvitee(3L) 를 호출하면 (ACCEPTED 는 제외)") {
                val result = roomInvitationCustomRepositoryImpl.findPendingByInvitee(3L)

                Then("빈 리스트가 반환된다") {
                    result.shouldBeEmpty()
                }
            }
        }
    }
}
