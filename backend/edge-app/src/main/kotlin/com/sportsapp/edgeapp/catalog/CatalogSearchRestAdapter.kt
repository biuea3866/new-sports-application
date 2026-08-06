package com.sportsapp.edgeapp.catalog

import com.sportsapp.domain.catalog.dto.CatalogItem
import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.gateway.CatalogSearchGateway
import com.sportsapp.domain.catalog.vo.SellerType
import com.sportsapp.edgeapp.upstream.InternalRestClientFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriBuilder

/**
 * `CatalogSearchGateway` 의 원격 구현 (S2-09).
 *
 * **위치가 `edge-app` 인 것이 중요하다.** 로컬 어댑터(bootstrap)와 이 어댑터가 같은 인터페이스를
 * 구현하므로, 이 클래스를 `edge` 모듈에 `@Component` 로 두면 모놀리스 컨텍스트가 그것까지 스캔해
 * **동일 타입 빈 2개**로 기동이 깨진다. 컴포지션 루트가 자기 구현을 소유한다.
 *
 * **파생 위치를 옮기지 않는다.** `itemType`·`sourceId`·`detailPath`·한정판 `status` 판정은 지금과
 * 같은 자리(edge)에서 한다 — 공급자는 원자값(`limitedDropId`·`limitedDropStatus`)만 주고, 판정을
 * 공급자로 옮기면 섀도 응답 동일성 비교(S2-06·S2-15)의 전제가 무너진다.
 *
 * 원격 실패는 **예외로 전파한다** — 파사드의 `catch` 가 `failedDomains` 로 기록해 부분 저하로
 * 흡수한다. 빈 목록으로 위장하면 실패가 관측되지 않은 채 결과가 조용히 빈다.
 */
@Component
class CatalogSearchRestAdapter(
    restClientFactory: InternalRestClientFactory,
) : CatalogSearchGateway {

    private val commerce: RestClient = restClientFactory.forCommerce()
    private val facilityBooking: RestClient = restClientFactory.forFacilityBooking()
    private val social: RestClient = restClientFactory.forSocial()

    override fun searchGoods(keyword: String?, sellerType: SellerType?, pageable: Pageable): List<CatalogItem> =
        commerce.fetch(GOODS_PAYLOAD_LIST, "/internal/catalog/goods", pageable) { builder ->
            keyword?.let { builder.queryParam("keyword", it) }
            sellerType?.let { builder.queryParam("sellerType", it.name) }
        }.map { it.toCatalogItem() }

    override fun searchTicketingEvents(keyword: String?, pageable: Pageable): List<CatalogItem> =
        commerce.fetch(TICKETING_PAYLOAD_LIST, "/internal/catalog/ticketing", pageable) { builder ->
            keyword?.let { builder.queryParam("keyword", it) }
        }.map { it.toCatalogItem() }

    override fun searchPrograms(keyword: String?, pageable: Pageable): List<CatalogItem> =
        facilityBooking.fetch(PROGRAM_PAYLOAD_LIST, "/internal/catalog/programs", pageable) { builder ->
            keyword?.let { builder.queryParam("keyword", it) }
        }.map { it.toCatalogItem() }

    override fun searchRecruitments(keyword: String?, pageable: Pageable): List<CatalogItem> =
        social.fetch(RECRUITMENT_PAYLOAD_LIST, "/internal/catalog/recruitments", pageable) { builder ->
            keyword?.let { builder.queryParam("keyword", it) }
        }.map { it.toCatalogItem() }

    /**
     * `page`·`size` 를 그대로 상류에 전달한다 — 병합·정렬·페이징은 파사드의 책임이고 어댑터가
     * 페이징 의미를 바꾸지 않는다. 상한 절삭도 하지 않는다(공급자·소비자 분담, S2-03 계약).
     */
    private fun <T : Any> RestClient.fetch(
        responseType: ParameterizedTypeReference<List<T>>,
        path: String,
        pageable: Pageable,
        query: (UriBuilder) -> Unit,
    ): List<T> = get()
        .uri { builder ->
            builder.path(path)
                .queryParam("page", pageable.pageNumber)
                .queryParam("size", pageable.pageSize)
            query(builder)
            builder.build()
        }
        .retrieve()
        .body(responseType)
        ?: emptyList()

    /**
     * 응답 타입은 **호출부에서 구체 타입으로** 넘긴다.
     *
     * `reified T` + `Array<T>::class.java` 는 소거되어 `Object[]` 로 요청되고, Jackson 이
     * `LinkedHashMap` 을 만들어 `ClassCastException` 이 런타임에 터진다(테스트가 이걸 잡았다).
     * 구체 타입으로 고정한 `ParameterizedTypeReference` 는 그 소거를 겪지 않는다.
     */
    private companion object {
        val GOODS_PAYLOAD_LIST = object : ParameterizedTypeReference<List<GoodsCatalogItemPayload>>() {}
        val TICKETING_PAYLOAD_LIST = object : ParameterizedTypeReference<List<TicketingCatalogItemPayload>>() {}
        val PROGRAM_PAYLOAD_LIST = object : ParameterizedTypeReference<List<ProgramCatalogItemPayload>>() {}
        val RECRUITMENT_PAYLOAD_LIST = object : ParameterizedTypeReference<List<RecruitmentCatalogItemPayload>>() {}
    }
}

