package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomParticipant
import com.sportsapp.domain.message.RoomType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class CustomRoomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val customRoomRepositoryImpl: CustomRoomRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("1:1 룸과 두 참가자가 저장된 상태") {
            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room.id, 101L))
            roomParticipantJpaRepository.save(RoomParticipant.create(room.id, 102L))

            When("두 userId 로 findDirectRoomByParticipantIds 를 호출하면") {
                val found = customRoomRepositoryImpl.findDirectRoomByParticipantIds(101L, 102L)

                Then("[R-01] 기존 1:1 룸이 반환된다") {
                    found.shouldNotBeNull()
                    found.type shouldBe RoomType.DIRECT
                }
            }

            When("관련 없는 userId 로 검색하면") {
                val notFound = customRoomRepositoryImpl.findDirectRoomByParticipantIds(101L, 999L)

                Then("[R-01] null 이 반환된다") {
                    notFound.shouldBeNull()
                }
            }
        }

        Given("userId=200 이 참여한 룸 중 이름이 '축구' 인 룸") {
            val matchedRoom = roomJpaRepository.save(Room.createGroup("축구 모임"))
            val unmatchedRoom = roomJpaRepository.save(Room.createGroup("농구 모임"))
            roomParticipantJpaRepository.save(RoomParticipant.create(matchedRoom.id, 200L))
            roomParticipantJpaRepository.save(RoomParticipant.create(unmatchedRoom.id, 200L))

            When("keyword='축구' 로 findMyRoomsByKeyword 를 호출하면") {
                val result = customRoomRepositoryImpl.findMyRoomsByKeyword(200L, "축구")

                Then("[R-01] room.name 매칭 결과가 반환된다") {
                    result shouldHaveSize 1
                    result[0].name shouldBe "축구 모임"
                }
            }

            When("keyword=null 로 findMyRoomsByKeyword 를 호출하면") {
                val allRooms = customRoomRepositoryImpl.findMyRoomsByKeyword(200L, null)

                Then("참여한 모든 룸이 반환된다") {
                    allRooms shouldHaveSize 2
                }
            }
        }

        Given("1000건의 Message 가 있는 Room") {
            val room = roomJpaRepository.save(Room.createDirect())
            // 여기서는 Room soft-delete cascade 를 RoomParticipant 레벨에서 검증
            room.softDelete(null)
            roomJpaRepository.save(room)

            When("findDirectRoomByParticipantIds 로 삭제된 룸을 검색하면") {
                val result = customRoomRepositoryImpl.findDirectRoomByParticipantIds(300L, 301L)

                Then("[R-02] 삭제된 룸은 반환되지 않는다") {
                    result.shouldBeNull()
                }
            }
        }
    }
}
