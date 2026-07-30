package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogItem
import com.sportsapp.application.catalog.dto.CatalogItemType
import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.service.ProgramDomainService
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.service.TicketingDomainService
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
 * catalog 통합검색 조합 서비스 (BE-07). 4개 코어 DomainService(goods/facility/recruitment/
 * ticketing)의 catalog 읽기 메서드를 [catalogSearchExecutor]로 병렬 fan-out하고, 도메인당
 * [DOMAIN_TIMEOUT_MILLIS] 타임아웃을 적용한다. 실패·타임아웃 도메인은 결과에서 제외하고
 * [CatalogSearchResponse.failedDomains]에 기록한다(FR-11).
 *
 * catalog는 읽기 전용 조합(dashboard 패턴)이므로 domain 레이어를 신설하지 않는다.
 */
@Service
class CatalogCompositionService(
    private val goodsDomainService: GoodsDomainService,
    private val programDomainService: ProgramDomainService,
    private val recruitmentDomainService: RecruitmentDomainService,
    private val ticketingDomainService: TicketingDomainService,
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
            DOMAIN_FACILITY -> programDomainService.searchForCatalog(criteria.keyword, pageable).content.map { it.toCatalogItem() }
            DOMAIN_RECRUITMENT -> recruitmentDomainService.searchOpenRecruitments(criteria.keyword, pageable).content.map { it.toCatalogItem() }
            DOMAIN_TICKETING -> ticketingDomainService.searchOpenEvents(criteria.keyword, pageable).content.map { it.toCatalogItem() }
            else -> emptyList()
        }

    private fun fetchGoodsItems(criteria: CatalogSearchCriteria, pageable: Pageable): List<CatalogItem> {
        val page = goodsDomainService.search(
            category = null,
            keyword = criteria.keyword,
            priceMin = null,
            priceMax = null,
            sellerType = criteria.sellerType,
            pageable = pageable,
        )
        val items = page.content.map { it.toCatalogItem() }
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

/**
 * LIMITED_DROP 분기의 status는 Product.status(ACTIVE/INACTIVE)가 아니라
 * [com.sportsapp.domain.goods.entity.LimitedDrop.effectiveStatus]가 파생한
 * SCHEDULED/OPEN/SOLD_OUT/CLOSED를 노출한다 — 품절 한정판을 ACTIVE로 오노출하지 않기 위함이다.
 * `limitedDropId`가 채워지는 시점([GoodsDomainService.enrichWithLimitedDropId])에 항상
 * `limitedDropStatus`도 함께 채워지므로 `requireNotNull`로 그 불변식을 강제한다.
 */
private fun ProductWithStock.toCatalogItem(): CatalogItem {
    val isLimitedDrop = limitedDropId != null
    val itemType = if (isLimitedDrop) CatalogItemType.LIMITED_DROP else CatalogItemType.PRODUCT
    val sourceId = if (isLimitedDrop) requireNotNull(limitedDropId) else product.id
    val detailPath = if (isLimitedDrop) "/limited-drops/$limitedDropId" else "/products/${product.id}"
    val status = if (isLimitedDrop) requireNotNull(limitedDropStatus).name else product.status.name
    return CatalogItem(
        itemType = itemType,
        sourceId = sourceId,
        title = product.name,
        price = product.price,
        sellerType = product.sellerType,
        status = status,
        detailPath = detailPath,
        createdAt = product.createdAt,
    )
}

private fun Program.toCatalogItem(): CatalogItem = CatalogItem(
    itemType = CatalogItemType.PROGRAM,
    sourceId = id,
    title = name,
    price = price,
    sellerType = null,
    status = "ACTIVE",
    detailPath = "/programs/$id",
    createdAt = createdAt,
)

private fun Recruitment.toCatalogItem(): CatalogItem = CatalogItem(
    itemType = CatalogItemType.RECRUITMENT,
    sourceId = id,
    title = title,
    price = feeAmount,
    sellerType = null,
    status = status.name,
    detailPath = "/recruitments/$id",
    createdAt = createdAt,
)

private fun Event.toCatalogItem(): CatalogItem = CatalogItem(
    itemType = CatalogItemType.TICKET,
    sourceId = id,
    title = title,
    price = null,
    sellerType = null,
    status = status.name,
    detailPath = "/events/$id",
    createdAt = createdAt,
)
