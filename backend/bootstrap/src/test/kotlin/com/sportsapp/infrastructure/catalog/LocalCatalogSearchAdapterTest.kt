package com.sportsapp.infrastructure.catalog

import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.vo.SellerType
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.service.ProgramDomainService
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.EventWithMinSeatPrice
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import com.sportsapp.domain.goods.vo.SellerType as GoodsSellerType

/**
 * S2-01 — [LocalCatalogSearchAdapter]가 [com.sportsapp.domain.catalog.gateway.CatalogSearchGateway]
 * 계약을 4개 코어 DomainService 호출 + [com.sportsapp.domain.catalog.dto.CatalogItem] 매핑으로
 * 만족시키는지 검증한다. fan-out·타임아웃·부분 저하는 edge의 `CatalogCompositionServiceTest`가
 * 검증하므로 여기서는 순수 매핑 정확성(이동 전 `CatalogCompositionServiceTest`의 매핑 케이스를
 * 그대로 승계)만 다룬다.
 */
class LocalCatalogSearchAdapterTest : BehaviorSpec({

    fun mockProduct(
        id: Long,
        name: String,
        price: BigDecimal,
        sellerType: GoodsSellerType,
        createdAt: ZonedDateTime,
    ): Product {
        val product = mockk<Product>(relaxed = true)
        every { product.id } returns id
        every { product.name } returns name
        every { product.price } returns price
        every { product.sellerType } returns sellerType
        every { product.status } returns ProductStatus.ACTIVE
        every { product.createdAt } returns createdAt
        return product
    }

    val now = ZonedDateTime.now()
    val pageable = PageRequest.of(0, 20)

    Given("goods 조회 결과에 일반 상품이 포함된 상황") {
        val product = mockProduct(1L, "러닝화", BigDecimal("50000"), GoodsSellerType.B2C, now)
        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(null, "러닝화", null, null, GoodsSellerType.B2C, pageable)
        } returns PageImpl(listOf(ProductWithStock(product = product, stockQuantity = 10, limitedDropId = null)))

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = goodsDomainService,
            programDomainService = mockk(),
            recruitmentDomainService = mockk(),
            ticketingDomainService = mockk(),
        )

        When("searchGoods를 호출하면") {
            val items = adapter.searchGoods("러닝화", SellerType.B2C, pageable)

            Then("PRODUCT 타입 CatalogItem으로 매핑되고 sellerType이 edge 타입으로 변환된다") {
                items.single().itemType shouldBe CatalogItemType.PRODUCT
                items.single().sourceId shouldBe 1L
                items.single().sellerType shouldBe SellerType.B2C
                items.single().status shouldBe "ACTIVE"
                verify(exactly = 1) { goodsDomainService.search(null, "러닝화", null, null, GoodsSellerType.B2C, pageable) }
            }
        }
    }

    Given("goods 조회 결과에 판매중인 한정판 회차가 연결된 상품이 포함된 상황") {
        val limitedDropProduct = mockProduct(60L, "한정판 스니커즈", BigDecimal("150000"), GoodsSellerType.B2C, now)
        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(any(), any(), any(), any(), any(), any())
        } returns PageImpl(
            listOf(
                ProductWithStock(
                    product = limitedDropProduct,
                    stockQuantity = 2,
                    limitedDropId = 777L,
                    limitedDropStatus = LimitedDropStatus.OPEN,
                ),
            ),
        )

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = goodsDomainService,
            programDomainService = mockk(),
            recruitmentDomainService = mockk(),
            ticketingDomainService = mockk(),
        )

        When("searchGoods를 호출하면") {
            val items = adapter.searchGoods(null, null, pageable)

            Then("limitedDropId를 sourceId로 하는 LIMITED_DROP 항목으로 분기되고 status가 OPEN으로 노출된다") {
                val item = items.single()
                item.itemType shouldBe CatalogItemType.LIMITED_DROP
                item.sourceId shouldBe 777L
                item.detailPath shouldBe "/limited-drops/777"
                item.status shouldBe "OPEN"
            }
        }
    }

    Given("goods 조회 결과에 품절(SOLD_OUT)된 한정판 회차가 연결된 상품이 포함된 상황") {
        val soldOutProduct = mockProduct(61L, "품절 한정판", BigDecimal("200000"), GoodsSellerType.B2C, now)
        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(any(), any(), any(), any(), any(), any())
        } returns PageImpl(
            listOf(
                ProductWithStock(
                    product = soldOutProduct,
                    stockQuantity = 0,
                    limitedDropId = 888L,
                    limitedDropStatus = LimitedDropStatus.SOLD_OUT,
                ),
            ),
        )

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = goodsDomainService,
            programDomainService = mockk(),
            recruitmentDomainService = mockk(),
            ticketingDomainService = mockk(),
        )

        When("searchGoods를 호출하면") {
            val items = adapter.searchGoods(null, null, pageable)

            Then("품절된 한정판의 status가 SOLD_OUT으로 노출되어 ACTIVE와 구분된다") {
                items.single().status shouldBe "SOLD_OUT"
            }
        }
    }

    Given("facility 조회 결과에 프로그램이 포함된 상황") {
        val program = mockk<Program>(relaxed = true)
        every { program.id } returns 3L
        every { program.name } returns "요가 클래스"
        every { program.price } returns BigDecimal("30000")
        every { program.createdAt } returns now

        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog("요가", pageable) } returns PageImpl(listOf(program))

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = mockk(),
            programDomainService = programDomainService,
            recruitmentDomainService = mockk(),
            ticketingDomainService = mockk(),
        )

        When("searchPrograms를 호출하면") {
            val items = adapter.searchPrograms("요가", pageable)

            Then("PROGRAM 타입 CatalogItem으로 매핑되고 status는 항상 ACTIVE다") {
                val item = items.single()
                item.itemType shouldBe CatalogItemType.PROGRAM
                item.sourceId shouldBe 3L
                item.status shouldBe "ACTIVE"
                item.detailPath shouldBe "/programs/3"
            }
        }
    }

    Given("recruitment 조회 결과에 모집글이 포함된 상황") {
        val recruitment = mockk<Recruitment>(relaxed = true)
        every { recruitment.id } returns 4L
        every { recruitment.title } returns "주말 등산 모집"
        every { recruitment.feeAmount } returns BigDecimal("10000")
        every { recruitment.status } returns RecruitmentStatus.OPEN
        every { recruitment.createdAt } returns now

        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(null, pageable) } returns PageImpl(listOf(recruitment))

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = mockk(),
            programDomainService = mockk(),
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = mockk(),
        )

        When("searchRecruitments를 호출하면") {
            val items = adapter.searchRecruitments(null, pageable)

            Then("RECRUITMENT 타입 CatalogItem으로 매핑되고 status는 도메인 enum name 그대로다") {
                val item = items.single()
                item.itemType shouldBe CatalogItemType.RECRUITMENT
                item.sourceId shouldBe 4L
                item.status shouldBe "OPEN"
            }
        }
    }

    Given("ticketing 조회 결과에 좌석가가 다른 경기가 포함된 상황") {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns 5L
        every { event.title } returns "농구 결승전"
        every { event.status } returns EventStatus.OPEN
        every { event.createdAt } returns now

        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEventsForCatalog(null, pageable) } returns
            PageImpl(listOf(EventWithMinSeatPrice(event, BigDecimal("44000"))))

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = mockk(),
            programDomainService = mockk(),
            recruitmentDomainService = mockk(),
            ticketingDomainService = ticketingDomainService,
        )

        When("searchTicketingEvents를 호출하면") {
            val items = adapter.searchTicketingEvents(null, pageable)

            Then("TICKET 타입 CatalogItem으로 매핑되고 price는 최저 좌석가다") {
                val item = items.single()
                item.itemType shouldBe CatalogItemType.TICKET
                item.sourceId shouldBe 5L
                item.price shouldBe BigDecimal("44000")
            }
        }
    }

    Given("좌석이 아직 등록되지 않은 경기가 조회되는 상황") {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns 8L
        every { event.title } returns "좌석 미등록 경기"
        every { event.status } returns EventStatus.OPEN
        every { event.createdAt } returns now

        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEventsForCatalog(null, pageable) } returns
            PageImpl(listOf(EventWithMinSeatPrice(event, null)))

        val adapter = LocalCatalogSearchAdapter(
            goodsDomainService = mockk(),
            programDomainService = mockk(),
            recruitmentDomainService = mockk(),
            ticketingDomainService = ticketingDomainService,
        )

        When("searchTicketingEvents를 호출하면") {
            val items = adapter.searchTicketingEvents(null, pageable)

            Then("대표가 없이(null) 항목만 노출된다") {
                items.single().price shouldBe null
            }
        }
    }
})
