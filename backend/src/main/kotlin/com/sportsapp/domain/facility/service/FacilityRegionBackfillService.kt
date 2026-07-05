package com.sportsapp.domain.facility.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.facility.dto.BackfillResult
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityRegionBackfillInProgressException
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityRegion
import java.time.Duration
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 기존 시설 문서의 지역(시도·시군구) 필드를 주소 재해석으로 백필한다.
 *
 * 동일 주소는 항상 동일 코드로 결정적 재해석되므로 재실행해도 결과가 수렴한다(멱등).
 * 관리자 중복 트리거는 분산 락(`lock:facility:region-backfill`)으로 방지하고,
 * 페이지 단위로 문서별 save 하여 장시간 트랜잭션을 만들지 않는다.
 */
@Service
@Profile("!test-jpa")
class FacilityRegionBackfillService(
    private val facilityRepository: FacilityRepository,
    private val regionResolveGateway: RegionResolveGateway,
    private val distributedLock: DistributedLock,
) {
    fun backfill(pageSize: Int): BackfillResult {
        val lockValue = UUID.randomUUID().toString()
        if (!distributedLock.tryLock(LOCK_KEY, lockValue, LOCK_TTL)) {
            throw FacilityRegionBackfillInProgressException()
        }
        return try {
            backfillAllPages(pageSize)
        } finally {
            distributedLock.unlock(LOCK_KEY, lockValue)
        }
    }

    private fun backfillAllPages(pageSize: Int): BackfillResult {
        var updated = 0
        var unspecified = 0
        var pageable: Pageable = PageRequest.of(0, pageSize)
        while (true) {
            val page = facilityRepository.findAllForBackfill(pageable)
            page.content.forEach { facility ->
                updated++
                if (backfillOne(facility).isUnspecified()) unspecified++
            }
            if (!page.hasNext()) break
            pageable = pageable.next()
        }
        return BackfillResult(updated = updated, unspecified = unspecified)
    }

    private fun backfillOne(facility: Facility): FacilityRegion {
        val region = regionResolveGateway.resolve(facility.address, null)
        facilityRepository.save(facility.assignRegion(region))
        return region
    }

    companion object {
        private const val LOCK_KEY = "lock:facility:region-backfill"
        private val LOCK_TTL: Duration = Duration.ofMinutes(30)
    }
}
