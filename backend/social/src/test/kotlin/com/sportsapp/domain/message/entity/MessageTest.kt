package com.sportsapp.domain.message.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MessageTest : BehaviorSpec({

    Given("Message.create() 호출") {
        val room = Room.createDirect()

        When("정상적인 내용으로 생성하면") {
            val message = Message.create(room = room, userId = 2L, content = "안녕하세요")
            Then("room, userId, content 가 설정된다") {
                message.room shouldBe room
                message.userId shouldBe 2L
                message.content shouldBe "안녕하세요"
            }
        }

        When("빈 content 로 생성하면") {
            Then("IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Message.create(room = room, userId = 2L, content = "  ")
                }
            }
        }
    }

    Given("활성 Message") {
        val room = Room.createDirect()
        val message = Message.create(room = room, userId = 2L, content = "테스트")

        When("softDelete 를 호출하면") {
            message.softDelete(2L)
            Then("isDeleted 가 true 이고 deletedBy 가 설정된다") {
                message.isDeleted shouldBe true
                message.deletedBy shouldBe 2L
            }
        }
    }
})
