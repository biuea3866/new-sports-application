package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * 컨텍스트 방(예: COMMUNITY) provision·자동 가입·자동 퇴장 오케스트레이션 (BE-09).
 * `Room.createForContext`(BE-03a)·`RoomParticipantRepository.existsByRoomIdAndUserId` 재사용으로
 * 방 중복 생성·참여자 중복 등록을 방지한다.
 *
 * Kotest `BehaviorSpec`은 트리 전체를 단일 패스로 순차 실행하므로(각 leaf마다 재실행되지 않는다),
 * `Given` 블록마다 mock을 새로 만들어 앞선 블록의 호출 이력이 뒤 블록의
 * `verify(exactly = 0)` 검증에 섞여 들지 않도록 격리한다.
 *
 * provision·join·leave는 모두 `@Async` AFTER_COMMIT으로 독립 실행되는 순서 경합이 있다(PR #270
 * 리뷰 p3) — join/leave가 provision보다 먼저 도착하는 경우를 "방 미존재" 케이스로 재현해
 * 크래시 없이 스킵(+WARN 로깅)됨을 검증한다.
 */
class RoomContextDomainServiceTest : BehaviorSpec({

    Given("컨텍스트에 연결된 방이 아직 없는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 10L) } returns null
        val roomSlot = slot<Room>()
        every { roomRepository.save(capture(roomSlot)) } answers { roomSlot.captured }
        every { roomParticipantRepository.save(any()) } answers { firstArg() }

        When("provision 을 호출하면") {
            val result = service.provision(
                contextType = RoomContextType.COMMUNITY,
                contextId = 10L,
                name = "주말 축구 모임",
                hostUserId = 1L,
            )

            Then("전용 그룹 방이 생성되고 방장이 참여자로 등록된다") {
                result.type shouldBe RoomType.GROUP
                result.contextType shouldBe RoomContextType.COMMUNITY
                result.contextId shouldBe 10L
                verify(exactly = 1) { roomRepository.save(any()) }
                verify(exactly = 1) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트에 연결된 방이 이미 있는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val existingRoom = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 20L, "기존 방")
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 20L) } returns existingRoom

        When("provision 을 다시 호출하면") {
            val result = service.provision(
                contextType = RoomContextType.COMMUNITY,
                contextId = 20L,
                name = "기존 방",
                hostUserId = 1L,
            )

            Then("새 방을 만들지 않고 기존 방을 그대로 반환한다") {
                result shouldBe existingRoom
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트 방이 있고 사용자가 아직 참여자가 아닌 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 30L, "방")
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 30L) } returns room
        every { roomParticipantRepository.existsByRoomIdAndUserId(room.id, 2L) } returns false
        every { roomParticipantRepository.save(any()) } answers { firstArg() }

        When("joinContext 를 호출하면") {
            service.joinContext(RoomContextType.COMMUNITY, 30L, 2L)

            Then("참여자로 새로 등록된다") {
                verify(exactly = 1) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트 방이 있고 사용자가 이미 참여자인 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 40L, "방")
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 40L) } returns room
        every { roomParticipantRepository.existsByRoomIdAndUserId(room.id, 3L) } returns true

        When("동일 사용자의 가입 이벤트를 재수신해도") {
            service.joinContext(RoomContextType.COMMUNITY, 40L, 3L)

            Then("중복 참여자가 생기지 않는다 (멱등)") {
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트 방이 아직 provision 되지 않은 상태에서 가입 이벤트를 수신하면") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 50L) } returns null

        When("joinContext 를 호출해도 (provision·join 순서 경합 재현)") {
            Then("예외 없이 크래시하지 않고 아무 참여자도 등록되지 않는다 (WARN 로깅 후 스킵)") {
                shouldNotThrowAny {
                    service.joinContext(RoomContextType.COMMUNITY, 50L, 4L)
                }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트 방이 아직 provision 되지 않은 상태에서 탈퇴 이벤트를 수신하면") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 55L) } returns null

        When("leaveContext 를 호출해도 (provision·leave 순서 경합 재현)") {
            Then("예외 없이 크래시하지 않고 아무 작업도 하지 않는다 (WARN 로깅 후 스킵)") {
                shouldNotThrowAny {
                    service.leaveContext(RoomContextType.COMMUNITY, 55L, 4L)
                }
                verify(exactly = 0) { roomParticipantRepository.findActiveByRoomIdAndUserId(any(), any()) }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("컨텍스트 방에 활성 참여자가 있는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 60L, "방")
        val participant = RoomParticipant.create(room, 5L)
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 60L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, 5L) } returns participant
        every { roomParticipantRepository.save(any()) } answers { firstArg() }

        When("leaveContext 를 호출하면") {
            service.leaveContext(RoomContextType.COMMUNITY, 60L, 5L)

            Then("참여자가 소프트 삭제되어 방에서 자동 퇴장 처리된다") {
                participant.isDeleted shouldBe true
                verify(exactly = 1) { roomParticipantRepository.save(participant) }
            }
        }
    }

    Given("컨텍스트 방에 해당 사용자의 활성 참여 기록이 없는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 70L, "방")
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 70L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, 6L) } returns null

        When("leaveContext 를 호출해도") {
            service.leaveContext(RoomContextType.COMMUNITY, 70L, 6L)

            Then("예외 없이 아무 작업도 하지 않는다") {
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }
})
