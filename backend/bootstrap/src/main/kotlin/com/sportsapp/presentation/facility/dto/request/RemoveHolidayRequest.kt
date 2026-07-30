package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.RemoveHolidayCommand
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class RemoveHolidayRequest(
    val date: String,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = RemoveHolidayCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        date = parseDate(),
    )

    private fun parseDate(): LocalDate = try {
        LocalDate.parse(date)
    } catch (exception: DateTimeParseException) {
        throw IllegalArgumentException("date must be in yyyy-MM-dd format, got: $date")
    }
}
