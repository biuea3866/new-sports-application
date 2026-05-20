package com.sportsapp.domain.message

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MessageTest : BehaviorSpec({

    Given("Message.create() 호출") {
        When("정상적인 내용으로 생성하면") {
            val message = Message.create(roomId = 1L, userId = 2L, content = "안녕하세요")
            Then("[U-01] roomId, userId, content 가 설정된다") {
                message.roomId shouldBe 1L
                message.userId shouldBe 2L
                message.content shouldBe "안녕하세요"
            }
        }

        When("빈 content 로 생성하면") {
            Then("[U-02] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Message.create(roomId = 1L, userId = 2L, content = "  ")
                }
            }
        }
    }

    Given("활성 Message") {
        val message = Message.create(roomId = 1L, userId = 2L, content = "테스트")

        When("softDelete 를 호출하면") {
            message.softDelete(2L)
            Then("[U-03] isDeleted 가 true 이고 deletedBy 가 설정된다") {
                message.isDeleted shouldBe true
                message.deletedBy shouldBe 2L
            }
        }
    }
})
