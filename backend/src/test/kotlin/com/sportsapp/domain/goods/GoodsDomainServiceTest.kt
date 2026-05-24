package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class GoodsDomainServiceTest : BehaviorSpec({

    val productRepository = mockk<ProductRepository>()
    val stockRepository = mockk<StockRepository>()
    val productCustomRepository = mockk<ProductCustomRepository>()
    val popularProductsCache = mockk<PopularProductsCache>()
    val goodsOrderRepository = mockk<GoodsOrderRepository>()
    val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
    val cartDomainService = mockk<CartDomainService>()
    val service = GoodsDomainService(
        productRepository = productRepository,
        stockRepository = stockRepository,
        productCustomRepository = productCustomRepository,
        popularProductsCache = popularProductsCache,
        goodsOrderRepository = goodsOrderRepository,
        goodsOrderItemRepository = goodsOrderItemRepository,
        cartDomainService = cartDomainService,
    )

    Given("재고가 충분한 Product가 존재할 때") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://example.com/image.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )
        val stock = Stock(productId = 1L, quantity = 10)

        every { productRepository.findById(1L) } returns product
        every { stockRepository.findByProductId(1L) } returns stock
        every { stockRepository.save(any()) } returns stock

        When("deductStock을 호출하면") {
            service.deductStock(productId = 1L, quantity = 3)

            Then("[U-05] StockRepository.save가 1회 호출된다") {
                verify(exactly = 1) { stockRepository.save(any()) }
                stock.quantity shouldBe 7
            }
        }
    }

    Given("재고가 부족한 Product가 존재할 때") {
        val product = Product(
            name = "배드민턴 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/badminton.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )
        val stock = Stock(productId = 2L, quantity = 2)

        every { productRepository.findById(2L) } returns product
        every { stockRepository.findByProductId(2L) } returns stock

        When("5개를 차감 시도하면") {
            Then("[U-05] OutOfStockException이 발생한다") {
                shouldThrow<OutOfStockException> {
                    service.deductStock(productId = 2L, quantity = 5)
                }
            }
        }
    }

    Given("존재하지 않는 Product에 대해") {
        every { productRepository.findById(99L) } returns null

        When("deductStock을 호출하면") {
            Then("[U-05] ResourceNotFoundException이 발생한다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.ResourceNotFoundException> {
                    service.deductStock(productId = 99L, quantity = 1)
                }
            }
        }
    }

    Given("빈 items 목록으로 createPendingOrder를 호출할 때") {
        every { goodsOrderRepository.findByIdempotencyKey("idem-empty") } returns null

        When("execute하면") {
            Then("[U-01] EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> {
                    service.createPendingOrder(userId = 1L, items = emptyList(), idempotencyKey = "idem-empty")
                }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 items로 createPendingOrder를 호출할 때") {
        val inactiveProduct = Product(
            name = "단종 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("10000"),
            description = "단종",
            imageUrl = "https://example.com/old.jpg",
            status = ProductStatus.INACTIVE,
            ownerId = 1L,
        )
        every { productRepository.findById(50L) } returns inactiveProduct
        every { goodsOrderRepository.findByIdempotencyKey("idem-inactive") } returns null

        When("execute하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> {
                    service.createPendingOrder(
                        userId = 1L,
                        items = listOf(OrderItemInput(productId = 50L, quantity = 1)),
                        idempotencyKey = "idem-inactive",
                    )
                }
            }
        }
    }

    Given("재고가 충분한 ACTIVE 상품으로 createPendingOrder를 호출할 때") {
        val activeProduct = Product(
            name = "야구 글러브",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("80000"),
            description = "가죽 글러브",
            imageUrl = "https://example.com/glove.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )
        val stock = Stock(productId = 10L, quantity = 5)
        val savedOrder = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("160000"), idempotencyKey = "idem-create")

        every { goodsOrderRepository.findByIdempotencyKey("idem-create") } returns null
        every { productRepository.findById(10L) } returns activeProduct
        every { stockRepository.findByProductId(10L) } returns stock
        every { stockRepository.save(any()) } returns stock
        every { goodsOrderRepository.save(any()) } returns savedOrder
        every { goodsOrderItemRepository.saveAll(any()) } returns emptyList()

        When("2개 주문하면") {
            Then("[U-02] totalAmount = price × quantity로 계산된 주문이 저장된다") {
                val order = service.createPendingOrder(
                    userId = 1L,
                    items = listOf(OrderItemInput(productId = 10L, quantity = 2)),
                    idempotencyKey = "idem-create",
                )
                order.totalAmount.compareTo(BigDecimal("160000")) shouldBe 0
                verify(exactly = 1) { goodsOrderRepository.save(any()) }
            }
        }
    }

    Given("GoodsDomainService 메서드 시그니처 (B2B-17 fix: @Transactional UseCase 레이어로 이전)") {
        When("public 메서드 어노테이션을 검사하면") {
            Then("[U-03] @Transactional 어노테이션은 어느 public 메서드에도 선언돼 있지 않다") {
                val transactionalAnnotated = GoodsDomainService::class.java.declaredMethods
                    .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
                    .filter { method ->
                        method.annotations.any {
                            it.annotationClass.simpleName == "Transactional"
                        }
                    }
                transactionalAnnotated.size shouldBe 0
            }
        }
    }
})
