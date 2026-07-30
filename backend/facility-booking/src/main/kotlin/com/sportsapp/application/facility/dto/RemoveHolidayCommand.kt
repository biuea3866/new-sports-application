package com.sportsapp.application.facility.dto

import java.time.LocalDate

data class RemoveHolidayCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val date: LocalDate,
)
