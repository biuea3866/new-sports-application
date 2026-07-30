package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired

class RoomParticipantCustomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val roomParticipantCustomRepositoryImpl: RoomParticipantCustomRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("만료 시각이 지난 게스트와 지나지 않은 게스트, 그리고 MEMBER 가 저장된 상태") {
            val room = roomJpaRepository.save(Room.createDirect())
            val expiredGuest = RoomParticipant.reconstitute(
                room = room,
                userId = 10L,
                joinedAt = ZonedDateTime.now().minusDays(10),
                participantType = com.sportsapp.domain.message.vo.ParticipantType.GUEST,
                canSpeak = true,
                expiresAt = ZonedDateTime.now().minusDays(1),
                lastReadMessageId = null,
            )
            val activeGuest = RoomParticipant.forGuest(room = room, userId = 11L, canSpeak = true, expiresInDays = 7L)
            val member = RoomParticipant.create(room, 12L)
            roomParticipantJpaRepository.save(expiredGuest)
            roomParticipantJpaRepository.save(activeGuest)
            roomParticipantJpaRepository.save(member)

            When("findExpiredGuestsBefore(now) 를 호출하면") {
                val result = roomParticipantCustomRepositoryImpl.findExpiredGuestsBefore(ZonedDateTime.now())

                Then("만료된 게스트만 포함되고 만료되지 않은 게스트·MEMBER 는 제외된다") {
                    result shouldHaveSize 1
                    result[0].userId shouldBe 10L
                }
            }

            When("만료되지 않은 시점(과거)을 기준으로 findExpiredGuestsBefore 를 호출하면") {
                val result = roomParticipantCustomRepositoryImpl.findExpiredGuestsBefore(ZonedDateTime.now().minusDays(30))

                Then("빈 리스트가 반환된다") {
                    result.shouldBeEmpty()
                }
            }

            When("findActiveByUserId(11) 를 호출하면") {
                val result = roomParticipantCustomRepositoryImpl.findActiveByUserId(11L)

                Then("해당 유저의 활성 참여 레코드가 반환된다") {
                    result shouldHaveSize 1
                    result[0].userId shouldBe 11L
                }
            }
        }

        Given("soft-delete 된 참여자") {
            val room = roomJpaRepository.save(Room.createDirect())
            val participant = roomParticipantJpaRepository.save(RoomParticipant.create(room, 20L))
            participant.softDelete(null)
            roomParticipantJpaRepository.save(participant)

            When("findActiveByUserId(20) 를 호출하면") {
                val result = roomParticipantCustomRepositoryImpl.findActiveByUserId(20L)

                Then("삭제된 참여자는 제외되어 빈 리스트가 반환된다") {
                    result.shouldBeEmpty()
                }
            }
        }
    }
}
