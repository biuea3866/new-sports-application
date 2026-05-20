package com.sportsapp.domain.booking

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "slots")
class Slot private constructor(
    @Column(nullable = false)
    val facilityId: String,

    @Column(nullable = false)
    val date: ZonedDateTime,

    @Column(nullable = false)
    val timeRange: String,

    @Column(nullable = false)
    val capacity: Int,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        private val TIME_RANGE_REGEX = Regex("""^\d{2}:\d{2}-\d{2}:\d{2}$""")

        fun create(
            facilityId: String,
            date: ZonedDateTime,
            timeRange: String,
            capacity: Int,
        ): Slot {
            if (!TIME_RANGE_REGEX.matches(timeRange)) {
                throw InvalidSlotException("timeRange must be HH:mm-HH:mm format, got: $timeRange")
            }
            return Slot(
                facilityId = facilityId,
                date = date,
                timeRange = timeRange,
                capacity = capacity,
            )
        }
    }
}
