package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.gateway.DropReservationCompensator
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

// [W1-DEBT-01] LimitedDropDomainServiceTest(LargeClass, 1179줄, 31 Given)를 관심사별로 분리하며
// 공유 상수·헬퍼를 이 파일로 추출했다. 각 스펙 파일이 동일한 fixture를 재사용해 중복 없이 분리한다.

internal const val DROP_ID = 0L
internal const val PRODUCT_ID = 10L
internal const val USER_ID = 100L
internal const val OWNER_USER_ID = 500L
internal const val PER_USER_LIMIT = 2
internal const val QUANTITY = 1
internal const val IDEMPOTENCY_KEY = "idem-key-1"

internal fun openDrop(): LimitedDrop = LimitedDrop.reconstitute(
    productId = PRODUCT_ID,
    openAt = ZonedDateTime.now().minusMinutes(1),
    closeAt = ZonedDateTime.now().plusDays(1),
    limitedQuantity = 100,
    perUserLimit = PER_USER_LIMIT,
    status = LimitedDropStatus.OPEN,
)

internal fun buildService(
    limitedDropRepository: LimitedDropRepository = mockk(),
    dropReservationStore: DropReservationStore = mockk(),
    goodsDomainService: GoodsDomainService = mockk(),
    domainEventPublisher: DomainEventPublisher = mockk(),
    dropReservationCompensator: DropReservationCompensator = mockk(relaxed = true),
    underSellReconciliationEnabled: Boolean = true,
    underSellGraceSeconds: Long = 60,
) = LimitedDropDomainService(
    limitedDropRepository = limitedDropRepository,
    dropReservationStore = dropReservationStore,
    goodsDomainService = goodsDomainService,
    domainEventPublisher = domainEventPublisher,
    dropReservationCompensator = dropReservationCompensator,
    underSellReconciliationEnabled = underSellReconciliationEnabled,
    underSellGraceSeconds = underSellGraceSeconds,
)

internal fun purchaseCommand(): PurchaseLimitedDropCommand = PurchaseLimitedDropCommand(
    dropId = DROP_ID,
    userId = USER_ID,
    quantity = QUANTITY,
    idempotencyKey = IDEMPOTENCY_KEY,
)

internal fun sampleSneakerProduct(ownerUserId: Long = OWNER_USER_ID): Product = Product.create(
    name = "한정판 스니커즈",
    category = ProductCategory.FOOTWEAR,
    price = BigDecimal("50000"),
    description = "설명",
    imageUrl = "https://image",
    ownerUserId = ownerUserId,
    sellerType = SellerType.B2C,
)
