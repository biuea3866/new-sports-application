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
}
