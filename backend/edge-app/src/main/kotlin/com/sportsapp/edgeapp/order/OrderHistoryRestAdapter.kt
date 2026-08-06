package com.sportsapp.edgeapp.order

import com.sportsapp.domain.order.dto.OrderHistoryItem
import com.sportsapp.domain.order.dto.OrderHistorySeat
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.order.gateway.OrderHistoryGateway
import com.sportsapp.edgeapp.upstream.InternalRestClientFactory
import com.sportsapp.infrastructure.security.InternalIdentityHeaders
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * `OrderHistoryGateway` 의 원격 구현 (S2-10).
 *
 * catalog(S2-09)와 결정적으로 다른 점은 **개인 데이터**라는 것이다. 사용자 식별이 필수인데
 * 그 신원은 [InternalIdentityHeaders.SUBJECT] **헤더 하나로만** 보낸다 — 쿼리·본문으로도 보내면
 * 공급자가 무엇을 신뢰해야 하는지가 갈리고, 둘이 어긋날 때 조용히 남의 데이터를 반환할 수 있다.
 * 공급자(S2-03·S2-04·S2-05)도 그 헤더만 읽고, 호출자 인증(S2-07)을 통과한 요청에서만 신뢰한다.
 *
 * **조회 창 계약을 보존한다.** 파사드는 `windowSize = (page + 1) * size` 로 넉넉히 가져와
 * 자기가 병합·필터·페이징한다. 어댑터는 받은 `page`·`size` 를 그대로 넘기고 **다시 자르지 않는다**
 * — 여기서 자르면 페이지 2 이상에서 항목이 누락된다. goods 만 페이징 계약이고 나머지 셋은 전량이다.
 *
 * 파생(`orderType`·`detailPath`)은 지금과 같은 자리(edge)에서 한다 — 공급자는 원자값만 준다.
 * 원격 실패는 예외로 전파해 파사드의 `failedDomains` 부분 저하가 흡수한다.
 */
@Component
class OrderHistoryRestAdapter(
    restClientFactory: InternalRestClientFactory,
) : OrderHistoryGateway {

    private val commerce: RestClient = restClientFactory.forCommerce()
    private val facilityBooking: RestClient = restClientFactory.forFacilityBooking()
    private val social: RestClient = restClientFactory.forSocial()

    override fun findGoodsOrders(userId: Long, pageable: Pageable): List<OrderHistoryItem> =
        commerce.fetch(GOODS_LIST, "/internal/order-history/goods", userId) { builder ->
            builder.queryParam("page", pageable.pageNumber)
            builder.queryParam("size", pageable.pageSize)
        }.map { it.toOrderHistoryItem() }

    override fun findTicketingOrders(userId: Long): List<OrderHistoryItem> =
        commerce.fetch(TICKETING_LIST, "/internal/order-history/ticketing", userId)
            .map { it.toOrderHistoryItem() }

    override fun findBookingOrders(userId: Long): List<OrderHistoryItem> =
        facilityBooking.fetch(BOOKING_LIST, "/internal/order-history/bookings", userId)
            .map { it.toOrderHistoryItem() }

    override fun findRecruitmentOrders(userId: Long): List<OrderHistoryItem> =
        social.fetch(RECRUITMENT_LIST, "/internal/order-history/recruitment-applications", userId)
            .map { it.toOrderHistoryItem() }

    private fun <T : Any> RestClient.fetch(
        responseType: ParameterizedTypeReference<List<T>>,
        path: String,
        userId: Long,
        query: (org.springframework.web.util.UriBuilder) -> Unit = {},
    ): List<T> = get()
        .uri { builder ->
            builder.path(path)
            query(builder)
            builder.build()
        }
        // 신원은 여기 한 곳에서만 붙인다 — 도메인별 메서드가 각자 붙이면 하나를 빠뜨려도
        // 컴파일이 통과하고, 그 도메인만 400 이 되어 부분 저하로 조용히 흡수된다.
        .header(InternalIdentityHeaders.SUBJECT, userId.toString())
        .retrieve()
        .body(responseType)
        ?: emptyList()

    /** 응답 타입은 구체 타입으로 고정한다 — reified 제네릭은 소거되어 컨버터가 대상을 못 찾는다(S2-09). */
    private companion object {
        val GOODS_LIST = object : ParameterizedTypeReference<List<GoodsOrderHistoryPayload>>() {}
        val TICKETING_LIST = object : ParameterizedTypeReference<List<TicketingOrderHistoryPayload>>() {}
        val BOOKING_LIST = object : ParameterizedTypeReference<List<BookingOrderHistoryPayload>>() {}
        val RECRUITMENT_LIST = object : ParameterizedTypeReference<List<RecruitmentOrderHistoryPayload>>() {}
    }
}

/**
 * 공급자 응답의 수신 형태 (S2-03·S2-04·S2-05 계약).
 *
 * 공급자 DTO 타입을 edge 로 끌어오지 않는다 — 끌어오면 그 타입이 곧 직렬화 계약이 되고 공급자
 * 모듈을 컴파일 의존해야 해서 S2-01 의 의존 역전이 무의미해진다. 필드 일치는 소비자 계약
 * 테스트(`OrderFanOutConsumerContractSpec`)가 고정한다.
 */
internal data class GoodsOrderHistoryPayload(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
) {
    fun toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
        orderType = OrderType.GOODS,
        sourceId = sourceId,
        title = title,
        status = status,
        paymentId = paymentId,
        detailPath = "/goods-orders/$sourceId",
        createdAt = createdAt,
        amount = amount,
    )
}

internal data class TicketingOrderHistoryPayload(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
    val seats: List<SeatPayload> = emptyList(),
) {
    internal data class SeatPayload(val section: String, val rowNo: String, val seatNo: String)

    fun toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
        orderType = OrderType.TICKETING,
        sourceId = sourceId,
        title = title,
        status = status,
        paymentId = paymentId,
        detailPath = "/ticket-orders/$sourceId",
        createdAt = createdAt,
        amount = amount,
        // 좌석 문자열을 미리 조합하지 않는다 — 표시 규칙은 모바일 formatSeatDescription 이 소유한다.
        // 빈 목록은 null 로 낮춘다(로컬 어댑터의 takeIf 의미 보존 — "좌석 정보 없음"과 "빈 배열"을 구분).
        seats = seats.map { OrderHistorySeat(it.section, it.rowNo, it.seatNo) }.takeIf { it.isNotEmpty() },
    )
}

internal data class BookingOrderHistoryPayload(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
) {
    fun toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
        orderType = OrderType.BOOKING,
        sourceId = sourceId,
        title = title,
        status = status,
        paymentId = paymentId,
        detailPath = "/bookings/$sourceId",
        createdAt = createdAt,
        amount = amount,
    )
}

internal data class RecruitmentOrderHistoryPayload(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
) {
    fun toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
        orderType = OrderType.RECRUITMENT,
        sourceId = sourceId,
        title = title,
        status = status,
        paymentId = paymentId,
        detailPath = "/applications/$sourceId",
        createdAt = createdAt,
        // 무료 모집은 금액이 없다 — 0 으로 위장하지 않는다(0원 오표시 사고 유형).
        amount = amount,
    )
}
