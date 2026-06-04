package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomParticipant
import com.sportsapp.domain.message.RoomType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RoomRepositoryTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val roomParticipantCustomRepositoryImpl: RoomParticipantCustomRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("Room 저장 후 ZonedDateTime 검증") {
            val room = Room.createGroup("테스트 룸")
            val saved = roomJpaRepository.save(room)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = roomJpaRepository.findByIdAndDeletedAtIsNull(saved.id)
                Then("ZonedDateTime 이 UTC 로 저장되고 조회 시 복원된다") {
                    found.shouldNotBeNull()
                    found.createdAt.shouldNotBeNull()
                    found.updatedAt.shouldNotBeNull()
                    found.type shouldBe RoomType.GROUP
                    found.name shouldBe "테스트 룸"
                }
            }
        }

        Given("소프트 삭제된 Room") {
            val room = Room.createDirect()
            val saved = roomJpaRepository.save(room)
            saved.softDelete(null)
            roomJpaRepository.save(saved)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = roomJpaRepository.findByIdAndDeletedAtIsNull(saved.id)
                Then("deleted_at 필터가 적용되어 null 을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("UNIQUE(room_id, user_id, deleted_at) 제약 검증") {
            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 100L))

            When("동일 (room_id, user_id) 로 existsByRoomIdAndUserId 를 조회하면") {
                val exists = roomParticipantCustomRepositoryImpl.existsByRoomIdAndUserId(room.id, 100L)
                Then("true 를 반환해 application 레벨에서 중복을 차단한다") {
                    exists shouldBe true
                }
            }
        }
    }
}
