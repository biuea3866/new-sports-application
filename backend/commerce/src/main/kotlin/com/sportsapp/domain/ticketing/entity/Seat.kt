package com.sportsapp.domain.ticketing.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "seats")
class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    @Column(nullable = false, length = 50)
    val section: String,

    @Column(name = "row_no", nullable = false, length = 10)
    val rowNo: String,

    @Column(name = "seat_no", nullable = false, length = 10)
    val seatNo: String,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,
) : JpaAuditingBase() {

    /**
     * 좌석을 사람이 읽는 한 줄 표기 — 구역·열·번호 조합(예: `R석 2열 05`).
     *
     * - seatNo 에 구역명이 이미 접두로 들어오는 데이터가 있어(예: section "R석" + seatNo
     *   "R석-01") 그대로 이어붙이면 "R석 1열 R석-01" 처럼 등급이 중복 표기된다. 모바일
     *   `seat-format.ts#stripSectionPrefix` 와 동일한 규칙으로 접두를 한 번만 남긴다.
     * - rowNo 는 현재 유일한 좌석 생성 경로인 `CreateMyEventRequest`(포털 경기 등록)가
     *   열 정보를 수집하지 않고 [UNSPECIFIED_ROW_NO] 로 고정 저장한다. 그 값을 실제 열인
     *   것처럼 "N열"로 표기하면 수집하지 않은 사실을 사실처럼 보여주는 것이므로, 그 값일
     *   때는 열 파트를 생략한다. 값만으로는 "진짜 1열"과 구분할 수 없다는 한계가 있다 —
     *   향후 열 수집 기능이 추가되면 이 상수 의존을 재검토해야 한다.
     */
    val displayLabel: String
        get() {
            val seatNumber = stripSectionPrefix(seatNo)
            val rowPart = if (rowNo == UNSPECIFIED_ROW_NO) null else "${rowNo}열"
            return listOfNotNull(section, rowPart, seatNumber)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }

    private fun stripSectionPrefix(seatNo: String): String {
        if (section.isEmpty() || !seatNo.startsWith(section)) return seatNo
        return seatNo.removePrefix(section).trimStart('-', ' ')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Seat) return false
        return eventId == other.eventId &&
            section == other.section &&
            rowNo == other.rowNo &&
            seatNo == other.seatNo
    }

    override fun hashCode(): Int {
        var result = eventId.hashCode()
        result = 31 * result + section.hashCode()
        result = 31 * result + rowNo.hashCode()
        result = 31 * result + seatNo.hashCode()
        return result
    }

    companion object {
        /**
         * `CreateMyEventRequest`가 열 정보를 수집하지 않고 고정 저장하는 값(미수집 센티널).
         * [displayLabel] 이 이 값일 때 "N열" 표기를 생략하는 기준으로 쓴다.
         */
        const val UNSPECIFIED_ROW_NO = "1"
    }
}
