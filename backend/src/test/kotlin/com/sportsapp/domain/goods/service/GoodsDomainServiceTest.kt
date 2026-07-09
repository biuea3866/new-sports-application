package com.sportsapp.domain.goods.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.repository.PopularProductsCache
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.exception.OutOfStockException
import com.sportsapp.domain.goods.exception.EmptyOrderException
import com.sportsapp.domain.goods.exception.ProductInactiveException
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.common.security.AuthChannelResolver
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/** 순수 단위 테스트에서 JPA 생성 전략으로 채워질 id를 강제 주입한다(McpTokenDomainServiceTest와 동일 패턴). */
private fun <T : Any> forceId(entity: T, id: Long): T {
    val idField = entity.javaClass.getDeclaredField("id")
    idField.isAccessible = true
    idField.set(entity, id)
    return entity
}

class GoodsDomainServiceTest : BehaviorSpec({

    val productRepository = mockk<ProductRepository>()
    val stockRepository = mockk<StockRepository>()
    val productCustomRepository = mockk<ProductCustomRepository>()
    val popularProductsCache = mockk<PopularProductsCache>()
    val goodsOrderRepository = mockk<GoodsOrderRepository>()
    val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
    val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
    val limitedDropRepository = mockk<LimitedDropRepository>()
    val authChannelResolver = mockk<AuthChannelResolver>()
    val service = GoodsDomainService(
        productRepository = productRepository,
        stockRepository = stockRepository,
        productCustomRepository = productCustomRepository,
        popularProductsCache = popularProductsCache,
        goodsOrderRepository = goodsOrderRepository,
        goodsOrderItemRepository = goodsOrderItemRepository,
        goodsOrderCustomRepository = goodsOrderCustomRepository,
        limitedDropRepository = limitedDropRepository,
        authChannelResolver = authChannelResolver,
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

            Then("StockRepository.save가 1회 호출된다") {
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
            Then("OutOfStockException이 발생한다") {
                shouldThrow<OutOfStockException> {
                    service.deductStock(productId = 2L, quantity = 5)
                }
            }
        }
    }

    Given("존재하지 않는 Product에 대해") {
        every { productRepository.findById(99L) } returns null

        When("deductStock을 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.ResourceNotFoundException> {
                    service.deductStock(productId = 99L, quantity = 1)
                }
            }
        }
    }

    Given("빈 items 목록으로 createPendingOrder를 호출할 때") {
        every { goodsOrderRepository.findByIdempotencyKey("idem-empty") } returns null

        When("execute하면") {
            Then("EmptyOrderException이 발생한다") {
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
            Then("ProductInactiveException이 발생한다") {
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
            Then("totalAmount = price × quantity로 계산된 주문이 저장된다") {
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

    Given("활성 한정판 회차가 연결된 상품을 단건 조회하는 상황") {
        val product = Product(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://example.com/sneaker.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )
        val stock = Stock(productId = 20L, quantity = 30)
        val openDrop = LimitedDrop.reconstitute(
            productId = 20L,
            openAt = ZonedDateTime.now().minusHours(1),
            closeAt = ZonedDateTime.now().plusHours(1),
            limitedQuantity = 30,
            perUserLimit = 2,
            status = com.sportsapp.domain.goods.entity.LimitedDropStatus.OPEN,
        )

        every { productRepository.findByIdAndDeletedAtIsNull(20L) } returns product
        every { stockRepository.findByProductId(20L) } returns stock
        every { limitedDropRepository.findOpenByProductId(20L) } returns openDrop

        When("getProductWithStock을 호출하면") {
            val result = service.getProductWithStock(20L)

            Then("활성 회차의 dropId를 limitedDropId로 결합한다") {
                result.limitedDropId shouldBe openDrop.id
            }
        }
    }

    Given("활성 한정판 회차가 없는 상품을 단건 조회하는 상황") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )
        val stock = Stock(productId = 21L, quantity = 30)

        every { productRepository.findByIdAndDeletedAtIsNull(21L) } returns product
        every { stockRepository.findByProductId(21L) } returns stock
        every { limitedDropRepository.findOpenByProductId(21L) } returns null

        When("getProductWithStock을 호출하면") {
            val result = service.getProductWithStock(21L)

            Then("limitedDropId는 null이다") {
                result.limitedDropId shouldBe null
            }
        }
    }

    Given("검색 결과 2건 중 1건에만 활성 한정판 회차가 있는 상황") {
        val productWithDrop = forceId(
            Product(
                name = "한정판 스니커즈",
                category = ProductCategory.FOOTWEAR,
                price = BigDecimal("50000"),
                description = "설명",
                imageUrl = "https://example.com/sneaker.jpg",
                status = ProductStatus.ACTIVE,
                ownerId = 1L,
            ),
            id = 20L,
        )
        val productWithoutDrop = forceId(
            Product(
                name = "테니스 라켓",
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("30000"),
                description = "설명",
                imageUrl = "https://example.com/racket.jpg",
                status = ProductStatus.ACTIVE,
                ownerId = 1L,
            ),
            id = 21L,
        )
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(
            listOf(
                ProductWithStock(product = productWithDrop, stockQuantity = 10),
                ProductWithStock(product = productWithoutDrop, stockQuantity = 5),
            ),
            pageable,
            2,
        )
        val openDrop = LimitedDrop.reconstitute(
            productId = productWithDrop.id,
            openAt = ZonedDateTime.now().minusHours(1),
            closeAt = ZonedDateTime.now().plusHours(1),
            limitedQuantity = 10,
            perUserLimit = 2,
            status = com.sportsapp.domain.goods.entity.LimitedDropStatus.OPEN,
        )

        every {
            productCustomRepository.search(null, null, null, null, null, pageable)
        } returns page
        every {
            limitedDropRepository.findOpenByProductIds(listOf(productWithDrop.id, productWithoutDrop.id))
        } returns listOf(openDrop)

        When("search를 호출하면") {
            val result = service.search(null, null, null, null, null, pageable)

            Then("활성 회차가 있는 상품만 limitedDropId가 채워진다") {
                result.content[0].limitedDropId shouldBe openDrop.id
                result.content[1].limitedDropId shouldBe null
            }
        }
    }

    Given("검색 결과 상품 1건에 활성 회차가 2건 연결된 상황") {
        val product = forceId(
            Product(
                name = "한정판 스니커즈",
                category = ProductCategory.FOOTWEAR,
                price = BigDecimal("50000"),
                description = "설명",
                imageUrl = "https://example.com/sneaker.jpg",
                status = ProductStatus.ACTIVE,
                ownerId = 1L,
            ),
            id = 30L,
        )
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(
            listOf(ProductWithStock(product = product, stockQuantity = 10)),
            pageable,
            1,
        )
        val olderDrop = forceId(
            LimitedDrop.reconstitute(
                productId = product.id,
                openAt = ZonedDateTime.now().minusHours(3),
                closeAt = ZonedDateTime.now().plusHours(1),
                limitedQuantity = 10,
                perUserLimit = 2,
                status = com.sportsapp.domain.goods.entity.LimitedDropStatus.SOLD_OUT,
            ),
            id = 301L,
        )
        val newerDrop = forceId(
            LimitedDrop.reconstitute(
                productId = product.id,
                openAt = ZonedDateTime.now().minusHours(1),
                closeAt = ZonedDateTime.now().plusHours(2),
                limitedQuantity = 5,
                perUserLimit = 1,
                status = com.sportsapp.domain.goods.entity.LimitedDropStatus.OPEN,
            ),
            id = 302L,
        )

        every {
            productCustomRepository.search(null, null, null, null, null, pageable)
        } returns page
        every {
            limitedDropRepository.findOpenByProductIds(listOf(product.id))
            // 배치 조회 결과 순서가 openAt 오름차순으로 오더라도 결과가 바뀌면 안 된다.
        } returns listOf(olderDrop, newerDrop)

        When("search를 호출하면") {
            val result = service.search(null, null, null, null, null, pageable)

            Then("openAt이 가장 최신인 회차의 dropId가 채워진다(단건 조회의 OrderByOpenAtDesc와 동일 기준)") {
                result.content[0].limitedDropId shouldBe newerDrop.id
            }
        }
    }

    Given("검색 결과가 없는 상황") {
        // 이 Given 전용 fresh mock — 스펙 전체가 공유하는 top-level limitedDropRepository는
        // 다른 Given의 호출 이력과 섞여 verify(exactly = 0)이 오염될 수 있다.
        val isolatedProductCustomRepository = mockk<ProductCustomRepository>()
        val isolatedLimitedDropRepository = mockk<LimitedDropRepository>()
        val isolatedService = GoodsDomainService(
            productRepository = productRepository,
            stockRepository = stockRepository,
            productCustomRepository = isolatedProductCustomRepository,
            popularProductsCache = popularProductsCache,
            goodsOrderRepository = goodsOrderRepository,
            goodsOrderItemRepository = goodsOrderItemRepository,
            goodsOrderCustomRepository = goodsOrderCustomRepository,
            limitedDropRepository = isolatedLimitedDropRepository,
            authChannelResolver = authChannelResolver,
        )
        val pageable = PageRequest.of(0, 20)
        val emptyPage = PageImpl<ProductWithStock>(emptyList(), pageable, 0)

        every {
            isolatedProductCustomRepository.search(null, null, null, null, null, pageable)
        } returns emptyPage

        When("search를 호출하면") {
            val result = isolatedService.search(null, null, null, null, null, pageable)

            Then("LimitedDropRepository를 호출하지 않고 빈 페이지를 반환한다") {
                result.content shouldBe emptyList()
                verify(exactly = 0) { isolatedLimitedDropRepository.findOpenByProductIds(any()) }
            }
        }
    }

    Given("파트너 API Key 인증을 경유한 등록 요청 상황") {
        every { authChannelResolver.isPartnerAuthenticated() } returns true
        val savedProductSlot = io.mockk.slot<Product>()
        every { productRepository.save(capture(savedProductSlot)) } answers { savedProductSlot.captured }
        every { stockRepository.save(any()) } answers { firstArg<Stock>() }

        When("createProduct를 호출하면") {
            val (product, _) = service.createProduct(
                name = "브랜드 정품 저지",
                category = ProductCategory.APPAREL,
                price = BigDecimal("89000"),
                description = "설명",
                imageUrl = "https://example.com/jersey.jpg",
                ownerUserId = 100L,
            )

            Then("sellerType이 B2B로 저장된다") {
                product.sellerType shouldBe SellerType.B2B
                verify(exactly = 1) { authChannelResolver.isPartnerAuthenticated() }
            }
        }
    }

    Given("일반 JWT 인증으로 온 등록 요청 상황") {
        every { authChannelResolver.isPartnerAuthenticated() } returns false
        val savedProductSlot = io.mockk.slot<Product>()
        every { productRepository.save(capture(savedProductSlot)) } answers { savedProductSlot.captured }
        every { stockRepository.save(any()) } answers { firstArg<Stock>() }

        When("createProduct를 호출하면") {
            val (product, _) = service.createProduct(
                name = "중고 러닝화",
                category = ProductCategory.FOOTWEAR,
                price = BigDecimal("15000"),
                description = "설명",
                imageUrl = "https://example.com/shoes.jpg",
                ownerUserId = 100L,
            )

            Then("sellerType이 B2C로 저장된다") {
                product.sellerType shouldBe SellerType.B2C
            }
        }
    }

    Given("사용자의 GoodsOrder 통합조회(title 조인) 상황") {
        val userId = 50L
        val pageable = PageRequest.of(0, 20)
        val order = GoodsOrder.create(userId = userId, totalAmount = BigDecimal("30000"))
        val withTitle = com.sportsapp.domain.goods.dto.GoodsOrderWithTitle(order = order, title = "나이키 러닝화")
        val page = PageImpl(listOf(withTitle), pageable, 1)

        every { goodsOrderCustomRepository.findBy(userId, pageable) } returns page

        When("listMyOrdersWithTitle을 호출하면") {
            val result = service.listMyOrdersWithTitle(userId, pageable)

            Then("GoodsOrderCustomRepository.findBy 결과를 그대로 반환한다") {
                result.content[0].title shouldBe "나이키 러닝화"
                verify(exactly = 1) { goodsOrderCustomRepository.findBy(userId, pageable) }
            }
        }
    }

    // @Transactional은 UseCase 레이어에서만 선언한다(DomainService 메서드에는 선언하지 않음).
    Given("GoodsDomainService 메서드 시그니처") {
        When("public 메서드 어노테이션을 검사하면") {
            Then("@Transactional 어노테이션은 어느 public 메서드에도 선언돼 있지 않다") {
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
