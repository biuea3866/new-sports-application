package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class MessageRepositoryTest(
    @Autowired private val messageJpaRepository: MessageJpaRepository,
    @Autowired private val messageCustomRepositoryImpl: MessageCustomRepositoryImpl,
    @Autowired private val roomJpaRepository: RoomJpaRepository,
) : BaseIntegrationTest() {

    init {
        Given("Message 저장 후 ZonedDateTime 검증") {
            val room = roomJpaRepository.save(Room.createDirect())
            val message = Message.create(room = room, userId = 1L, content = "안녕하세요")
            val saved = messageJpaRepository.save(message)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = messageJpaRepository.findByIdAndDeletedAtIsNull(saved.id)
                Then("ZonedDateTime 이 UTC 로 저장되고 조회 시 복원된다") {
                    found.shouldNotBeNull()
                    found.createdAt.shouldNotBeNull()
                    found.content shouldBe "안녕하세요"
                    found.userId shouldBe 1L
                    found.room.id shouldBe room.id
                }
            }
        }

        Given("소프트 삭제된 Message") {
            val room = roomJpaRepository.save(Room.createDirect())
            val message = messageJpaRepository.save(
                Message.create(room = room, userId = 2L, content = "삭제 예정")
            )
            message.softDelete(2L)
            messageJpaRepository.save(message)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = messageJpaRepository.findByIdAndDeletedAtIsNull(message.id)
                Then("deleted_at 필터가 적용되어 null 을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("동일 roomId 의 Message 다건 조회") {
            val room = roomJpaRepository.save(Room.createDirect())
            messageJpaRepository.save(Message.create(room, 1L, "첫 번째"))
            messageJpaRepository.save(Message.create(room, 1L, "두 번째"))

            When("findByRoomIdAndNotDeleted 로 조회하면") {
                val messages = messageCustomRepositoryImpl.findByRoomIdAndNotDeleted(room.id)
                Then("2건이 반환된다") {
                    messages shouldHaveSize 2
                }
            }
        }
    }
}
