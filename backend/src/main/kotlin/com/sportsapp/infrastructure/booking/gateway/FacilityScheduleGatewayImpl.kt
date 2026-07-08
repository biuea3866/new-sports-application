package com.sportsapp.infrastructure.booking.gateway

import com.sportsapp.domain.booking.dto.FacilitySchedule
import com.sportsapp.domain.booking.dto.WeeklyHours
import com.sportsapp.domain.booking.gateway.FacilityScheduleGateway
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.OperatingHours
import com.sportsapp.domain.facility.vo.TimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * FacilityScheduleGateway 구현체(BE-58).
 *
 * facility 도메인의 FacilityRepository(MongoDB)를 읽어 운영시간·휴무를 booking 소유 DTO로 변환한다.
 * FacilityRepository가 test-jpa 프로파일에서 비활성화되므로 동일 프로파일 조건을 따른다
 * (FacilityOwnershipGatewayImpl 패턴).
 */
@Component
@Profile("!test-jpa")
class FacilityScheduleGatewayImpl(
    private val facilityRepository: FacilityRepository,
) : FacilityScheduleGateway {

    override fun findSchedulableFacilities(): List<FacilitySchedule> =
        fetchAllFacilities()
            .filter { it.ownerUserId != null && it.operatingHours.isNotEmpty() }
            .map { it.toFacilitySchedule() }

    private fun fetchAllFacilities(): List<Facility> {
        val facilities = mutableListOf<Facility>()
        var pageable: Pageable = PageRequest.of(0, PAGE_SIZE)
        while (true) {
            val page = facilityRepository.findAllForBackfill(pageable)
            facilities += page.content
            if (!page.hasNext()) break
            pageable = pageable.next()
        }
        return facilities
    }

    private fun Facility.toFacilitySchedule(): FacilitySchedule = FacilitySchedule(
        facilityId = requireNotNull(id) { "Facility.id must not be null" },
        ownerId = requireNotNull(ownerUserId) { "Facility.ownerUserId must not be null" },
        weeklyHours = operatingHours.map { it.toWeeklyHours() },
        holidays = holidays.map { it.date }.toSet(),
    )

    private fun OperatingHours.toWeeklyHours(): WeeklyHours = WeeklyHours(
        dayOfWeek = dayOfWeek,
        timeRanges = slotRangesFor(referenceDateFor(dayOfWeek)).map { it.toRangeString() },
        capacity = capacity,
    )

    private fun TimeRange.toRangeString(): String =
        "${start.format(TIME_FORMATTER)}-${end.format(TIME_FORMATTER)}"

    // slotRangesFor는 요일 일치 여부만으로 판단하므로 실제 날짜 값과 무관하게 동일 요일이면 결과가 같다.
    private fun referenceDateFor(dayOfWeek: DayOfWeek): LocalDate =
        LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek))

    companion object {
        private const val PAGE_SIZE = 200
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
