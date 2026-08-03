package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.common.security.AuthChannelResolver
import com.sportsapp.domain.goods.GoodsFeatureFlagKeys
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.goods.dto.GoodsOrderDetail
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryFilterResult
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryTtlPolicy
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.exception.EmptyOrderException
import com.sportsapp.domain.goods.exception.GoodsOrderNotFoundException
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.repository.PopularProductsCache
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class GoodsDomainService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productCustomRepository: ProductCustomRepository,
    private val popularProductsCache: PopularProductsCache,
    private val goodsOrderRepository: GoodsOrderRepository,
    private val goodsOrderItemRepository: GoodsOrderItemRepository,
    private val goodsOrderCustomRepository: GoodsOrderCustomRepository,
    private val limitedDropRepository: LimitedDropRepository,
    private val authChannelResolver: AuthChannelResolver,
    private val dropReservationStore: DropReservationStore,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {
    /**
     * catalog 통합검색(BE-07 예정)이 재사용하는 조회 — [sellerType]은 옵션 필터(B2B 브랜드 상품만
     * 보고 싶을 때), status=ACTIVE는 항상 강제한다(비활성 상품은 공개 검색 대상이 아니다).
     */
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        sellerType: SellerType?,
        pageable: Pageable,
    ): Page<ProductWithStock> {
        val page = productCustomRepository.search(category, keyword, priceMin, priceMax, sellerType, pageable)
        return enrichWithLimitedDropId(page)
    }

    /**
     * `/products` 목록 응답·catalog 통합검색(BE-07)의 한정판 진입점 배너용 — 페이지 내 상품 id를
     * 한 번에 모아 [LimitedDropRepository.findOpenByProductIds]로 배치 조회한다(N+1 방지).
     *
     * 한 상품에 활성 회차가 2건 이상이면 openAt이 가장 최신인 회차를 선택한다 —
     * 단건 조회 경로([LimitedDropRepository.findOpenByProductId]의
     * `OrderByOpenAtDesc`)와 선택 기준을 일치시켜, 목록과 상세의 limitedDropId가
     * 달라지지 않게 한다(코드 리뷰 p3).
     *
     * [limitedDropStatus]도 함께 채운다 — [DropReservationStore.remaining]으로 잔여 수량을 조회해
     * [LimitedDrop.effectiveStatus]로 파생한다. catalog 통합검색이 품절(SOLD_OUT) 회차를 ACTIVE로
     * 오노출하지 않기 위해 필요하다(코드 리뷰 p2).
     */
    private fun enrichWithLimitedDropId(page: Page<ProductWithStock>): Page<ProductWithStock> {
        if (page.content.isEmpty()) return page
        val productIds = page.content.map { it.product.id }
        val dropByProductId = limitedDropRepository.findOpenByProductIds(productIds)
            .sortedByDescending { it.openAt }
            .distinctBy { it.productId }
            .associateBy { it.productId }
        val enrichedContent = page.content.map { productWithStock -> enrichWithLimitedDrop(productWithStock, dropByProductId) }
        return PageImpl(enrichedContent, page.pageable, page.totalElements)
    }

    private fun enrichWithLimitedDrop(productWithStock: ProductWithStock, dropByProductId: Map<Long, LimitedDrop>): ProductWithStock {
        val drop = dropByProductId[productWithStock.product.id] ?: return productWithStock
        val remaining = dropReservationStore.remaining(drop.id)
        return productWithStock.copy(limitedDropId = drop.id, limitedDropStatus = drop.effectiveStatus(remaining))
    }

    fun deductStock(productId: Long, quantity: Int) {
        productRepository.findById(productId) ?: throw ResourceNotFoundException("Product", productId)
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.deduct(quantity)
        stockRepository.save(stock)
    }

    fun restoreStock(productId: Long, quantity: Int) {
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.restore(quantity)
        stockRepository.save(stock)
    }

    fun getPopular(category: ProductCategory): List<PopularProductSnapshot> {
        popularProductsCache.get(category)?.let { return it }
        // TODO(GOODS-05): 판매 수 집계 기반 정렬로 교체
        val snapshots = productRepository.findByCategoryAndStatus(category, ProductStatus.ACTIVE)
            .sortedByDescending { it.createdAt }
            .take(POPULAR_LIMIT)
            .map { PopularProductSnapshot.of(it) }
        popularProductsCache.put(category, snapshots)
        return snapshots
    }

    fun invalidatePopularCache(category: ProductCategory) {
        popularProductsCache.invalidate(category)
    }

    fun createPendingOrder(userId: Long, items: List<OrderItemInput>, idempotencyKey: String): GoodsOrder {
        goodsOrderRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }
        if (items.isEmpty()) throw EmptyOrderException()
        val products = items.associate { item -> item.productId to validateAndDeductStock(item) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(products.getValue(item.productId).price.multiply(BigDecimal(item.quantity)))
        }
        val order = goodsOrderRepository.save(GoodsOrder.create(userId, totalAmount, idempotencyKey))
        val orderItems = items.map { item ->
            GoodsOrderItem(
                orderId = order.id,
                productId = item.productId,
                quantity = item.quantity,
                unitPrice = products.getValue(item.productId).price,
            )
        }
        goodsOrderItemRepository.saveAll(orderItems)
        return order
    }

    private fun validateAndDeductStock(item: OrderItemInput): Product {
        val product = productRepository.findById(item.productId)
            ?: throw ResourceNotFoundException("Product", item.productId)
        product.requireActive()
        val stock = stockRepository.findByProductId(item.productId)
            ?: throw ResourceNotFoundException("Stock", item.productId)
        stock.requireSufficient(item.quantity)
        stock.deduct(item.quantity)
        stockRepository.save(stock)
        return product
    }

    /**
     * 언더셀 대사(reconciliation, FIX-04)용 — idempotencyKey에 대응하는 `goods_orders` 행 존재 여부.
     * [createPendingOrder]의 내부 멱등 조회와 동일한 유니크 인덱스(`idempotency_key`)를 사용한다.
     */
    fun hasOrderFor(idempotencyKey: String): Boolean = goodsOrderRepository.findByIdempotencyKey(idempotencyKey) != null

    fun cancelPendingOrder(orderId: Long) {
        val order = goodsOrderRepository.findById(orderId)
            ?: throw GoodsOrderNotFoundException(orderId)
        if (order.status == GoodsOrderStatus.CANCELLED) return
        order.cancel()
        goodsOrderRepository.save(order)
        restoreStockAndSoftDeleteItems(orderId)
    }

    /**
     * [cancelPendingOrder](결제 취소 webhook)와 [expireOrders](W1-11a 만료 스위퍼)가 공유하는
     * 재고 복원 + 아이템 soft-delete 로직 — 티켓 "취소 시 재고 복원은 기존 cancelPendingOrder
     * 경로를 재사용한다"의 실제 구현이다. 스위퍼 경로는 CAS(tryExpire) 성공 이후에만 이
     * 메서드를 호출해 재고 이중 복원을 막는다(호출부가 원자성을 보장).
     */
    private fun restoreStockAndSoftDeleteItems(orderId: Long) {
        val items = goodsOrderItemRepository.findByOrderId(orderId)
        items.forEach { item ->
            val stock = stockRepository.findByProductId(item.productId)
                ?: throw ResourceNotFoundException("Stock", item.productId)
            stock.restore(item.quantity)
            stockRepository.save(stock)
        }
        items.forEach { it.softDelete(null) }
        if (items.isNotEmpty()) goodsOrderItemRepository.saveAll(items)
    }

    /**
     * **CAS가 프로덕션 전이 경로다.** 실제 확정은
     * [GoodsOrderCustomRepository.tryConfirm](조건부 UPDATE, WHERE status='PENDING')이
     * 수행한다 — 비잠금 find→mutate→save 경로는 만료 스위퍼([expireOrders])가 먼저 커밋한
     * CANCELLED(+재고 복원)를 조건 없는 dirty-checking UPDATE로 덮어써, 이미 다른 곳에
     * 풀린 재고를 CONFIRMED 주문이 차지한 것처럼 보이게 하는 반대 방향 lost update(재고
     * 이중 차감)를 만들 수 있어 tryExpire와 대칭으로 CAS로 닫았다.
     *
     * CAS 실패 시 재조회한 현재 상태로 원인을 가른다 — 이미 같은 paymentId로 CONFIRMED면
     * 멱등(webhook 중복), 그 외(CANCELLED 등)면 [InvalidGoodsOrderStateException]을 던져
     * 상태 머신 우회를 막는다.
     *
     * **호출 계약(KDoc contract)**: 이 메서드 호출 **이전에 같은 트랜잭션에서 대상
     * GoodsOrder를 먼저 로드하지 말 것.** [GoodsOrderCustomRepository.tryConfirm]은 QueryDSL
     * 벌크 UPDATE라 JPA 1차 캐시를 무효화하지 않는다 — 이미 로드된 GoodsOrder가 있으면 아래
     * `findById`가 그 stale 인스턴스를 그대로 반환해 status가 실제 DB 값과 어긋날 수 있다
     * (`facility-booking`(W1-11c) `BookingDomainService.confirmBooking`과 동일 계약).
     * 현재 유일 호출부(webhook 확정 경로)는 이 계약을 지키고 있다.
     */
    fun markPaid(orderId: Long, paymentId: Long): GoodsOrder {
        val transitioned = goodsOrderCustomRepository.tryConfirm(orderId = orderId, paymentId = paymentId)
        val current = goodsOrderRepository.findById(orderId) ?: throw GoodsOrderNotFoundException(orderId)
        if (!transitioned) {
            if (current.status == GoodsOrderStatus.CONFIRMED && current.paymentId == paymentId) return current
            throw InvalidGoodsOrderStateException(current.status, GoodsOrderStatus.CONFIRMED)
        }
        return current
    }

    /**
     * W1-11a 만료 스위퍼 — PENDING이며 createdAt < (now - ttlMinutes, 빠른 TTL)이고
     * id > afterId(청크 커서)인 주문 후보를 최대 limit건 조회한다. 시간 계산은 이 메서드
     * 내부에서 해결한다(no-time-parameter). `facility-booking`(W1-11c)
     * `findExpirableBookingCandidates`와 동일한 이유로 named argument를 강제한다 —
     * `ttlMinutes`(Long)와 `afterId`(Long)가 인접한 동일 타입이라 위치 인자로 바꿔 넘기면
     * 컴파일은 통과하되 TTL↔커서가 뒤바뀌는 오동작이 조용히 재발할 수 있다.
     */
    fun findExpirableGoodsOrderCandidates(ttlMinutes: Long, afterId: Long, limit: Int): List<GoodsOrderExpiryCandidate> {
        val threshold = ZonedDateTime.now().minusMinutes(ttlMinutes)
        return goodsOrderCustomRepository.findPendingCreatedBefore(threshold, afterId, limit)
    }

    /**
     * W1-11a 만료 스위퍼 — 만료 후보 중 실제로 만료시킬 대상을 최종 판정한다. 판정 로직
     * (settled 우선 제외·Live의 두 창 AND 결합·단조성)은 이 메서드가 재구현하지 않고
     * [OrderPaymentLiveness.allowsExpiry]로 전량 위임한다 — `facility-booking`(W1-11c)에서
     * "승자 하나 고르기" 구조가 세 번(6차·7차·8차) 재발한 결함을 소비 도메인이 각자
     * 재구현하지 못하도록 타입으로 강제한 결과이며, goods·ticketing·recruitment가 모두
     * 이 위임 하나만 지키면 같은 결함이 재발할 수 없다.
     */
    fun filterExpirable(
        candidates: List<GoodsOrderExpiryCandidate>,
        liveness: Map<Long, OrderPaymentLiveness>,
        ttlPolicy: GoodsOrderExpiryTtlPolicy,
    ): GoodsOrderExpiryFilterResult {
        val now = ZonedDateTime.now()
        val fastThreshold = now.minusMinutes(ttlPolicy.ttlMinutes)
        val readyThreshold = now.minusMinutes(ttlPolicy.readyTtlMinutes)
        val settled = candidates.filter { liveness[it.orderId] is OrderPaymentLiveness.Settled }
        val expirableIds = candidates
            .filterNot { liveness[it.orderId] is OrderPaymentLiveness.Settled }
            .filter { candidate ->
                val candidateLiveness = liveness[candidate.orderId] ?: OrderPaymentLiveness.None
                candidateLiveness.allowsExpiry(candidate.createdAt, readyThreshold, fastThreshold)
            }
            .map { it.orderId }
        return GoodsOrderExpiryFilterResult(expirableIds = expirableIds, skippedSettledCount = settled.size)
    }

    /**
     * W1-11a 만료 스위퍼 — 청크 단위로 PENDING → CANCELLED CAS 전이한다
     * ([GoodsOrderCustomRepository.tryExpire]). booking과 달리 goods는 만료 시 **재고
     * 복원**이 필요하므로(슬롯 점유처럼 상태만으로 파생되지 않는다), CAS가 실제로 성공한
     * 건(이 호출이 PENDING→CANCELLED 전이를 이겼다는 뜻)에 한해서만
     * [restoreStockAndSoftDeleteItems]를 호출한다 — CAS 경합에서 진 건(이미 다른
     * 트랜잭션이 CONFIRMED로 전이시킨 건)은 재고를 건드리지 않아 이중 복원을 막는다.
     * 트랜잭션 경계는 이 메서드를 호출하는 UseCase(`ExpireGoodsOrderChunkUseCase`)가
     * 소유한다 — DomainService는 트랜잭션을 선언하지 않는다.
     */
    fun expireOrders(orderIds: List<Long>): Int {
        if (orderIds.isEmpty()) return 0
        return orderIds.count { orderId ->
            val expired = goodsOrderCustomRepository.tryExpire(orderId)
            if (expired) restoreStockAndSoftDeleteItems(orderId)
            expired
        }
    }

    /**
     * goods.expiry.enabled 운영 킬 스위치 판정 — 부팅 고정 설정이 아니라 매 스케줄 주기
     * `FeatureFlagEvaluator`로 런타임 조회한다(no-conditional-on-property).
     */
    fun isExpiryEnabled(): Boolean =
        featureFlagEvaluator.isEnabled(GoodsFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)

    /**
     * 주문 상세 조회 — 통합 주문내역 리스트([listMyOrdersWithTitle])와 동일한 대표 상품명(title)을
     * 함께 반환해 상세가 리스트보다 빈약해지는 역전을 막는다(Option A+).
     */
    fun getOrder(userId: Long, orderId: Long): GoodsOrderDetail {
        val order = goodsOrderRepository.findById(orderId)
            ?: throw GoodsOrderNotFoundException(orderId)
        order.requireOwnedBy(userId)
        val items = goodsOrderItemRepository.findByOrderId(orderId)
        val title = goodsOrderCustomRepository.findTitleFor(orderId)
        return GoodsOrderDetail(order = order, items = items, title = title)
    }

    fun listMyOrders(userId: Long, pageable: Pageable): Page<GoodsOrder> =
        goodsOrderRepository.findByUserId(userId, pageable)

    /**
     * order 통합조회(BE-08 예정)가 재사용하는 조회 — 대표 상품명(다건 시 "외 N건")을 조인해
     * 함께 반환한다(TDD "주문 표시명 확보 방식", GoodsOrder→Product는 동일 goods 컨텍스트).
     */
    fun listMyOrdersWithTitle(userId: Long, pageable: Pageable): Page<GoodsOrderWithTitle> =
        goodsOrderCustomRepository.findBy(userId, pageable)

    /**
     * sellerType은 등록 시점 인증 채널로 자동 판별한다(TDD "방안 3", 상태 전이표 — 이후 불변).
     * [command]에 실린 값이 있어도 이 판별을 우회하지 않는다(no-external-state-check) — 판별
     * 로직 자체를 domain에 두어 호출부가 값을 조작해 정책을 우회할 여지를 없앤다.
     */
    fun createProduct(
        name: String,
        category: ProductCategory,
        price: java.math.BigDecimal,
        description: String,
        imageUrl: String,
        ownerUserId: Long,
    ): Pair<Product, Stock> {
        val sellerType = SellerType.fromPartnerAuthenticated(authChannelResolver.isPartnerAuthenticated())
        val product = productRepository.save(
            Product.create(
                name = name,
                category = category,
                price = price,
                description = description,
                imageUrl = imageUrl,
                ownerUserId = ownerUserId,
                sellerType = sellerType,
            )
        )
        val stock = stockRepository.save(Stock(productId = product.id, quantity = 0))
        return product to stock
    }

    /**
     * message 도메인의 `GoodsProductGateway`(ACL) 가 호출하는 소유자 id 전용 조회 (PH0-03).
     * [getProductWithStock]을 재사용하지 않는다 — 소유자 id 하나를 얻으려고 재고까지 조회하는
     * 불필요한 결합과 `ProductWithStock` 래퍼 노출(no-getter-chain-behavior 유발)을 막는다.
     */
    fun findOwnerIdBy(productId: Long): Long {
        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        return product.ownerId
    }

    fun getProductWithStock(productId: Long): ProductWithStock {
        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        val limitedDropId = limitedDropRepository.findOpenByProductId(productId)?.id
        return ProductWithStock(product = product, stockQuantity = stockQuantity, limitedDropId = limitedDropId)
    }

    fun getProductByIdAndOwnerId(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun updateProduct(
        productId: Long,
        ownerUserId: Long,
        name: String?,
        category: ProductCategory?,
        price: java.math.BigDecimal?,
        description: String?,
        imageUrl: String?,
    ): ProductWithStock {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.update(name, category, price, description, imageUrl)
        val saved = productRepository.save(productEntity)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = saved, stockQuantity = stockQuantity)
    }

    fun activateProduct(productId: Long, ownerUserId: Long): Product {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.activate()
        return productRepository.save(productEntity)
    }

    fun activateProductWithStock(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = activateProduct(productId, ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun deactivateProduct(productId: Long, ownerUserId: Long): Product {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.deactivate()
        return productRepository.save(productEntity)
    }

    fun deactivateProductWithStock(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = deactivateProduct(productId, ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun listMyProducts(ownerUserId: Long, pageable: Pageable): Page<ProductWithStock> =
        productCustomRepository.findByOwnerId(ownerUserId, pageable)

    /** BE-11 배치 백필 검증 스텝이 호출 — seller_type NULL 잔여 건수. */
    fun countProductsMissingSellerType(): Long = productRepository.countBySellerTypeIsNull()

    fun countActiveProductsByOwnerId(ownerId: Long): Long =
        productRepository.countByOwnerIdAndStatus(ownerId, ProductStatus.ACTIVE)

    fun countOutOfStockProductsByOwnerId(ownerId: Long): Long =
        stockRepository.countOutOfStockByOwnerId(ownerId)

    fun restoreProductStock(productId: Long, ownerUserId: Long, quantity: Int) {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        restoreStock(productId, quantity)
    }

    fun countConfirmedOrdersByOwnerUserId(ownerUserId: Long): Long =
        goodsOrderCustomRepository.countConfirmedByProductOwnerUserId(ownerUserId)

    fun sumRevenueByOwnerUserId(ownerUserId: Long): BigDecimal =
        goodsOrderCustomRepository.sumRevenueByProductOwnerUserId(ownerUserId)

    /**
     * 판매자의 상품이 담긴 굿즈 주문 id → 그 판매자 몫 금액.
     * 혼합 주문에서 결제 총액을 그대로 매출로 계상하지 않기 위해 귀속 금액을 함께 돌려준다.
     */
    fun findGoodsOrderSellerAmounts(ownerUserId: Long): Map<Long, BigDecimal> =
        goodsOrderCustomRepository.findSellerAmountsByProductOwnerUserId(ownerUserId)

    fun aggregateGoodsKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): GoodsKpiSummary {
        val periodRevenue = goodsOrderCustomRepository.sumRevenueByProductOwnerUserIdAndDateRange(ownerUserId, from, to)
        val outOfStockSkuCount = stockRepository.countOutOfStockByOwnerId(ownerUserId)
        val activeProductCount = productRepository.countByOwnerIdAndStatus(ownerUserId, ProductStatus.ACTIVE)

        // 매출 합산 조회 창(sumRevenueByProductOwnerUserIdAndDateRange)은 goe(from)~loe(to)로
        // 양끝을 포함한다. ChronoUnit.DAYS.between은 종료일을 포함하지 않으므로 그대로 쓰면
        // 분자(N일 매출)를 분모(N-1일)로 나눠 일 평균이 체계적으로 과대해진다(off-by-one).
        // +1을 더해 양끝 포함 일수로 맞춘다.
        val dayCount = (ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1).coerceAtLeast(1)
        val dailyRevenueTotal = periodRevenue.divide(BigDecimal(dayCount), 2, RoundingMode.HALF_UP)

        val inventoryTurnoverRate = if (activeProductCount > 0) {
            periodRevenue.divide(BigDecimal(activeProductCount), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return GoodsKpiSummary(
            dailyRevenueTotal = dailyRevenueTotal,
            inventoryTurnoverRate = inventoryTurnoverRate,
            outOfStockSkuCount = outOfStockSkuCount,
        )
    }

    companion object {
        private const val POPULAR_LIMIT = 20
    }
}
