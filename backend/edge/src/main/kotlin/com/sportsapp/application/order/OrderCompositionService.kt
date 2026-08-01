package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryResponse
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.order.dto.OrderHistoryItem
import com.sportsapp.domain.order.gateway.OrderHistoryGateway
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(OrderCompositionService::class.java)
private const val DOMAIN_TIMEOUT_MILLIS = 300L

/**
 * order 통합조회(BE-08) 조합 로직 — 4개 주문 도메인(booking/goods/ticketing/recruitment)의 이름
 * 포함 주문 읽기를 [OrderHistoryGateway]로 위임하고 병렬 fan-out해 `OrderHistoryItem`으로 매핑한
 * 단일 응답으로 합친다.
 *
 * 파사드는 이름을 만들지 않는다 — 각 컨텍스트가 반환한 title을 그대로 매핑만 한다(TDD
 * "주문 표시명 확보 방식"). DomainService 없음(dashboard 패턴), read-only 조합 전용.
 *
 * [S2-01] 이전에는 4개 코어 DomainService(BookingDomainService 등)를 직접 주입했다 — edge가 이
 * 서비스들의 소유 모듈(commerce·facility-booking·social)을 컴파일 의존해야 했다. 지금은 edge
 * 소유 [OrderHistoryGateway] 하나만 주입한다 — fan-out·타임아웃·부분 저하 로직은 이동 전과
 * 완전히 동일하고, "무엇을 조회하는가"만 Gateway 뒤로 숨었다.
 */
@Service
class OrderCompositionService(
    private val orderHistoryGateway: OrderHistoryGateway,
    @Qualifier("orderHistoryExecutor") private val orderHistoryExecutor: AsyncTaskExecutor,
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
        val submissions = buildTasks(userId, windowSize).mapValues { (orderType, task) -> trySubmit(orderType, task) }
        return submissions.mapValues { (orderType, future) -> future?.let { awaitOrNull(orderType, it) } }
    }

    private fun buildTasks(userId: Long, windowSize: Int): Map<OrderType, () -> List<OrderHistoryItem>> = mapOf(
        OrderType.BOOKING to { orderHistoryGateway.findBookingOrders(userId) },
        OrderType.GOODS to { orderHistoryGateway.findGoodsOrders(userId, PageRequest.of(0, windowSize)) },
        OrderType.TICKETING to { orderHistoryGateway.findTicketingOrders(userId) },
        OrderType.RECRUITMENT to { orderHistoryGateway.findRecruitmentOrders(userId) },
    )

    /**
     * `CompletableFuture.supplyAsync(task, executor)`는 내부에서 `executor.execute(...)`를
     * 제출 시점에 동기 호출한다 — bounded `orderHistoryExecutor`가 포화(코어+큐 가득) 상태면
     * `RejectedExecutionException`이 이 호출에서 즉시 던져진다. 이를 [awaitOrNull]과 동일하게
     * 해당 도메인만 실패 처리(failedDomains)해 나머지 도메인 응답까지 500으로 막지 않는다(FR-11).
     */
    private fun trySubmit(orderType: OrderType, task: () -> List<OrderHistoryItem>): CompletableFuture<List<OrderHistoryItem>>? =
        try {
            CompletableFuture.supplyAsync(task, orderHistoryExecutor)
        } catch (exception: Exception) {
            logger.warn("order history domain query submission rejected: domain={}", orderType, exception)
            null
        }

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
