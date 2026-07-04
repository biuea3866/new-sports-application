package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RoomCustomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val roomCustomRepositoryImpl: RoomCustomRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("1:1 룸과 두 참가자가 저장된 상태") {
            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 101L))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 102L))

            When("두 userId 로 findDirectRoomByParticipantIds 를 호출하면") {
                val found = roomCustomRepositoryImpl.findDirectRoomByParticipantIds(101L, 102L)

                Then("기존 1:1 룸이 반환된다") {
                    found.shouldNotBeNull()
                    found.type shouldBe RoomType.DIRECT
                }
            }

            When("관련 없는 userId 로 검색하면") {
                val notFound = roomCustomRepositoryImpl.findDirectRoomByParticipantIds(101L, 999L)

                Then("null 이 반환된다") {
                    notFound.shouldBeNull()
                }
            }
        }

        Given("userId=200 이 참여한 룸 중 이름이 '축구' 인 룸") {
            val matchedRoom = roomJpaRepository.save(Room.createGroup("축구 모임"))
            val unmatchedRoom = roomJpaRepository.save(Room.createGroup("농구 모임"))
            roomParticipantJpaRepository.save(RoomParticipant.create(matchedRoom, 200L))
            roomParticipantJpaRepository.save(RoomParticipant.create(unmatchedRoom, 200L))

            When("keyword='축구' 로 findMyRoomsByKeyword 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomsByKeyword(200L, "축구")

                Then("room.name 매칭 결과가 반환된다") {
                    result shouldHaveSize 1
                    result[0].name shouldBe "축구 모임"
                }
            }

            When("keyword=null 로 findMyRoomsByKeyword 를 호출하면") {
                val allRooms = roomCustomRepositoryImpl.findMyRoomsByKeyword(200L, null)

                Then("참여한 모든 룸이 반환된다") {
                    allRooms shouldHaveSize 2
                }
            }
        }

        Given("soft-delete 된 Room") {
            val room = roomJpaRepository.save(Room.createDirect())
            room.softDelete(null)
            roomJpaRepository.save(room)

            When("findDirectRoomByParticipantIds 로 삭제된 룸을 검색하면") {
                val result = roomCustomRepositoryImpl.findDirectRoomByParticipantIds(300L, 301L)

                Then("삭제된 룸은 반환되지 않는다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("contextType=COMMUNITY, contextId=10 인 컨텍스트 Room 이 저장된 상태") {
            roomJpaRepository.save(Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 10L, "주말축구"))

            When("findByContext(COMMUNITY, 10) 을 호출하면") {
                val found = roomCustomRepositoryImpl.findByContext(RoomContextType.COMMUNITY, 10L)

                Then("해당 컨텍스트 방 1건이 반환된다") {
                    found.shouldNotBeNull()
                    found.contextType shouldBe RoomContextType.COMMUNITY
                    found.contextId shouldBe 10L
                }
            }

            When("존재하지 않는 contextId 로 findByContext 를 호출하면") {
                val notFound = roomCustomRepositoryImpl.findByContext(RoomContextType.COMMUNITY, 9999L)

                Then("null 이 반환된다") {
                    notFound.shouldBeNull()
                }
            }
        }

        Given("soft-delete 된 컨텍스트 Room") {
            val contextRoom = roomJpaRepository.save(
                Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 20L, "중고거래방"),
            )
            contextRoom.softDelete(null)
            roomJpaRepository.save(contextRoom)

            When("findByContext(GOODS_PRODUCT, 20) 을 호출하면") {
                val result = roomCustomRepositoryImpl.findByContext(RoomContextType.GOODS_PRODUCT, 20L)

                Then("삭제된 컨텍스트 방은 제외되어 null 이 반환된다") {
                    result.shouldBeNull()
                }
            }
        }
    }
}