/**
 * 공급자 응답의 **수신 형태**다 (S2-03·S2-04·S2-05 계약).
 *
 * 공급자 DTO 타입을 edge 로 끌어오지 않는다 — 끌어오면 그 타입이 곧 서비스 간 직렬화 계약이 되고,
 * 공급자 모듈을 컴파일 의존해야 해서 S2-01 의 의존 역전이 무의미해진다. 이름이 같은 별개 타입이며
 * 필드 일치는 소비자 계약 테스트(`CatalogConsumerContractSpec`)가 고정한다.
 */
internal data class GoodsCatalogItemPayload(
    val productId: Long,
    val limitedDropId: Long?,
    val limitedDropStatus: String?,
    val title: String,
    val price: java.math.BigDecimal,
    val sellerType: SellerType?,
    val productStatus: String,
    val createdAt: java.time.ZonedDateTime,
) {
    /**
     * 한정판이면 식별자·경로·상태가 모두 한정판 기준이다.
     *
     * `limitedDropId` 가 채워지는 시점에 `limitedDropStatus` 도 항상 함께 채워지는 것이 공급자
     * 불변식이라 `requireNotNull` 로 강제한다 — 깨지면 조용히 상품 상태를 노출하는 것보다
     * 즉시 실패해 부분 저하로 드러나는 편이 낫다(품절 한정판을 ACTIVE 로 오노출하지 않는다).
     */
    fun toCatalogItem(): CatalogItem {
        val isLimitedDrop = limitedDropId != null
        return CatalogItem(
            itemType = if (isLimitedDrop) CatalogItemType.LIMITED_DROP else CatalogItemType.PRODUCT,
            sourceId = if (isLimitedDrop) requireNotNull(limitedDropId) else productId,
            title = title,
            price = price,
            sellerType = sellerType,
            status = if (isLimitedDrop) requireNotNull(limitedDropStatus) else productStatus,
            detailPath = if (isLimitedDrop) "/limited-drops/$limitedDropId" else "/products/$productId",
            createdAt = createdAt,
            // goods 는 구분할 장소·일정 개념이 없다 — 공급자가 보내지 않고 여기서 null 로 채운다.
            locationName = null,
            scheduledAt = null,
        )
    }
}

internal data class TicketingCatalogItemPayload(
    val sourceId: Long,
    val title: String,
    val price: java.math.BigDecimal?,
    val status: String,
    val createdAt: java.time.ZonedDateTime,
    val locationName: String,
    val scheduledAt: java.time.ZonedDateTime,
) {
    fun toCatalogItem(): CatalogItem = CatalogItem(
        itemType = CatalogItemType.TICKET,
        sourceId = sourceId,
        title = title,
        // 경기는 좌석마다 가격이 달라 최저 좌석가를 대표가로 노출한다(좌석 미등록 경기는 null).
        price = price,
        sellerType = null,
        status = status,
        detailPath = "/events/$sourceId",
        createdAt = createdAt,
        // 경기장명·시작 일시로 같은 제목의 경기를 구분한다.
        locationName = locationName,
        scheduledAt = scheduledAt,
    )
}

internal data class ProgramCatalogItemPayload(
    val sourceId: Long,
    val title: String,
    val price: java.math.BigDecimal,
    val createdAt: java.time.ZonedDateTime,
    val locationName: String?,
) {
    fun toCatalogItem(): CatalogItem = CatalogItem(
        itemType = CatalogItemType.PROGRAM,
        sourceId = sourceId,
        title = title,
        price = price,
        sellerType = null,
        // 프로그램은 상태 개념이 없어 로컬 어댑터가 고정값을 노출했다 — 같은 값을 유지한다.
        status = PROGRAM_STATUS,
        detailPath = "/programs/$sourceId",
        createdAt = createdAt,
        // 시설별로 같은 이름의 프로그램이 등록될 수 있어 시설명으로 구분한다.
        // 시설이 삭제돼 이름을 찾지 못하면 null 그대로 노출한다(빈 문자열·유형명 반복 금지).
        locationName = locationName,
        scheduledAt = null,
    )

    private companion object {
        const val PROGRAM_STATUS = "ACTIVE"
    }
}

internal data class RecruitmentCatalogItemPayload(
    val sourceId: Long,
    val title: String,
    val price: java.math.BigDecimal,
    val status: String,
    val createdAt: java.time.ZonedDateTime,
    val scheduledAt: java.time.ZonedDateTime,
) {
    fun toCatalogItem(): CatalogItem = CatalogItem(
        itemType = CatalogItemType.RECRUITMENT,
        sourceId = sourceId,
        title = title,
        price = price,
        sellerType = null,
        status = status,
        detailPath = "/recruitments/$sourceId",
        createdAt = createdAt,
        locationName = null,
        // 모임 활동 일시로 같은 제목의 모집글을 구분한다.
        scheduledAt = scheduledAt,
    )
}
