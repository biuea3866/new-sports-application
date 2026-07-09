package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogItemType
import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.service.ProgramDomainService
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

class CatalogCompositionServiceTest : BehaviorSpec({

    fun testExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 50
            setThreadNamePrefix("catalog-search-test-")
            initialize()
        }

    fun mockProduct(
        id: Long,
        name: String,
        price: BigDecimal,
        sellerType: SellerType,
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

    fun mockProgram(id: Long, name: String, price: BigDecimal, createdAt: ZonedDateTime): Program {
        val program = mockk<Program>(relaxed = true)
        every { program.id } returns id
        every { program.name } returns name
        every { program.price } returns price
        every { program.createdAt } returns createdAt
        return program
    }

    fun mockRecruitment(id: Long, title: String, feeAmount: BigDecimal, createdAt: ZonedDateTime): Recruitment {
        val recruitment = mockk<Recruitment>(relaxed = true)
        every { recruitment.id } returns id
        every { recruitment.title } returns title
        every { recruitment.feeAmount } returns feeAmount
        every { recruitment.status } returns RecruitmentStatus.OPEN
        every { recruitment.createdAt } returns createdAt
        return recruitment
    }

    fun mockEvent(id: Long, title: String, createdAt: ZonedDateTime): Event {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns id
        every { event.title } returns title
        every { event.status } returns EventStatus.OPEN
        every { event.createdAt } returns createdAt
        return event
    }

    fun <T> emptyPage(): Page<T> = PageImpl(emptyList(), Pageable.unpaged(), 0)

    fun buildService(
        goodsDomainService: GoodsDomainService = mockk(),
        programDomainService: ProgramDomainService = mockk(),
        recruitmentDomainService: RecruitmentDomainService = mockk(),
        ticketingDomainService: TicketingDomainService = mockk(),
        executor: ThreadPoolTaskExecutor = testExecutor(),
    ) = CatalogCompositionService(
        goodsDomainService = goodsDomainService,
        programDomainService = programDomainService,
        recruitmentDomainService = recruitmentDomainService,
        ticketingDomainService = ticketingDomainService,
        catalogSearchExecutor = executor,
    )

    val now = ZonedDateTime.now()

    Given("5개 도메인(4개 조회 호출) 모두 판매 대상을 보유한 상황") {
        val product = mockProduct(1L, "러닝화", BigDecimal("50000"), SellerType.B2C, now.minusMinutes(1))
        val limitedDropProduct = mockProduct(2L, "한정판 저지", BigDecimal("120000"), SellerType.B2B, now.minusMinutes(2))
        val program = mockProgram(3L, "요가 클래스", BigDecimal("30000"), now.minusMinutes(3))
        val recruitment = mockRecruitment(4L, "주말 등산 모집", BigDecimal("10000"), now.minusMinutes(4))
        val event = mockEvent(5L, "농구 결승전", now.minusMinutes(5))

        val goodsDomainService = mockk<GoodsDomainService>()
        every { goodsDomainService.search(any(), any(), any(), any(), any(), any()) } returns PageImpl(
            listOf(
                ProductWithStock(product = product, stockQuantity = 10, limitedDropId = null),
                ProductWithStock(product = limitedDropProduct, stockQuantity = 3, limitedDropId = 99L),
            ),
        )
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } returns PageImpl(listOf(program))
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(any(), any()) } returns PageImpl(listOf(recruitment))
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents(any(), any()) } returns PageImpl(listOf(event))

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("전체 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("5개 유형이 모두 포함된 단일 응답으로 조합되고 createdAt 내림차순으로 정렬된다") {
                response.items shouldContainExactlyInAnyOrderTypes listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.LIMITED_DROP,
                    CatalogItemType.PROGRAM,
                    CatalogItemType.RECRUITMENT,
                    CatalogItemType.TICKET,
                )
                // limitedDropProduct(id=2L)는 limitedDropId=99L을 sourceId로 노출한다 (LIMITED_DROP 분기)
                response.items.map { it.sourceId } shouldBe listOf(1L, 99L, 3L, 4L, 5L)
                response.failedDomains.shouldBeEmpty()
            }
        }
    }

    Given("\"요가\" 키워드로 검색하는 상황") {
        val b2cYoga = mockProduct(10L, "요가매트", BigDecimal("20000"), SellerType.B2C, now.minusMinutes(1))
        val b2bYoga = mockProduct(11L, "브랜드 요가복", BigDecimal("80000"), SellerType.B2B, now.minusMinutes(2))
        val yogaProgram = mockProgram(12L, "성인 요가 클래스", BigDecimal("30000"), now.minusMinutes(3))
        val yogaRecruitment = mockRecruitment(13L, "요가 동호회 모집", BigDecimal.ZERO, now.minusMinutes(4))

        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(null, "요가", null, null, null, any())
        } returns PageImpl(
            listOf(
                ProductWithStock(product = b2cYoga, stockQuantity = 5, limitedDropId = null),
                ProductWithStock(product = b2bYoga, stockQuantity = 5, limitedDropId = null),
            ),
        )
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog("요가", any()) } returns PageImpl(listOf(yogaProgram))
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments("요가", any()) } returns PageImpl(listOf(yogaRecruitment))
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents("요가", any()) } returns emptyPage()

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("keyword=요가로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = "요가", itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("Product(B2C/B2B)·Program·Recruitment가 혼합 반환된다") {
                response.items.map { it.itemType } shouldContainExactlyInAnyOrder listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.PRODUCT,
                    CatalogItemType.PROGRAM,
                    CatalogItemType.RECRUITMENT,
                )
            }

            Then("PRODUCT 항목이 sellerType을 노출한다") {
                val productItems = response.items.filter { it.itemType == CatalogItemType.PRODUCT }
                productItems.map { it.sellerType } shouldContainExactlyInAnyOrder listOf(SellerType.B2C, SellerType.B2B)
            }
        }
    }

    Given("sellerType=B2B로 필터링하는 상황") {
        val b2bProduct = mockProduct(20L, "브랜드 저지", BigDecimal("90000"), SellerType.B2B, now)
        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(null, null, null, null, SellerType.B2B, any())
        } returns PageImpl(listOf(ProductWithStock(product = b2bProduct, stockQuantity = 1, limitedDropId = null)))
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } returns emptyPage()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(any(), any()) } returns emptyPage()
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents(any(), any()) } returns emptyPage()

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("sellerType=B2B로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = SellerType.B2B, page = 0, size = 20),
            )

            Then("goods 도메인에 sellerType=B2B가 전달되고 브랜드 상품만 반환된다") {
                verify(exactly = 1) { goodsDomainService.search(null, null, null, null, SellerType.B2B, any()) }
                response.items shouldBeSingleSourceId 20L
            }
        }
    }

    Given("itemType=RECRUITMENT로 필터링하는 상황") {
        val recruitment = mockRecruitment(30L, "주말 축구 모집", BigDecimal("5000"), now)
        val goodsDomainService = mockk<GoodsDomainService>()
        val programDomainService = mockk<ProgramDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(any(), any()) } returns PageImpl(listOf(recruitment))

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("itemType=RECRUITMENT로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.RECRUITMENT, sellerType = null, page = 0, size = 20),
            )

            Then("recruitment 도메인만 조회되고 모집만 반환된다") {
                response.items.map { it.itemType } shouldBe listOf(CatalogItemType.RECRUITMENT)
                verify(exactly = 0) { goodsDomainService.search(any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { programDomainService.searchForCatalog(any(), any()) }
                verify(exactly = 0) { ticketingDomainService.searchOpenEvents(any(), any()) }
            }
        }
    }

    Given("1개 도메인(facility) 조회가 300ms를 초과해 지연되는 상황") {
        val product = mockProduct(40L, "축구화", BigDecimal("60000"), SellerType.B2C, now)
        val recruitment = mockRecruitment(41L, "풋살 모집", BigDecimal("3000"), now.minusSeconds(1))
        val event = mockEvent(42L, "야구 개막전", now.minusSeconds(2))

        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(any(), any(), any(), any(), any(), any())
        } returns PageImpl(listOf(ProductWithStock(product = product, stockQuantity = 1, limitedDropId = null)))
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } answers {
            Thread.sleep(500)
            emptyPage()
        }
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(any(), any()) } returns PageImpl(listOf(recruitment))
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents(any(), any()) } returns PageImpl(listOf(event))

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("전체 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("나머지 3개 도메인 결과는 반환되고 facility는 failedDomains에 표기된다") {
                response.items.map { it.itemType } shouldContainExactlyInAnyOrder listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.RECRUITMENT,
                    CatalogItemType.TICKET,
                )
                response.failedDomains shouldBe listOf("facility")
            }
        }
    }

    Given("5개 도메인 어디에도 검색 결과가 없는 상황") {
        val goodsDomainService = mockk<GoodsDomainService>()
        every { goodsDomainService.search(any(), any(), any(), any(), any(), any()) } returns emptyPage()
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } returns emptyPage()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.searchOpenRecruitments(any(), any()) } returns emptyPage()
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents(any(), any()) } returns emptyPage()

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("keyword=클라이밍으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = "클라이밍", itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("빈 items를 반환한다") {
                response.items.shouldBeEmpty()
                response.failedDomains.shouldBeEmpty()
            }
        }
    }

    Given("TICKET 유형 검색 결과가 있는 상황") {
        val event = mockEvent(50L, "배구 준결승", now)
        val goodsDomainService = mockk<GoodsDomainService>()
        val programDomainService = mockk<ProgramDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.searchOpenEvents(any(), any()) } returns PageImpl(listOf(event))

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("itemType=TICKET으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.TICKET, sellerType = null, page = 0, size = 20),
            )

            Then("TICKET 항목의 price는 null이다") {
                response.items.single().price shouldBe null
            }
        }
    }

    Given("goods 조회 결과에 한정판 회차가 연결된 상품이 포함된 상황") {
        val limitedDropProduct = mockProduct(60L, "한정판 스니커즈", BigDecimal("150000"), SellerType.B2C, now)
        val goodsDomainService = mockk<GoodsDomainService>()
        every {
            goodsDomainService.search(any(), any(), any(), any(), any(), any())
        } returns PageImpl(listOf(ProductWithStock(product = limitedDropProduct, stockQuantity = 2, limitedDropId = 777L)))
        val programDomainService = mockk<ProgramDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()

        val service = buildService(
            goodsDomainService = goodsDomainService,
            programDomainService = programDomainService,
            recruitmentDomainService = recruitmentDomainService,
            ticketingDomainService = ticketingDomainService,
        )

        When("itemType=LIMITED_DROP으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.LIMITED_DROP, sellerType = null, page = 0, size = 20),
            )

            Then("limitedDropId를 sourceId로 하는 LIMITED_DROP 항목으로 분기된다") {
                val item = response.items.single()
                item.itemType shouldBe CatalogItemType.LIMITED_DROP
                item.sourceId shouldBe 777L
                item.detailPath shouldBe "/limited-drops/777"
            }
        }
    }
})

private infix fun List<com.sportsapp.application.catalog.dto.CatalogItem>.shouldContainExactlyInAnyOrderTypes(
    types: List<CatalogItemType>,
) {
    this.map { it.itemType } shouldContainExactlyInAnyOrder types
}

private infix fun List<com.sportsapp.application.catalog.dto.CatalogItem>.shouldBeSingleSourceId(sourceId: Long) {
    this.map { it.sourceId } shouldBe listOf(sourceId)
}
