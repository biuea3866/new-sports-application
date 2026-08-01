package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import com.sportsapp.domain.catalog.dto.CatalogItem
import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.gateway.CatalogSearchGateway
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(CatalogCompositionService::class.java)
private const val DOMAIN_TIMEOUT_MILLIS = 300L

private const val DOMAIN_GOODS = "goods"
private const val DOMAIN_FACILITY = "facility"
private const val DOMAIN_RECRUITMENT = "recruitment"
private const val DOMAIN_TICKETING = "ticketing"

/**
 * catalog 통합검색 조합 서비스 (BE-07). 4개 코어 도메인(goods/facility/recruitment/ticketing)의
 * catalog 읽기를 [CatalogSearchGateway]로 위임하고 [catalogSearchExecutor]로 병렬 fan-out한다.
 * 도메인당 [DOMAIN_TIMEOUT_MILLIS] 타임아웃을 적용하며, 실패·타임아웃 도메인은 결과에서 제외하고
 * [CatalogSearchResponse.failedDomains]에 기록한다(FR-11).
 *
 * catalog는 읽기 전용 조합(dashboard 패턴)이므로 DomainService를 신설하지 않는다.
 *
 * [S2-01] 이전에는 4개 코어 DomainService(GoodsDomainService 등)를 직접 주입했다 — edge가 이
 * 서비스들의 소유 모듈(commerce·facility-booking·social)을 컴파일 의존해야 했다. 지금은 edge
 * 소유 [CatalogSearchGateway] 하나만 주입한다 — fan-out·타임아웃·부분 저하 로직은 이동 전과
 * 완전히 동일하고, "무엇을 조회하는가"만 Gateway 뒤로 숨었다.
 */
@Service
class CatalogCompositionService(
    private val catalogSearchGateway: CatalogSearchGateway,
    @Qualifier("catalogSearchExecutor") private val catalogSearchExecutor: AsyncTaskExecutor,
) {
    fun search(criteria: CatalogSearchCriteria): CatalogSearchResponse {
        val pageable = criteria.toDomainPageable()
        val outcomes = fetchDomains(criteria, pageable)
        val items = outcomes.flatMap { it.items }
        val failedDomains = outcomes.filter { it.failed }.flatMap { coveredItemTypesFor(it.domainName, criteria.itemType) }
        return CatalogSearchResponse(
            items = mergeAndPaginate(items, criteria),
            page = criteria.page,
            size = criteria.cappedSize,
            failedDomains = failedDomains,
        )
    }

    private fun fetchDomains(criteria: CatalogSearchCriteria, pageable: Pageable): List<DomainOutcome> {
        val submissions = resolveDomains(criteria.itemType).map { domainName ->
            domainName to submitTask(domainName, criteria, pageable)
        }
        return submissions.map { (domainName, future) ->
            if (future == null) DomainOutcome(domainName, emptyList(), failed = true) else awaitOutcome(domainName, future)
        }
    }

    /**
     * bounded executor 포화 시 `submit()` 자체가 [RejectedExecutionException]을 동기적으로 던질 수
     * 있다(FR-11) — 여기서 흡수하지 않으면 그 도메인뿐 아니라 요청 전체가 500이 된다.
     */
    private fun submitTask(domainName: String, criteria: CatalogSearchCriteria, pageable: Pageable): Future<List<CatalogItem>>? =
        try {
            catalogSearchExecutor.submit(Callable { fetchItems(domainName, criteria, pageable) })
        } catch (exception: RejectedExecutionException) {
            logger.warn("catalog domain fetch rejected: domain={}", domainName, exception)
            null
        }

    private fun resolveDomains(itemType: CatalogItemType?): List<String> = when (itemType) {
        null -> listOf(DOMAIN_GOODS, DOMAIN_FACILITY, DOMAIN_RECRUITMENT, DOMAIN_TICKETING)
        CatalogItemType.PRODUCT, CatalogItemType.LIMITED_DROP -> listOf(DOMAIN_GOODS)
        CatalogItemType.TICKET -> listOf(DOMAIN_TICKETING)
        CatalogItemType.PROGRAM -> listOf(DOMAIN_FACILITY)
        CatalogItemType.RECRUITMENT -> listOf(DOMAIN_RECRUITMENT)
    }

    /**
     * 실패한 도메인이 담당하던 [CatalogItemType]들을 [CatalogSearchResponse.failedDomains]에 얹기
     * 위한 역매핑. goods는 PRODUCT/LIMITED_DROP 둘을 겸하므로(FR-4) itemType 필터가 없으면 둘 다,
     * 필터가 있으면 그 필터 하나만 담아 FE 배너가 요청하지 않은 유형까지 실패로 표시하지 않게 한다.
     */
    private fun coveredItemTypesFor(domainName: String, itemType: CatalogItemType?): List<CatalogItemType> = when (domainName) {
        DOMAIN_GOODS -> when (itemType) {
            null -> listOf(CatalogItemType.PRODUCT, CatalogItemType.LIMITED_DROP)
            CatalogItemType.PRODUCT, CatalogItemType.LIMITED_DROP -> listOf(itemType)
            else -> emptyList()
        }
        DOMAIN_FACILITY -> listOf(CatalogItemType.PROGRAM)
        DOMAIN_RECRUITMENT -> listOf(CatalogItemType.RECRUITMENT)
        DOMAIN_TICKETING -> listOf(CatalogItemType.TICKET)
        else -> emptyList()
    }

    private fun fetchItems(domainName: String, criteria: CatalogSearchCriteria, pageable: Pageable): List<CatalogItem> =
        when (domainName) {
            DOMAIN_GOODS -> fetchGoodsItems(criteria, pageable)
            DOMAIN_FACILITY -> catalogSearchGateway.searchPrograms(criteria.keyword, pageable)
            DOMAIN_RECRUITMENT -> catalogSearchGateway.searchRecruitments(criteria.keyword, pageable)
            DOMAIN_TICKETING -> catalogSearchGateway.searchTicketingEvents(criteria.keyword, pageable)
            else -> emptyList()
        }

    private fun fetchGoodsItems(criteria: CatalogSearchCriteria, pageable: Pageable): List<CatalogItem> {
        val items = catalogSearchGateway.searchGoods(criteria.keyword, criteria.sellerType, pageable)
        return if (criteria.itemType == null) items else items.filter { it.itemType == criteria.itemType }
    }

    private fun awaitOutcome(domainName: String, future: Future<List<CatalogItem>>): DomainOutcome =
        try {
            DomainOutcome(domainName, future.get(DOMAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), failed = false)
        } catch (exception: TimeoutException) {
            future.cancel(true)
            logger.warn("catalog domain fetch timed out: domain={}", domainName, exception)
            DomainOutcome(domainName, emptyList(), failed = true)
        } catch (exception: Exception) {
            logger.warn("catalog domain fetch failed: domain={}", domainName, exception)
            DomainOutcome(domainName, emptyList(), failed = true)
        }

    private fun mergeAndPaginate(items: List<CatalogItem>, criteria: CatalogSearchCriteria): List<CatalogItem> {
        val cappedSize = criteria.cappedSize
        return items.sortedByDescending { it.createdAt }
            .drop(criteria.page * cappedSize)
            .take(cappedSize)
    }

    private data class DomainOutcome(val domainName: String, val items: List<CatalogItem>, val failed: Boolean)
}
