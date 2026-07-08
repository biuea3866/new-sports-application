package com.sportsapp.application.facility.dto

import java.time.LocalDate

data class AddHolidayCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val date: LocalDate,
)
