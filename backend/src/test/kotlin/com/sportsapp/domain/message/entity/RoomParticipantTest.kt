package com.sportsapp.domain.message.entity

import com.sportsapp.domain.message.exception.ReadOnlyParticipantException
import com.sportsapp.domain.message.exception.RoomParticipantExpiredException
import com.sportsapp.domain.message.vo.ParticipantType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class RoomParticipantTest : BehaviorSpec({

    Given("RoomParticipant.create(room, userId) 호출") {
        val room = Room.createDirect()

        When("정회원을 생성하면") {
            val participant = RoomParticipant.create(room, 1L)

            Then("participantType 은 MEMBER, canSpeak 은 true, expiresAt 은 null 이다 (기존 호환)") {
                participant.participantType shouldBe ParticipantType.MEMBER
                participant.canSpeak shouldBe true
                participant.expiresAt.shouldBeNull()
                participant.currentLastReadMessageId.shouldBeNull()
            }
        }
    }

    Given("RoomParticipant.forGuest(room, userId, canSpeak, expiresInDays) 호출") {
        val room = Room.createDirect()
        val before = ZonedDateTime.now()

        When("발화 가능한 게스트를 7일 만료로 생성하면") {
            val participant = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)
            val after = ZonedDateTime.now()

            Then("participantType 은 GUEST, canSpeak 은 true, expiresAt 은 now+7d 범위다") {
                participant.participantType shouldBe ParticipantType.GUEST
                participant.canSpeak shouldBe true
                val expiresAt = requireNotNull(participant.expiresAt)
                (expiresAt.isAfter(before.plusDays(7)) || expiresAt.isEqual(before.plusDays(7))) shouldBe true
                (expiresAt.isBefore(after.plusDays(7)) || expiresAt.isEqual(after.plusDays(7))) shouldBe true
            }
        }
    }

    Given("markReadUpTo 로 읽음 커서를 전진시킨 참여자") {
        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 1L)
        participant.markReadUpTo(100L)

        When("이전 값보다 작은 messageId 로 markReadUpTo(50) 을 호출하면") {
            participant.markReadUpTo(50L)

            Then("lastReadMessageId 는 100 으로 유지된다 (forward-only)") {
                participant.currentLastReadMessageId shouldBe 100L
            }
        }

        When("이후 값인 messageId 로 markReadUpTo(150) 을 호출하면") {
            participant.markReadUpTo(150L)

            Then("lastReadMessageId 는 150 으로 전진한다") {
                participant.currentLastReadMessageId shouldBe 150L
            }
        }
    }

    Given("읽기 전용 게스트 (canSpeak=false)") {
        val room = Room.createDirect()
        val participant = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = false, expiresInDays = 7L)

        When("validateCanSpeak 을 호출하면") {
            Then("ReadOnlyParticipantException 을 던진다") {
                shouldThrow<ReadOnlyParticipantException> {
                    participant.validateCanSpeak()
                }
            }
        }
    }

    Given("발화 가능한 참여자") {
        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 1L)

        When("validateCanSpeak 을 호출하면") {
            Then("예외 없이 통과한다") {
                participant.validateCanSpeak()
            }
        }
    }

    Given("expiresAt 이 과거인 게스트") {
        val room = Room.createDirect()
        val participant = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)

        When("validateNotExpired 를 호출하면") {
            val expired = RoomParticipant.reconstitute(
                room = room,
                userId = 5L,
                joinedAt = ZonedDateTime.now().minusDays(10),
                participantType = ParticipantType.GUEST,
                canSpeak = true,
                expiresAt = ZonedDateTime.now().minusDays(1),
                lastReadMessageId = null,
            )

            Then("RoomParticipantExpiredException 을 던진다") {
                shouldThrow<RoomParticipantExpiredException> {
                    expired.validateNotExpired()
                }
            }

            Then("아직 만료되지 않은 게스트는 예외 없이 통과한다") {
                participant.validateNotExpired()
            }
        }
    }

    Given("expiresAt 이 null 인 MEMBER 참여자") {
        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 1L)

        When("validateNotExpired 를 호출하면") {
            Then("예외 없이 통과한다") {
                participant.validateNotExpired()
            }
        }
    }

    Given("GUEST 참여자") {
        val room = Room.createDirect()
        val participant = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)

        When("evict 를 호출하면") {
            Then("예외 없이 방출 표시를 통과한다") {
                participant.evict()
            }
        }
    }

    Given("MEMBER 참여자") {
        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 1L)

        When("evict 를 호출하면") {
            Then("IllegalArgumentException 을 던진다 (정회원은 방출 대상이 아니다)") {
                shouldThrow<IllegalArgumentException> {
                    participant.evict()
                }
            }
        }

        When("isMember 를 호출하면") {
            Then("true 를 반환한다") {
                participant.isMember() shouldBe true
            }
        }
    }

    Given("GUEST 참여자 (isMember 질의)") {
        val room = Room.createDirect()
        val participant = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)

        When("isMember 를 호출하면") {
            Then("false 를 반환한다") {
                participant.isMember() shouldBe false
            }
        }
    }
})
