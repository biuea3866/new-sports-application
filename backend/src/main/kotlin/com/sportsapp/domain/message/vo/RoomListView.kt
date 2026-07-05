package com.sportsapp.domain.message.vo

import com.querydsl.core.annotations.QueryProjection
import java.time.ZonedDateTime

/**
 * 방목록 미리보기 projection (TDD "방목록 미리보기 N+1 회피", FR-9).
 *
 * `RoomCustomRepositoryImpl.findMyRoomViews`가 rooms + 마지막 메시지 1건을 단일 쿼리 조인으로
 * 조회해 반환하는 QueryDSL projection 이다. [lastMessageContent]는 원문이며, 최대 50자로 자르는
 * 로직은 `RoomResponse.of`(presentation)에 위치한다.
 */
data class RoomListView @QueryProjection constructor(
    val roomId: Long,
    val type: RoomType,
    val name: String?,
    val contextType: RoomContextType?,
    val lastMessageContent: String?,
    val lastMessageAt: ZonedDateTime?,
)
