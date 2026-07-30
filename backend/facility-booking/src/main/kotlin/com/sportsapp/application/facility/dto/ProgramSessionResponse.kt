package com.sportsapp.application.facility.dto

import com.sportsapp.domain.booking.entity.Slot
import java.time.ZonedDateTime

/**
 * program 회차(programId를 가진 Slot) 생성 응답. Slot 은 booking 도메인 소속이나, 이 타입은
 * application.facility 레이어의 출력 DTO로 도메인 값만 옮겨 담아(재노출 아님) domain 교차
 * 참조 금지 규칙(도메인 레이어 한정) 밖에서 두 도메인 오케스트레이션 결과를 표현한다.
 */
data class ProgramSessionResponse(
    val slotId: Long,
    val programId: Long?,
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
) {
    companion object {
        fun of(slot: Slot): ProgramSessionResponse = ProgramSessionResponse(
            slotId = slot.id,
            programId = slot.programId,
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
        )
    }
}
