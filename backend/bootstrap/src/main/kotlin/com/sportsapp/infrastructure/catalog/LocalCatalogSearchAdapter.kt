package com.sportsapp.infrastructure.catalog

import com.sportsapp.domain.catalog.dto.CatalogItem
import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.gateway.CatalogSearchGateway
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.service.ProgramDomainService
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.EventWithMinSeatPrice
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import com.sportsapp.domain.catalog.vo.SellerType as EdgeSellerType
import com.sportsapp.domain.goods.vo.SellerType as GoodsSellerType

/**
 * 1단계 로컬 어댑터 — edge의 catalog 검색 계약을 같은 프로세스의 4개 코어 DomainService 호출로
 * 만족시킨다 (S2-01, Branch By Abstraction).
 *
 * **조립자(`bootstrap`)에 두는 이유**: edge는 commerce·facility-booking·social을 의존하지 않아야
 * 하고, 그 모듈들도 edge를 의존하지 않는다. 두 쪽을 모두 아는 유일한 지점이 컴포지션 루트다.
 * 2단계에는 이 어댑터를 버리고 edge 안의 RestClient 구현이 각 서비스의 원격 catalog 조회
 * 엔드포인트를 호출한다(§9 Branch By Abstraction — 계약은 그대로, 구현만 교체).
 *
 * 타 모듈 Entity(`ProductWithStock`·`Program`·`Recruitment`·`EventWithMinSeatPrice`) →
 * [CatalogItem] 매핑은 이동 전 `CatalogCompositionService`가 갖던 것을 그대로 옮겼다 — 매핑
 * 규칙(LIMITED_DROP 분기, 최저 좌석가 등)에 동작 변화가 없다.
 *
 * `@Profile` 미부착 — 이동 전 `CatalogCompositionService`가 프로파일 무관 빈이었고, 여기서
 * 주입받는 `ProgramDomainService`(Mongo 기반, `@Profile("!test-jpa")`)는 test-jpa 프로파일에서
 * `TestJpaGatewayStubConfig`가 이미 스텁을 공급한다 — 그 경계를 그대로 보존한다.
 */
@Component
class LocalCatalogSearchAdapter(
    private val goodsDomainService: GoodsDomainService,
    private val programDomainService: ProgramDomainService,
    private val recruitmentDomainService: RecruitmentDomainService,
    private val ticketingDomainService: TicketingDomainService,
) : CatalogSearchGateway {

    override fun searchGoods(keyword: String?, sellerType: EdgeSellerType?, pageable: Pageable): List<CatalogItem> {
        val page = goodsDomainService.search(
            category = null,
            keyword = keyword,
            priceMin = null,
            priceMax = null,
            sellerType = sellerType?.toGoodsSellerType(),
            pageable = pageable,
        )
        return page.content.map { it.toCatalogItem() }
    }

    override fun searchPrograms(keyword: String?, pageable: Pageable): List<CatalogItem> =
        programDomainService.searchForCatalog(keyword, pageable).content.map { it.toCatalogItem() }

    override fun searchRecruitments(keyword: String?, pageable: Pageable): List<CatalogItem> =
        recruitmentDomainService.searchOpenRecruitments(keyword, pageable).content.map { it.toCatalogItem() }

    override fun searchTicketingEvents(keyword: String?, pageable: Pageable): List<CatalogItem> =
        ticketingDomainService.searchOpenEventsForCatalog(keyword, pageable).content.map { it.toCatalogItem() }
}

private fun EdgeSellerType.toGoodsSellerType(): GoodsSellerType = when (this) {
    EdgeSellerType.B2C -> GoodsSellerType.B2C
    EdgeSellerType.B2B -> GoodsSellerType.B2B
}

private fun GoodsSellerType.toEdgeSellerType(): EdgeSellerType = when (this) {
    GoodsSellerType.B2C -> EdgeSellerType.B2C
    GoodsSellerType.B2B -> EdgeSellerType.B2B
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
        sellerType = product.sellerType?.toEdgeSellerType(),
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

private fun EventWithMinSeatPrice.toCatalogItem(): CatalogItem = CatalogItem(
    itemType = CatalogItemType.TICKET,
    sourceId = event.id,
    title = event.title,
    // 경기는 좌석마다 가격이 달라 최저 좌석가를 대표가로 노출한다(좌석 미등록 경기는 null).
    price = minSeatPrice,
    sellerType = null,
    status = event.status.name,
    detailPath = "/events/${event.id}",
    createdAt = event.createdAt,
)
