package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.gateway.GeocodingGateway
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.OperatingHours
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Profile("!test-jpa")
class FacilityOwnerDomainService(
    private val facilityRepository: FacilityRepository,
    private val geocodingGateway: GeocodingGateway,
    private val slotQueryGateway: SlotQueryGateway,
    private val regionResolveGateway: RegionResolveGateway,
) {
    fun registerForOwner(attributes: FacilityAttributes, ownerUserId: Long): Facility {
        val resolved = resolveCoordinates(attributes).let(::resolveRegion)
        val facility = Facility.create(resolved)
        facility.assignOwner(ownerUserId)
        return facilityRepository.save(facility)
    }

    // 좌표가 비어 있고(0,0) 주소가 있으면 geocoding 으로 보강합니다. 실패 시 원본을 유지합니다.
    private fun resolveCoordinates(attributes: FacilityAttributes): FacilityAttributes {
        if (attributes.lat != 0.0 || attributes.lng != 0.0) return attributes
        if (attributes.address.isBlank()) return attributes
        val coordinate = geocodingGateway.geocode(attributes.address) ?: return attributes
        return attributes.copy(lat = coordinate.lat, lng = coordinate.lng)
    }

    // 주소·sidoHint로 행정표준코드를 해석합니다. 실패 시 UNSPECIFIED가 그대로 보존됩니다.
    private fun resolveRegion(attributes: FacilityAttributes): FacilityAttributes {
        val region = regionResolveGateway.resolve(attributes.address, attributes.sidoHint)
        return attributes.copy(region = region)
    }

    fun listByOwner(ownerUserId: Long, pageable: Pageable): Page<Facility> =
        facilityRepository.findByOwnerUserId(ownerUserId, pageable)

    fun getByIdAndOwner(id: String, ownerUserId: Long): Facility =
        facilityRepository.findByIdAndOwnerUserId(id, ownerUserId)
            ?: throw FacilityNotFoundException(id)

    fun updateMetaForOwner(id: String, ownerUserId: Long, patch: Map<String, String>, sido: String? = null): Facility {
        val facility = getByIdAndOwner(id, ownerUserId)
        val withMeta = facility.updateMeta(patch)
        val updated = if (sido == null) withMeta else reresolveRegion(withMeta, sido)
        return facilityRepository.save(updated)
    }

    // sido가 함께 전달된 수정 요청은 현재 주소를 기준으로 지역을 재해석해 반영합니다.
    private fun reresolveRegion(facility: Facility, sido: String): Facility {
        val region = regionResolveGateway.resolve(facility.address, sido)
        return facility.assignRegion(region)
    }

    fun deleteForOwner(id: String, ownerUserId: Long) {
        val facility = getByIdAndOwner(id, ownerUserId)
        if (slotQueryGateway.hasActiveSlots(id)) {
            throw FacilityHasActiveSlotException(id)
        }
        facility.softDelete(ownerUserId)
        facilityRepository.save(facility)
    }

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
