package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogCriteria
import com.sportsapp.domain.ticketing.dto.EventWithMinSeatPrice
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private val CREATED_AT: ZonedDateTime = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
private val STARTS_AT: ZonedDateTime = ZonedDateTime.of(2026, 7, 20, 19, 0, 0, 0, ZoneOffset.UTC)

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchTicketingEvents` 원격 구현(2단계) 공급자 (S2-03).
 *
 * 경기는 좌석마다 가격이 달라 **최저 좌석가**를 대표가로 노출하고(좌석 미등록 경기는 null),
 * 경기장명·시작 일시는 같은 제목의 경기를 구분하는 **실데이터**라 공급자가 채운다.
 */
class SearchTicketingCatalogUseCaseTest : BehaviorSpec({

    fun event(id: Long, title: String, venue: String): Event = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.title } returns title
        every { this@mockk.venue } returns venue
        every { startsAt } returns STARTS_AT
        every { status } returns EventStatus.OPEN
        every { createdAt } returns CREATED_AT
    }

    Given("좌석가가 등록된 경기와 미등록 경기가 섞인 검색 결과") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { ticketingDomainService.searchOpenEventsForCatalog("결승", pageable) } returns PageImpl(
            listOf(
                EventWithMinSeatPrice(event = event(5L, "농구 결승전", "잠실 실내체육관"), minSeatPrice = BigDecimal("44000")),
                EventWithMinSeatPrice(event = event(6L, "배구 결승전", "장충체육관"), minSeatPrice = null),
            ),
            pageable,
            2,
        )
        val useCase = SearchTicketingCatalogUseCase(ticketingDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(InternalTicketingCatalogCriteria(keyword = "결승", page = 0, size = 20))

            Then("최저 좌석가를 대표가로 실어 보낸다") {
                result[0].sourceId shouldBe 5L
                result[0].price shouldBe BigDecimal("44000")
            }

            Then("좌석 미등록 경기는 가격을 null 로 보낸다 (0 으로 방어하지 않는다)") {
                result[1].sourceId shouldBe 6L
                result[1].price shouldBe null
            }

            Then("경기장명·시작 일시를 함께 공급한다 — 같은 제목의 경기를 구분하는 근거다") {
                result[0].locationName shouldBe "잠실 실내체육관"
                result[0].scheduledAt shouldBe STARTS_AT
            }

            Then("제목·상태·생성 시각은 경기 자기 데이터로 채운다") {
                result[0].title shouldBe "농구 결승전"
                result[0].status shouldBe EventStatus.OPEN
                result[0].createdAt shouldBe CREATED_AT
            }

            Then("공급자는 itemType·sellerType·detailPath 를 만들지 않는다 (파사드 책임)") {
                val fieldNames = result[0]::class.members.map { it.name }
                fieldNames.contains("itemType") shouldBe false
                fieldNames.contains("sellerType") shouldBe false
                fieldNames.contains("detailPath") shouldBe false
            }
        }
    }

    Given("검색 결과가 0건일 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { ticketingDomainService.searchOpenEventsForCatalog(null, pageable) } returns
            PageImpl(emptyList(), pageable, 0)
        val useCase = SearchTicketingCatalogUseCase(ticketingDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(InternalTicketingCatalogCriteria(keyword = null, page = 0, size = 20))

            Then("빈 목록을 정상 반환하고 도메인 페이징만 위임한다") {
                result.shouldBeEmpty()
                verify(exactly = 1) { ticketingDomainService.searchOpenEventsForCatalog(null, pageable) }
            }
        }
    }
})
