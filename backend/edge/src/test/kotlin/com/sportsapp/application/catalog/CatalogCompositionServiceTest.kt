package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.domain.catalog.dto.CatalogItem
import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.gateway.CatalogSearchGateway
import com.sportsapp.domain.catalog.vo.SellerType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * S2-01 — [CatalogCompositionService]는 이제 4개 코어 DomainService가 아니라 edge 소유
 * [CatalogSearchGateway] 하나만 주입받는다. fan-out·타임아웃·부분 저하·페이지네이션 로직은
 * 이동 전과 동일해야 하므로(동작 변화 0), 여기서는 Gateway를 mock으로 대체해 같은 시나리오를
 * 검증한다. 타 모듈 Entity → CatalogItem 매핑 자체는 bootstrap의
 * `LocalCatalogSearchAdapterTest`가 검증한다.
 */
class CatalogCompositionServiceTest : BehaviorSpec({

    fun testExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 50
            setThreadNamePrefix("catalog-search-test-")
            initialize()
        }

    fun catalogItem(
        itemType: CatalogItemType,
        sourceId: Long,
        title: String,
        price: BigDecimal?,
        sellerType: SellerType?,
        createdAt: ZonedDateTime,
        locationName: String? = null,
        scheduledAt: ZonedDateTime? = null,
    ) = CatalogItem(
        itemType = itemType,
        sourceId = sourceId,
        title = title,
        price = price,
        sellerType = sellerType,
        status = "ACTIVE",
        detailPath = "/items/$sourceId",
        createdAt = createdAt,
        locationName = locationName,
        scheduledAt = scheduledAt,
    )

    fun buildService(
        catalogSearchGateway: CatalogSearchGateway = mockk(),
        executor: AsyncTaskExecutor = testExecutor(),
    ) = CatalogCompositionService(
        catalogSearchGateway = catalogSearchGateway,
        catalogSearchExecutor = executor,
    )

    val now = ZonedDateTime.now()

    Given("5개 도메인(4개 조회 호출) 모두 판매 대상을 보유한 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchGoods(any(), any(), any()) } returns listOf(
            catalogItem(CatalogItemType.PRODUCT, 1L, "러닝화", BigDecimal("50000"), SellerType.B2C, now.minusMinutes(1)),
            catalogItem(CatalogItemType.LIMITED_DROP, 99L, "한정판 저지", BigDecimal("120000"), SellerType.B2B, now.minusMinutes(2)),
        )
        every { gateway.searchPrograms(any(), any()) } returns listOf(
            catalogItem(CatalogItemType.PROGRAM, 3L, "요가 클래스", BigDecimal("30000"), null, now.minusMinutes(3)),
        )
        every { gateway.searchRecruitments(any(), any()) } returns listOf(
            catalogItem(CatalogItemType.RECRUITMENT, 4L, "주말 등산 모집", BigDecimal("10000"), null, now.minusMinutes(4)),
        )
        every { gateway.searchTicketingEvents(any(), any()) } returns listOf(
            catalogItem(CatalogItemType.TICKET, 5L, "농구 결승전", BigDecimal("44000"), null, now.minusMinutes(5)),
        )

        val service = buildService(catalogSearchGateway = gateway)

        When("전체 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("5개 유형이 모두 포함된 단일 응답으로 조합되고 createdAt 내림차순으로 정렬된다") {
                response.items.map { it.itemType } shouldContainExactlyInAnyOrder listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.LIMITED_DROP,
                    CatalogItemType.PROGRAM,
                    CatalogItemType.RECRUITMENT,
                    CatalogItemType.TICKET,
                )
                response.items.map { it.sourceId } shouldBe listOf(1L, 99L, 3L, 4L, 5L)
                response.failedDomains.shouldBeEmpty()
            }
        }
    }

    Given("\"요가\" 키워드로 검색하는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchGoods(null, null, any()) } returns emptyList()
        every { gateway.searchGoods("요가", null, any()) } returns listOf(
            catalogItem(CatalogItemType.PRODUCT, 10L, "요가매트", BigDecimal("20000"), SellerType.B2C, now.minusMinutes(1)),
            catalogItem(CatalogItemType.PRODUCT, 11L, "브랜드 요가복", BigDecimal("80000"), SellerType.B2B, now.minusMinutes(2)),
        )
        every { gateway.searchPrograms("요가", any()) } returns listOf(
            catalogItem(CatalogItemType.PROGRAM, 12L, "성인 요가 클래스", BigDecimal("30000"), null, now.minusMinutes(3)),
        )
        every { gateway.searchRecruitments("요가", any()) } returns listOf(
            catalogItem(CatalogItemType.RECRUITMENT, 13L, "요가 동호회 모집", BigDecimal.ZERO, null, now.minusMinutes(4)),
        )
        every { gateway.searchTicketingEvents("요가", any()) } returns emptyList()

        val service = buildService(catalogSearchGateway = gateway)

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
        val gateway = mockk<CatalogSearchGateway>()
        every {
            gateway.searchGoods(null, SellerType.B2B, any())
        } returns listOf(catalogItem(CatalogItemType.PRODUCT, 20L, "브랜드 저지", BigDecimal("90000"), SellerType.B2B, now))
        every { gateway.searchPrograms(any(), any()) } returns emptyList()
        every { gateway.searchRecruitments(any(), any()) } returns emptyList()
        every { gateway.searchTicketingEvents(any(), any()) } returns emptyList()

        val service = buildService(catalogSearchGateway = gateway)

        When("sellerType=B2B로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = SellerType.B2B, page = 0, size = 20),
            )

            Then("goods 게이트웨이에 sellerType=B2B가 전달되고 브랜드 상품만 반환된다") {
                verify(exactly = 1) { gateway.searchGoods(null, SellerType.B2B, any()) }
                response.items.map { it.sourceId } shouldBe listOf(20L)
            }
        }
    }

    Given("itemType=RECRUITMENT로 필터링하는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchRecruitments(any(), any()) } returns
            listOf(catalogItem(CatalogItemType.RECRUITMENT, 30L, "주말 축구 모집", BigDecimal("5000"), null, now))

        val service = buildService(catalogSearchGateway = gateway)

        When("itemType=RECRUITMENT로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.RECRUITMENT, sellerType = null, page = 0, size = 20),
            )

            Then("recruitment 게이트웨이만 호출되고 모집만 반환된다") {
                response.items.map { it.itemType } shouldBe listOf(CatalogItemType.RECRUITMENT)
                verify(exactly = 0) { gateway.searchGoods(any(), any(), any()) }
                verify(exactly = 0) { gateway.searchPrograms(any(), any()) }
                verify(exactly = 0) { gateway.searchTicketingEvents(any(), any()) }
            }
        }
    }

    Given("1개 도메인(facility) 조회가 300ms를 초과해 지연되는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every {
            gateway.searchGoods(any(), any(), any())
        } returns listOf(catalogItem(CatalogItemType.PRODUCT, 40L, "축구화", BigDecimal("60000"), SellerType.B2C, now))
        every { gateway.searchPrograms(any(), any()) } answers {
            Thread.sleep(500)
            emptyList()
        }
        every { gateway.searchRecruitments(any(), any()) } returns
            listOf(catalogItem(CatalogItemType.RECRUITMENT, 41L, "풋살 모집", BigDecimal("3000"), null, now.minusSeconds(1)))
        every { gateway.searchTicketingEvents(any(), any()) } returns
            listOf(catalogItem(CatalogItemType.TICKET, 42L, "야구 개막전", BigDecimal("44000"), null, now.minusSeconds(2)))

        val service = buildService(catalogSearchGateway = gateway)

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
                response.failedDomains shouldBe listOf(CatalogItemType.PROGRAM)
            }
        }
    }

    Given("5개 도메인 어디에도 검색 결과가 없는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchGoods(any(), any(), any()) } returns emptyList()
        every { gateway.searchPrograms(any(), any()) } returns emptyList()
        every { gateway.searchRecruitments(any(), any()) } returns emptyList()
        every { gateway.searchTicketingEvents(any(), any()) } returns emptyList()

        val service = buildService(catalogSearchGateway = gateway)

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
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchTicketingEvents(any(), any()) } returns
            listOf(catalogItem(CatalogItemType.TICKET, 50L, "배구 준결승", BigDecimal("44000"), null, now))

        val service = buildService(catalogSearchGateway = gateway)

        When("itemType=TICKET으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.TICKET, sellerType = null, page = 0, size = 20),
            )

            Then("게이트웨이가 매핑한 price가 그대로 응답에 실린다") {
                response.items.single().price shouldBe BigDecimal("44000")
            }
        }
    }

    Given("itemType=LIMITED_DROP으로 필터링하는 상황 (goods가 PRODUCT·LIMITED_DROP을 함께 반환)") {
        val gateway = mockk<CatalogSearchGateway>()
        every { gateway.searchGoods(any(), any(), any()) } returns listOf(
            catalogItem(CatalogItemType.PRODUCT, 60L, "일반 상품", BigDecimal("10000"), SellerType.B2C, now),
            catalogItem(CatalogItemType.LIMITED_DROP, 777L, "한정판 스니커즈", BigDecimal("150000"), SellerType.B2C, now),
        )

        val service = buildService(catalogSearchGateway = gateway)

        When("itemType=LIMITED_DROP으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.LIMITED_DROP, sellerType = null, page = 0, size = 20),
            )

            Then("게이트웨이가 함께 반환한 PRODUCT는 걸러지고 LIMITED_DROP만 남는다") {
                val item = response.items.single()
                item.itemType shouldBe CatalogItemType.LIMITED_DROP
                item.sourceId shouldBe 777L
            }
        }
    }

    Given("itemType 필터 없이 전체 검색 중 goods 도메인이 300ms를 초과해 지연되는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every {
            gateway.searchGoods(any(), any(), any())
        } answers {
            Thread.sleep(500)
            emptyList()
        }
        every { gateway.searchPrograms(any(), any()) } returns
            listOf(catalogItem(CatalogItemType.PROGRAM, 70L, "필라테스 클래스", BigDecimal("40000"), null, now))
        every { gateway.searchRecruitments(any(), any()) } returns emptyList()
        every { gateway.searchTicketingEvents(any(), any()) } returns emptyList()

        val service = buildService(catalogSearchGateway = gateway)

        When("전체 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("goods가 커버하는 PRODUCT·LIMITED_DROP 둘 다 failedDomains에 포함된다") {
                response.failedDomains shouldContainExactlyInAnyOrder listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.LIMITED_DROP,
                )
            }
        }
    }

    Given("itemType=PRODUCT로 필터링한 검색 중 goods 도메인이 300ms를 초과해 지연되는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every {
            gateway.searchGoods(any(), any(), any())
        } answers {
            Thread.sleep(500)
            emptyList()
        }

        val service = buildService(catalogSearchGateway = gateway)

        When("itemType=PRODUCT로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.PRODUCT, sellerType = null, page = 0, size = 20),
            )

            Then("failedDomains에는 요청된 PRODUCT만 포함되고 LIMITED_DROP은 포함되지 않는다") {
                response.failedDomains shouldBe listOf(CatalogItemType.PRODUCT)
            }
        }
    }

    Given("itemType=LIMITED_DROP으로 필터링한 검색 중 goods 도메인이 300ms를 초과해 지연되는 상황") {
        val gateway = mockk<CatalogSearchGateway>()
        every {
            gateway.searchGoods(any(), any(), any())
        } answers {
            Thread.sleep(500)
            emptyList()
        }

        val service = buildService(catalogSearchGateway = gateway)

        When("itemType=LIMITED_DROP으로 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = CatalogItemType.LIMITED_DROP, sellerType = null, page = 0, size = 20),
            )

            Then("failedDomains에는 요청된 LIMITED_DROP만 포함되고 PRODUCT는 포함되지 않는다") {
                response.failedDomains shouldBe listOf(CatalogItemType.LIMITED_DROP)
            }
        }
    }

    Given("bounded executor가 포화되어 4개 도메인 submit이 모두 즉시 거부되는 상황") {
        val rejectingExecutor = mockk<AsyncTaskExecutor>()
        every {
            rejectingExecutor.submit(any<Callable<*>>())
        } throws RejectedExecutionException("catalog-search-executor saturated")

        val service = buildService(executor = rejectingExecutor)

        When("전체 검색을 실행하면") {
            val response = service.search(
                CatalogSearchCriteria(keyword = null, itemType = null, sellerType = null, page = 0, size = 20),
            )

            Then("RejectedExecutionException을 전파하지 않고 5개 유형 전부 failedDomains로 처리된다") {
                response.items.shouldBeEmpty()
                response.failedDomains shouldContainExactlyInAnyOrder listOf(
                    CatalogItemType.PRODUCT,
                    CatalogItemType.LIMITED_DROP,
                    CatalogItemType.PROGRAM,
                    CatalogItemType.RECRUITMENT,
                    CatalogItemType.TICKET,
                )
            }
        }
    }
})
