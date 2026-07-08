package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.LegacyRow
import com.sportsapp.domain.facility.vo.FacilityAttributes
import org.slf4j.LoggerFactory

/**
 * 레거시 행을 FacilityAttributes 로 변환한다.
 *
 * 변환 규칙:
 * - legacyId  → code
 * - ycode     → lat (Double 변환 실패 시 null 반환 + 경고 로그)
 * - xcode     → lng (Double 변환 실패 시 null 반환 + 경고 로그)
 * - extraFields → meta 로 fallback
 */
object LegacyToFacilityMapper {

    private val logger = LoggerFactory.getLogger(LegacyToFacilityMapper::class.java)

    /**
     * 변환 성공 시 FacilityAttributes 반환, 좌표 파싱 실패 시 null 반환 (경고 로그 포함).
     */
    fun map(row: LegacyRow): FacilityAttributes? {
        val lat = row.ycode.toDoubleOrNull()
        val lng = row.xcode.toDoubleOrNull()

        if (lat == null || lng == null) {
            logger.warn(
                "[U-02] 좌표 변환 실패 — legacyId={} ycode={} xcode={} : 건너뜁니다",
                row.legacyId, row.ycode, row.xcode,
            )
            return null
        }

        return FacilityAttributes(
            code = row.legacyId,
            name = row.name,
            gu = row.gu,
            type = row.type,
            address = row.address,
            lat = lat,
            lng = lng,
            parking = row.parking,
            tel = row.tel,
            homePage = row.homePage,
            eduYn = row.eduYn,
            meta = row.extraFields,
            // region 해석은 매퍼가 아니라 FacilityDomainService.bulkImport 가 수행한다 — 힌트만 전달한다.
            sidoHint = row.sido,
        )
    }
}
