package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.OperatingHours
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 소유자 범위의 시설 운영 일정(운영시간·휴무일) 관리.
 * [FacilityOwnerDomainService]에서 분리했다 — 등록/조회/삭제와 달리 "소유권 검증 + 일정
 * 캡슐화"만 다루는 응집된 책임이라 별도 클래스로 뺐다(TooManyFunctions 12/11 해소).
 */
@Service
@Profile("!test-jpa")
class FacilityScheduleOwnerDomainService(
    private val facilityRepository: FacilityRepository,
) {
    fun registerOperatingHours(facilityId: String, ownerUserId: Long, operatingHours: List<OperatingHours>): Facility {
        val facility = getOwnedFacility(facilityId, ownerUserId)
        facility.registerOperatingHours(operatingHours)
        return facilityRepository.save(facility)
    }

    fun addHoliday(facilityId: String, ownerUserId: Long, date: LocalDate): Facility {
        val facility = getOwnedFacility(facilityId, ownerUserId)
        facility.addHoliday(date)
        return facilityRepository.save(facility)
    }

    fun removeHoliday(facilityId: String, ownerUserId: Long, date: LocalDate): Facility {
        val facility = getOwnedFacility(facilityId, ownerUserId)
        facility.removeHoliday(date)
        return facilityRepository.save(facility)
    }

    // 존재 여부와 소유 여부를 분리해 소유권 위반을 명시적 예외로 구분한다 (getByIdAndOwner의 not-found 은닉과 다른 용도).
    private fun getOwnedFacility(facilityId: String, ownerUserId: Long): Facility {
        val facility = facilityRepository.findById(facilityId) ?: throw FacilityNotFoundException(facilityId)
        facility.requireOwnedBy(ownerUserId)
        return facility
    }
}
