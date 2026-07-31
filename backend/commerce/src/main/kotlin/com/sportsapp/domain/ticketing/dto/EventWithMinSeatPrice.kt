package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import java.math.BigDecimal

/**
 * 경기 + 최저 좌석가 — 통합 카탈로그가 경기 항목의 대표가로 사용한다.
 *
 * 좌석이 아직 등록되지 않은 경기는 대표가를 정할 수 없어 [minSeatPrice]가 null이다.
 */
data class EventWithMinSeatPrice(
    val event: Event,
    val minSeatPrice: BigDecimal?,
)
