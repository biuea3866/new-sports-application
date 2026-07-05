package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

class MessageCustomRepositoryImplTest(
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val messageJpaRepository: MessageJpaRepository,
    @Autowired private val messageCustomRepositoryImpl: MessageCustomRepositoryImpl,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseIntegrationTest() {

    init {
        Given("커서 없이 메시지 목록을 조회할 때") {
            val room = roomJpaRepository.save(Room.createDirect())
            repeat(5) { index ->
                messageJpaRepository.save(Message.create(room, 1L, "메시지$index"))
            }

            When("before=null 로 findByCursor 를 호출하면") {
                val result = messageCustomRepositoryImpl.findByCursor(room.id, null, 30)

                Then("5건이 createdAt 내림차순으로 반환된다") {
                    result shouldHaveSize 5
                }
            }
        }

        Given("커서 기반 페이지네이션") {
            val room = roomJpaRepository.save(Room.createDirect())
            val saved = (1..35).map { index ->
                messageJpaRepository.save(Message.create(room, 1L, "메시지$index"))
            }
            val cursor = saved[4].createdAt

            When("before=cursor(5번째 메시지 createdAt) 로 findByCursor 를 호출하면") {
                val result = messageCustomRepositoryImpl.findByCursor(room.id, cursor, 30)

                Then("cursor 이전 메시지 4건 이하가 반환된다") {
                    result.size shouldNotBe 0
                    result.all { it.createdAt < cursor } shouldBe true
                }
            }
        }

        Given("pageSize 를 초과하는 메시지가 있을 때") {
            val room = roomJpaRepository.save(Room.createDirect())
            repeat(35) { index ->
                messageJpaRepository.save(Message.create(room, 1L, "메시지$index"))
            }

            When("pageSize=30 으로 findByCursor 를 호출하면") {
                val result = messageCustomRepositoryImpl.findByCursor(room.id, null, 30)

                Then("최대 30건만 반환된다") {
                    result shouldHaveSize 30
                }
            }
        }

        Given("소프트 삭제된 메시지가 포함된 룸") {
            val room = roomJpaRepository.save(Room.createDirect())
            val activeMessage = messageJpaRepository.save(Message.create(room, 1L, "활성"))
            val deletedMessage = messageJpaRepository.save(Message.create(room, 1L, "삭제됨"))
            deletedMessage.softDelete(1L)
            messageJpaRepository.save(deletedMessage)

            When("findByCursor 를 호출하면") {
                val result = messageCustomRepositoryImpl.findByCursor(room.id, null, 30)

                Then("소프트 삭제된 메시지는 제외되고 1건만 반환된다") {
                    result shouldHaveSize 1
                    result.first().id shouldBe activeMessage.id
                }
            }
        }

        Given("룸에 메시지가 2건 있을 때") {
            val room = roomJpaRepository.save(Room.createDirect())
            messageJpaRepository.save(Message.create(room, 1L, "첫 번째"))
            messageJpaRepository.save(Message.create(room, 1L, "두 번째"))

            When("softDeleteAllByRoomId 를 호출하면") {
                transactionTemplate.execute {
                    messageCustomRepositoryImpl.softDeleteAllByRoomId(room.id, 1L)
                }

                Then("해당 룸의 모든 메시지가 soft-delete 되어 0건이 조회된다") {
                    val remaining = messageCustomRepositoryImpl.findByCursor(room.id, null, 30)
                    remaining shouldHaveSize 0
                }
            }
        }

        Given("읽음 커서 이후 상대 메시지 10건이 쌓인 룸") {
            val room = roomJpaRepository.save(Room.createDirect())
            val meId = 101L
            val otherId = 102L
            val cursorMessage = messageJpaRepository.save(Message.create(room, meId, "커서 지점"))
            repeat(10) { index ->
                messageJpaRepository.save(Message.create(room, otherId, "안읽은 메시지$index"))
            }

            When("countUnread(roomId, cursorMessage.id, meId) 를 호출하면") {
                val result = messageCustomRepositoryImpl.countUnread(room.id, cursorMessage.id, meId)

                Then("커서 이후 상대 메시지 10건이 반환된다") {
                    result shouldBe 10L
                }
            }
        }

        Given("커서 이후 본인 메시지와 상대 메시지가 섞여 있는 룸") {
            val room = roomJpaRepository.save(Room.createDirect())
            val meId = 201L
            val otherId = 202L
            val cursorMessage = messageJpaRepository.save(Message.create(room, meId, "커서 지점"))
            messageJpaRepository.save(Message.create(room, meId, "본인이 보낸 메시지"))
            messageJpaRepository.save(Message.create(room, otherId, "상대가 보낸 메시지"))

            When("countUnread(roomId, cursorMessage.id, meId) 를 호출하면") {
                val result = messageCustomRepositoryImpl.countUnread(room.id, cursorMessage.id, meId)

                Then("본인이 보낸 메시지는 제외되고 상대 메시지 1건만 반환된다") {
                    result shouldBe 1L
                }
            }
        }

        Given("커서 이후 안읽은 메시지가 없는 룸") {
            val room = roomJpaRepository.save(Room.createDirect())
            val meId = 301L
            val otherId = 302L
            val lastMessage = messageJpaRepository.save(Message.create(room, otherId, "마지막 메시지"))

            When("countUnread(roomId, lastMessage.id, meId) 를 호출하면") {
                val result = messageCustomRepositoryImpl.countUnread(room.id, lastMessage.id, meId)

                Then("unreadCount 는 0 이다 (빈 상태)") {
                    result shouldBe 0L
                }
            }
        }

        Given("커서 이후 상대 메시지 중 하나가 soft-delete 된 룸") {
            val room = roomJpaRepository.save(Room.createDirect())
            val meId = 401L
            val otherId = 402L
            val cursorMessage = messageJpaRepository.save(Message.create(room, meId, "커서 지점"))
            messageJpaRepository.save(Message.create(room, otherId, "활성 메시지"))
            val deletedMessage = messageJpaRepository.save(Message.create(room, otherId, "삭제된 메시지"))
            deletedMessage.softDelete(otherId)
            messageJpaRepository.save(deletedMessage)

            When("countUnread(roomId, cursorMessage.id, meId) 를 호출하면") {
                val result = messageCustomRepositoryImpl.countUnread(room.id, cursorMessage.id, meId)

                Then("soft-delete 된 메시지는 제외되고 활성 메시지 1건만 반환된다") {
                    result shouldBe 1L
                }
            }
        }
    }
}
