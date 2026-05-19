package com.sportsapp.domain.booking

import java.time.ZonedDateTime

class Slot private constructor(
    val id: Long,
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
) {
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
                id = 0L,
                facilityId = facilityId,
                date = date,
                timeRange = timeRange,
                capacity = capacity,
            )
        }

        fun reconstruct(
            id: Long,
            facilityId: String,
            date: ZonedDateTime,
            timeRange: String,
            capacity: Int,
        ): Slot = Slot(
            id = id,
            facilityId = facilityId,
            date = date,
            timeRange = timeRange,
            capacity = capacity,
        )
    }
}
