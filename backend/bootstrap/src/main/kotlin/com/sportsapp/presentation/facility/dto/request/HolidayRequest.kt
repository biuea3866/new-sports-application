package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.AddHolidayCommand
import java.time.LocalDate

data class HolidayRequest(
    val date: String,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = AddHolidayCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        date = LocalDate.parse(date),
    )
}
