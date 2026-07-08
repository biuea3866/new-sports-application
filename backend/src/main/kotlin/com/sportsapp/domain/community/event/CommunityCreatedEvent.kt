package com.sportsapp.domain.community.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 커뮤니티 개설 이벤트 (TDD FR-1). [Community.create]가 개설 시점에 적재한다.
 *
 * topic 을 지정하지 않아 [com.sportsapp.infrastructure.messaging.SpringDomainEventPublisher] 경로로만 발행된다.
 * 후속 컨텍스트 방 provisioning(전용 그룹 채팅 자동 연동, FR-5)은 별도 컨슈머의 책임이다.
 *
 * [name]은 커뮤니티 이름을 실어 컨텍스트 방이 이름 없이 provision 되는 문제를 해소한다(BE-14).
 */
class CommunityCreatedEvent(
    communityId: Long,
    val hostUserId: Long,
    val name: String,
) : AbstractDomainEvent(aggregateId = communityId)
