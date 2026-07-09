package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryItem
import com.sportsapp.application.order.dto.OrderHistoryResponse
import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(OrderCompositionService::class.java)
private const val DOMAIN_TIMEOUT_MILLIS = 300L

/**
 * order 통합조회(BE-08) 조합 로직 — 4개 코어 DomainService(booking/goods/ticketing/recruitment)의
 * 이름 포함 주문 읽기를 병렬 fan-out하고, `OrderHistoryItem`으로 매핑해 단일 응답으로 합친다.
 *
 * 파사드는 이름을 만들지 않는다 — 각 컨텍스트가 반환한 title을 그대로 매핑만 한다(TDD
 * "주문 표시명 확보 방식"). domain 레이어 없음(dashboard 패턴), read-only 조합 전용.
 */
@Service
class OrderCompositionService(
    private val bookingDomainService: BookingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val ticketingDomainService: TicketingDomainService,
    private val recruitmentDomainService: RecruitmentDomainService,
    private val orderHistoryExecutor: AsyncTaskExecutor,
) {
    fun history(userId: Long, criteria: OrderHistoryCriteria): OrderHistoryResponse {
        val outcomes = fanOutDomainQueries(userId, criteria)
        val failedDomains = outcomes.filterValues { it == null }.keys.toList()
        val items = outcomes.values.filterNotNull().flatten()
            .filter { criteria.orderType == null || it.orderType == criteria.orderType }
            .filter { criteria.status == null || it.status == criteria.status }
            .sortedByDescending { it.createdAt }

        return OrderHistoryResponse(
            items = paginate(items, criteria),
            page = criteria.page,
            size = criteria.size,
            failedDomains = failedDomains,
        )
    }

    private fun fanOutDomainQueries(userId: Long, criteria: OrderHistoryCriteria): Map<OrderType, List<OrderHistoryItem>?> {
        val windowSize = (criteria.page + 1) * criteria.size
        val futures = buildTasks(userId, windowSize).mapValues { (_, task) ->
            CompletableFuture.supplyAsync(task, orderHistoryExecutor)
        }
        return futures.mapValues { (orderType, future) -> awaitOrNull(orderType, future) }
    }

    private fun buildTasks(userId: Long, windowSize: Int): Map<OrderType, () -> List<OrderHistoryItem>> = mapOf(
        OrderType.BOOKING to { bookingDomainService.findOrderHistory(userId).map { it.toOrderHistoryItem() } },
        OrderType.GOODS to {
            goodsDomainService.listMyOrdersWithTitle(userId, PageRequest.of(0, windowSize))
                .content.map { it.toOrderHistoryItem() }
        },
        OrderType.TICKETING to { ticketingDomainService.listTicketOrdersBy(userId).map { it.toOrderHistoryItem() } },
        OrderType.RECRUITMENT to { recruitmentDomainService.listApplicationsWithTitleBy(userId).map { it.toOrderHistoryItem() } },
    )

    private fun awaitOrNull(orderType: OrderType, future: CompletableFuture<List<OrderHistoryItem>>): List<OrderHistoryItem>? =
        try {
            future.get(DOMAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (exception: TimeoutException) {
            logger.warn("order history domain query timed out: domain={}", orderType, exception)
            null
        } catch (exception: Exception) {
            logger.warn("order history domain query failed: domain={}", orderType, exception)
            null
        }

    private fun paginate(items: List<OrderHistoryItem>, criteria: OrderHistoryCriteria): List<OrderHistoryItem> {
        val fromIndex = (criteria.page * criteria.size).coerceIn(0, items.size)
        val toIndex = (fromIndex + criteria.size).coerceIn(fromIndex, items.size)
        return items.subList(fromIndex, toIndex)
    }
}

private fun BookingOrderItem.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.BOOKING,
    sourceId = bookingId,
    title = title,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/bookings/$bookingId",
    createdAt = createdAt,
)

private fun GoodsOrderWithTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.GOODS,
    sourceId = order.id,
    title = title,
    status = order.status.name,
    paymentId = order.paymentId,
    detailPath = "/goods-orders/${order.id}",
    createdAt = order.createdAt,
)

private fun TicketOrderWithEventTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.TICKETING,
    sourceId = ticketOrderId,
    title = eventTitle,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/ticket-orders/$ticketOrderId",
    createdAt = createdAt,
)

private fun ApplicationWithRecruitmentTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.RECRUITMENT,
    sourceId = applicationId,
    title = recruitmentTitle,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/applications/$applicationId",
    createdAt = createdAt,
)
