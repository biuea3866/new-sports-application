package com.sportsapp.infrastructure.facility.region

import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.vo.FacilityRegion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * MySQL `regions` 마스터를 조회해 주소·시도 힌트를 행정표준코드로 해석한다.
 * 시도명은 약식·정식 표기를 모두 허용하도록 정규화하며, sidoHint가 주어지면 주소 파싱보다 우선한다.
 */
@Component
class RegionResolveGatewayImpl(
    private val regionJpaRepository: RegionJpaRepository,
) : RegionResolveGateway {

    private val logger = LoggerFactory.getLogger(RegionResolveGatewayImpl::class.java)

    override fun resolve(address: String, sidoHint: String?): FacilityRegion {
        val sidoName = resolveSidoName(address, sidoHint)
        val sigunguName = resolveSigunguName(address, sidoName)
        val region = findRegion(sidoName, sigunguName)

        if (region != null) return region.toFacilityRegion()

        logger.warn("region unresolved for address='{}', sidoHint='{}'", address, sidoHint)
        return FacilityRegion.UNSPECIFIED
    }

    private fun findRegion(sidoName: String?, sigunguName: String?): RegionJpaEntity? = when {
        sidoName != null && sigunguName != null ->
            regionJpaRepository.findBySidoNameAndSigunguName(sidoName, sigunguName)
        sigunguName != null -> regionJpaRepository.findBySigunguName(sigunguName).singleOrNull()
        else -> null
    }

    private fun resolveSidoName(address: String, sidoHint: String?): String? =
        normalizeSidoToken(sidoHint) ?: normalizeSidoToken(tokenize(address).firstOrNull())

    private fun resolveSigunguName(address: String, sidoName: String?): String? {
        if (sidoName == SEJONG_SIDO_NAME) return SEJONG_SIGUNGU_NAME
        return tokenize(address).firstOrNull { token -> isSigunguToken(token) }
    }

    private fun isSigunguToken(token: String): Boolean =
        SIGUNGU_SUFFIXES.any { suffix -> token.endsWith(suffix) } && normalizeSidoToken(token) == null

    private fun normalizeSidoToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return SIDO_ALIAS_TO_FULL_NAME[token] ?: token.takeIf { it in SIDO_FULL_NAMES }
    }

    private fun tokenize(address: String): List<String> =
        address.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val SIGUNGU_SUFFIXES = listOf("구", "군", "시")
        private const val SEJONG_SIDO_NAME = "세종특별자치시"
        private const val SEJONG_SIGUNGU_NAME = "세종특별자치시"

        private val SIDO_ALIAS_TO_FULL_NAME = mapOf(
            "부산" to "부산광역시",
            "서울" to "서울특별시",
            "대구" to "대구광역시",
            "인천" to "인천광역시",
            "광주" to "광주광역시",
            "대전" to "대전광역시",
            "울산" to "울산광역시",
            "세종" to "세종특별자치시",
            "경기" to "경기도",
            "강원" to "강원특별자치도",
            "충북" to "충청북도",
            "충남" to "충청남도",
            "전북" to "전북특별자치도",
            "전남" to "전라남도",
            "경북" to "경상북도",
            "경남" to "경상남도",
            "제주" to "제주특별자치도",
        )
        private val SIDO_FULL_NAMES: Set<String> = SIDO_ALIAS_TO_FULL_NAME.values.toSet()
    }
}
