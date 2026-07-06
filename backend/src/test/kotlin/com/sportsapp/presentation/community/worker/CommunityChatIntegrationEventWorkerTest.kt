package com.sportsapp.presentation.community.worker

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.community.dto.CreateCommunityCommand
import com.sportsapp.application.community.dto.JoinCommunityCommand
import com.sportsapp.application.community.dto.KickMemberCommand
import com.sportsapp.application.community.dto.LeaveCommunityCommand
import com.sportsapp.application.community.usecase.CreateCommunityUseCase
import com.sportsapp.application.community.usecase.JoinCommunityUseCase
import com.sportsapp.application.community.usecase.KickMemberUseCase
import com.sportsapp.application.community.usecase.LeaveCommunityUseCase
import com.sportsapp.domain.community.event.CommunityCreatedEvent
import com.sportsapp.domain.community.event.CommunityMemberJoinedEvent
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate

/**
 * community 도메인 이벤트(CommunityCreated/MemberJoined/MemberLeft) -> [CommunityChatIntegrationEventWorker]
 * (`@Async` + AFTER_COMMIT) -> 컨텍스트 방 provision/join/leave UseCase 연동 통합 테스트 (BE-09).
 *
 * 각 핸들러가 `@Async`라 `execute()` 반환 시점에 처리가 끝나 있다고 보장할 수 없다 — `eventually`로
 * 수렴을 기다린다(`FeatureFlagChangePropagationIntegrationTest`와 동일한 비동기 전파 검증 패턴).
 */
class CommunityChatIntegrationEventWorkerTest(
    @Autowired private val createCommunityUseCase: CreateCommunityUseCase,
    @Autowired private val joinCommunityUseCase: JoinCommunityUseCase,
    @Autowired private val leaveCommunityUseCase: LeaveCommunityUseCase,
    @Autowired private val kickMemberUseCase: KickMemberUseCase,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val roomParticipantRepository: RoomParticipantRepository,
    @Autowired private val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    init {
        Given("커뮤니티가 개설되면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "주말 축구 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.SOCCER,
                    hostUserId = 100L,
                ),
            )

            When("CommunityCreatedEvent 가 AFTER_COMMIT 이후 비동기로 소비되면") {
                Then("contextType=COMMUNITY 전용 그룹 방이 커뮤니티 이름을 가지고 자동 생성되고 방장이 참여자로 등록된다 (BE-14)") {
                    eventually(10.seconds) {
                        val room = roomRepository.findByContext(RoomContextType.COMMUNITY, community.id)
                            .shouldNotBeNull()
                        room.name shouldBe "주말 축구 모임"
                        roomParticipantRepository.existsByRoomIdAndUserId(room.id, 100L) shouldBe true
                        roomParticipantRepository.findActiveByRoomId(room.id).size shouldBe 1
                    }
                }
            }
        }

        Given("이미 컨텍스트 방이 provision 된 커뮤니티에 개설 이벤트가 중복 수신되면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "중복 이벤트 테스트 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.RUNNING,
                    hostUserId = 101L,
                ),
            )
            val roomBeforeReplay = eventually(10.seconds) {
                roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
            }

            When("동일 communityId 로 CommunityCreatedEvent 를 재발행하면") {
                transactionTemplate.execute<Unit> {
                    applicationEventPublisher.publishEvent(
                        CommunityCreatedEvent(communityId = community.id, hostUserId = 101L, name = "중복 이벤트 테스트 모임"),
                    )
                }

                Then("provision 이 새 방을 만들지 않고 동일한 방이 유지된다") {
                    eventually(10.seconds) {
                        val roomAfterReplay =
                            roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
                        roomAfterReplay.id shouldBe roomBeforeReplay.id
                        roomParticipantRepository.findActiveByRoomId(roomAfterReplay.id).size shouldBe 1
                    }
                }
            }
        }

        Given("커뮤니티에 새 멤버가 가입하면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "가입 테스트 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.BASKETBALL,
                    hostUserId = 200L,
                ),
            )
            eventually(10.seconds) {
                roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
            }
            joinCommunityUseCase.execute(JoinCommunityCommand(communityId = community.id, userId = 201L))

            When("CommunityMemberJoinedEvent 가 AFTER_COMMIT 이후 비동기로 소비되면") {
                Then("연결된 방에 참여자로 자동 등록된다") {
                    eventually(10.seconds) {
                        val room = roomRepository.findByContext(RoomContextType.COMMUNITY, community.id)
                            .shouldNotBeNull()
                        roomParticipantRepository.existsByRoomIdAndUserId(room.id, 201L) shouldBe true
                    }
                }
            }
        }

        Given("이미 참여 중인 사용자의 가입 이벤트가 재수신되면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "가입 멱등 테스트 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.TENNIS,
                    hostUserId = 300L,
                ),
            )
            val room = eventually(10.seconds) {
                roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
            }
            joinCommunityUseCase.execute(JoinCommunityCommand(communityId = community.id, userId = 301L))
            eventually(10.seconds) {
                roomParticipantRepository.existsByRoomIdAndUserId(room.id, 301L) shouldBe true
            }

            When("동일 userId 로 CommunityMemberJoinedEvent 를 재발행하면") {
                transactionTemplate.execute<Unit> {
                    applicationEventPublisher.publishEvent(
                        CommunityMemberJoinedEvent(memberId = 9999L, communityId = community.id, userId = 301L),
                    )
                }

                Then("중복 참여자가 생기지 않는다 (멱등)") {
                    eventually(10.seconds) {
                        val participants = roomParticipantRepository.findActiveByRoomId(room.id)
                        participants.count { it.userId == 301L } shouldBe 1
                    }
                }
            }
        }

        Given("커뮤니티 멤버가 탈퇴하면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "탈퇴 테스트 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.GOLF,
                    hostUserId = 400L,
                ),
            )
            val room = eventually(10.seconds) {
                roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
            }
            joinCommunityUseCase.execute(JoinCommunityCommand(communityId = community.id, userId = 401L))
            eventually(10.seconds) {
                roomParticipantRepository.existsByRoomIdAndUserId(room.id, 401L) shouldBe true
            }

            When("LeaveCommunityUseCase.execute 로 탈퇴하면") {
                leaveCommunityUseCase.execute(LeaveCommunityCommand(communityId = community.id, userId = 401L))

                Then("연결된 방에서 자동 퇴장 처리된다") {
                    eventually(10.seconds) {
                        roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, 401L).shouldBeNull()
                    }
                }
            }
        }

        Given("방장이 멤버를 강퇴하면") {
            val community = createCommunityUseCase.execute(
                CreateCommunityCommand(
                    name = "강퇴 테스트 모임",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.SWIMMING,
                    hostUserId = 500L,
                ),
            )
            val room = eventually(10.seconds) {
                roomRepository.findByContext(RoomContextType.COMMUNITY, community.id).shouldNotBeNull()
            }
            joinCommunityUseCase.execute(JoinCommunityCommand(communityId = community.id, userId = 501L))
            eventually(10.seconds) {
                roomParticipantRepository.existsByRoomIdAndUserId(room.id, 501L) shouldBe true
            }

            When("KickMemberUseCase.execute 로 강퇴하면") {
                kickMemberUseCase.execute(
                    KickMemberCommand(communityId = community.id, requesterId = 500L, targetUserId = 501L),
                )

                Then("강퇴된 멤버가 연결된 방에서 자동 퇴장 처리된다") {
                    eventually(10.seconds) {
                        roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, 501L).shouldBeNull()
                    }
                }
            }
        }
    }
}
