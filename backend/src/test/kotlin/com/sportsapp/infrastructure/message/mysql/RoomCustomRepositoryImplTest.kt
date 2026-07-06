package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired

class RoomCustomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val messageJpaRepository: MessageJpaRepository,
    @Autowired private val roomCustomRepositoryImpl: RoomCustomRepositoryImpl,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
) : BaseIntegrationTest() {

    private fun preparedStatementCount(): Long =
        entityManagerFactory.unwrap(SessionFactory::class.java).statistics.prepareStatementCount

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

            When("keyword='축구' 로 findMyRoomViews 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomViews(200L, "축구")

                Then("room.name 매칭 결과가 반환된다") {
                    result shouldHaveSize 1
                    result[0].name shouldBe "축구 모임"
                }
            }

            When("keyword=null 로 findMyRoomViews 를 호출하면") {
                val allRooms = roomCustomRepositoryImpl.findMyRoomViews(200L, null)

                Then("참여한 모든 룸이 반환된다") {
                    allRooms shouldHaveSize 2
                }
            }
        }

        Given("메시지가 2건 있는 룸에 userId=500 이 참여한 상태") {
            val room = roomJpaRepository.save(Room.createGroup("메시지 있는 방"))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 500L))
            messageJpaRepository.save(Message.create(room, 500L, "첫 번째 메시지"))
            val secondMessage = messageJpaRepository.save(Message.create(room, 500L, "두 번째 메시지"))

            When("findMyRoomViews 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomViews(500L, null)

                Then("가장 마지막(가장 큰 id) 메시지가 lastMessageContent·lastMessageAt 으로 반환된다") {
                    result shouldHaveSize 1
                    result[0].lastMessageContent shouldBe "두 번째 메시지"
                    result[0].lastMessageAt shouldBe secondMessage.createdAt
                }
            }
        }

        Given("메시지가 없는 룸에 userId=501 이 참여한 상태") {
            val room = roomJpaRepository.save(Room.createGroup("메시지 없는 방"))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 501L))

            When("findMyRoomViews 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomViews(501L, null)

                Then("lastMessageContent·lastMessageAt 이 모두 null 이다") {
                    result shouldHaveSize 1
                    result[0].lastMessageContent.shouldBeNull()
                    result[0].lastMessageAt.shouldBeNull()
                }
            }
        }

        Given("마지막 메시지가 soft-delete 된 룸에 userId=502 이 참여한 상태") {
            val room = roomJpaRepository.save(Room.createGroup("마지막 메시지 삭제된 방"))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 502L))
            val firstMessage = messageJpaRepository.save(Message.create(room, 502L, "살아있는 메시지"))
            val lastMessage = messageJpaRepository.save(Message.create(room, 502L, "삭제될 메시지"))
            lastMessage.softDelete(502L)
            messageJpaRepository.save(lastMessage)

            When("findMyRoomViews 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomViews(502L, null)

                Then("삭제되지 않은 이전 메시지가 lastMessageContent 로 반환된다") {
                    result shouldHaveSize 1
                    result[0].lastMessageContent shouldBe "살아있는 메시지"
                    result[0].lastMessageAt shouldBe firstMessage.createdAt
                }
            }
        }

        Given("기존 DIRECT 룸(컨텍스트 없음)에 userId=503 이 참여한 상태") {
            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, 503L))

            When("findMyRoomViews 를 호출하면") {
                val result = roomCustomRepositoryImpl.findMyRoomViews(503L, null)

                Then("contextType 이 null 로 반환된다") {
                    result shouldHaveSize 1
                    result[0].contextType.shouldBeNull()
                }
            }
        }

        Given("userId=600 이 참여한 룸이 3개이고 각각 메시지가 1건씩 있는 상태") {
            val rooms = (1..3).map { index ->
                val room = roomJpaRepository.save(Room.createGroup("N+1 검증방 $index"))
                roomParticipantJpaRepository.save(RoomParticipant.create(room, 600L))
                messageJpaRepository.save(Message.create(room, 600L, "메시지 $index"))
                room
            }

            When("findMyRoomViews 를 호출하면") {
                val before = preparedStatementCount()
                val result = roomCustomRepositoryImpl.findMyRoomViews(600L, null)
                val after = preparedStatementCount()

                Then("방 개수(3개)와 무관하게 단일 쿼리(prepared statement 1건)로 조회된다") {
                    result shouldHaveSize rooms.size
                    (after - before) shouldBe 1L
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

        Given("productId=30 에 대해 구매자별로 서로 다른 GOODS_PRODUCT 컨텍스트 방이 2건 있는 상태 (BE-11)") {
            val roomForBuyerA = roomJpaRepository.save(
                Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 30L, null),
            )
            roomParticipantJpaRepository.save(RoomParticipant.create(roomForBuyerA, 401L))
            roomParticipantJpaRepository.save(RoomParticipant.create(roomForBuyerA, 402L))

            val roomForBuyerB = roomJpaRepository.save(
                Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 30L, null),
            )
            roomParticipantJpaRepository.save(RoomParticipant.create(roomForBuyerB, 403L))
            roomParticipantJpaRepository.save(RoomParticipant.create(roomForBuyerB, 402L))

            When("findByContextAndParticipant(GOODS_PRODUCT, 30, 401) 을 호출하면") {
                val found = roomCustomRepositoryImpl.findByContextAndParticipant(RoomContextType.GOODS_PRODUCT, 30L, 401L)

                Then("구매자 A(401) 가 참여한 방이 반환된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe roomForBuyerA.id
                }
            }

            When("findByContextAndParticipant(GOODS_PRODUCT, 30, 403) 을 호출하면") {
                val found = roomCustomRepositoryImpl.findByContextAndParticipant(RoomContextType.GOODS_PRODUCT, 30L, 403L)

                Then("구매자 B(403) 가 참여한 방이 반환된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe roomForBuyerB.id
                }
            }

            When("findByContextAndParticipant(GOODS_PRODUCT, 30, 999) 를 참여하지 않은 사용자로 호출하면") {
                val notFound = roomCustomRepositoryImpl.findByContextAndParticipant(RoomContextType.GOODS_PRODUCT, 30L, 999L)

                Then("null 이 반환된다") {
                    notFound.shouldBeNull()
                }
            }
        }
    }
}
