package com.sportsapp.presentation.virtualqueue.interceptor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.application.goods.dto.LimitedDropPurchaseResult
import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.application.goods.usecase.CreateLimitedDropUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropStatsUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropUseCase
import com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase
import com.sportsapp.application.ticketing.dto.SelectSeatsResponse
import com.sportsapp.application.ticketing.usecase.GetEventUseCase
import com.sportsapp.application.ticketing.usecase.ListEventsUseCase
import com.sportsapp.application.ticketing.usecase.ReleaseSeatsUseCase
import com.sportsapp.application.ticketing.usecase.SelectSeatsUseCase
import com.sportsapp.domain.common.EntryTokenGuard
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.goods.controller.LimitedDropApiController
import com.sportsapp.presentation.ticketing.controller.EventApiController
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * [EntryTokenGateInterceptor] 게이트 로직 검증 (BE-09, FR-5).
 *
 * 실제 [LimitedDropApiController]·[EventApiController]를 UseCase만 mockk로 대체해 standalone
 * MockMvc에 등록한다(FeatureDemoApiControllerTest 선례) — 신규 `@RestController` 테스트 더블을
 * 만들지 않는다. 테스트 더블에 `@RestController`를 붙이면 `com.sportsapp` 베이스 패키지를
 * 스캔하는 다른 전체 부팅 테스트(`BaseIntegrationTest` 계열)에서 실제 컨트롤러와 라우팅이
 * 충돌한다(MEMORY "동일명 @Component 빈 충돌"과 동일 원인 — 클래스패스 스캔은 소스셋을 구분하지
 * 않는다). 경로 스코핑은 프로덕션과 동일한 path pattern 상수([EntryTokenGateInterceptor
 * .LIMITED_DROP_ORDER_PATH]·[TICKETING_SELECT_SEATS_PATH])로
 * [org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder.addMappedInterceptors]에
 * 등록해 검증한다.
 */
class EntryTokenGateInterceptorTest : BehaviorSpec({

    fun buildMockMvc(
        entryTokenGuard: EntryTokenGuard,
        featureFlagEvaluator: FeatureFlagEvaluator,
        meterRegistry: SimpleMeterRegistry,
        purchaseLimitedDropUseCase: PurchaseLimitedDropUseCase = mockk(),
        getLimitedDropUseCase: GetLimitedDropUseCase = mockk(),
        selectSeatsUseCase: SelectSeatsUseCase = mockk(),
    ): MockMvc {
        val interceptor = EntryTokenGateInterceptor(entryTokenGuard, featureFlagEvaluator, meterRegistry)
        val limitedDropApiController = LimitedDropApiController(
            createLimitedDropUseCase = mockk<CreateLimitedDropUseCase>(),
            getLimitedDropUseCase = getLimitedDropUseCase,
            purchaseLimitedDropUseCase = purchaseLimitedDropUseCase,
            getLimitedDropStatsUseCase = mockk<GetLimitedDropStatsUseCase>(),
        )
        val eventApiController = EventApiController(
            listEventsUseCase = mockk<ListEventsUseCase>(),
            getEventUseCase = mockk<GetEventUseCase>(),
            selectSeatsUseCase = selectSeatsUseCase,
            releaseSeatsUseCase = mockk<ReleaseSeatsUseCase>(),
        )
        val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
        return MockMvcBuilders
            .standaloneSetup(limitedDropApiController, eventApiController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .addMappedInterceptors(
                arrayOf(
                    EntryTokenGateInterceptor.LIMITED_DROP_ORDER_PATH,
                    EntryTokenGateInterceptor.TICKETING_SELECT_SEATS_PATH,
                ),
                interceptor,
            )
            .build()
    }

    Given("플래그 ON + 유효한 입장 토큰") {
        val entryTokenGuard = mockk<EntryTokenGuard>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val meterRegistry = SimpleMeterRegistry()
        val purchaseLimitedDropUseCase = mockk<PurchaseLimitedDropUseCase>()
        val mockMvc = buildMockMvc(
            entryTokenGuard = entryTokenGuard,
            featureFlagEvaluator = featureFlagEvaluator,
            meterRegistry = meterRegistry,
            purchaseLimitedDropUseCase = purchaseLimitedDropUseCase,
        )

        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(1L), false)
        } returns true
        every { entryTokenGuard.verify("limited-drop", 10L, 1L, "valid-token") } returns true
        every { purchaseLimitedDropUseCase.execute(any()) } returns LimitedDropPurchaseResult(
            orderId = 1L,
            dropId = 10L,
            status = GoodsOrderStatus.PENDING,
        )

        When("한정판 구매 요청 시") {
            Then("다운스트림(구매 UseCase)으로 통과한다") {
                mockMvc.perform(
                    post("/limited-drops/10/orders")
                        .header("X-User-Id", 1L)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Entry-Token", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"quantity":1}"""),
                ).andExpect(status().isAccepted)
            }
        }
    }

    Given("플래그 ON + 토큰 없음") {
        val entryTokenGuard = mockk<EntryTokenGuard>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val meterRegistry = SimpleMeterRegistry()
        val mockMvc = buildMockMvc(entryTokenGuard, featureFlagEvaluator, meterRegistry)

        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(2L), false)
        } returns true
        every { entryTokenGuard.verify("limited-drop", 11L, 2L, null) } returns false

        When("X-Entry-Token 헤더 없이 한정판 구매 요청 시") {
            Then("403 QUEUE_BYPASS_DENIED를 반환한다") {
                mockMvc.perform(
                    post("/limited-drops/11/orders")
                        .header("X-User-Id", 2L)
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"quantity":1}"""),
                )
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.properties.code").value("QUEUE_BYPASS_DENIED"))
            }
        }
    }

    Given("플래그 ON + 위조·만료된 토큰") {
        val entryTokenGuard = mockk<EntryTokenGuard>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val meterRegistry = SimpleMeterRegistry()
        val mockMvc = buildMockMvc(entryTokenGuard, featureFlagEvaluator, meterRegistry)

        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(3L), false)
        } returns true
        every { entryTokenGuard.verify("ticketing-event", 20L, 3L, "forged-token") } returns false

        When("좌석 선택 요청 시 위조 토큰이면") {
            Then("403을 반환하고 bypass_attempt 카운터를 증가시킨다") {
                mockMvc.perform(
                    post("/events/20/seats/select")
                        .header("X-User-Id", 3L)
                        .header("X-Entry-Token", "forged-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"seatIds":[100]}"""),
                ).andExpect(status().isForbidden)

                meterRegistry.counter("virtual_queue.bypass_attempt").count() shouldBe 1.0
            }
        }
    }

    Given("플래그 OFF (대기열 우회 허용, FR-9)") {
        val entryTokenGuard = mockk<EntryTokenGuard>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val meterRegistry = SimpleMeterRegistry()
        val purchaseLimitedDropUseCase = mockk<PurchaseLimitedDropUseCase>()
        val mockMvc = buildMockMvc(
            entryTokenGuard = entryTokenGuard,
            featureFlagEvaluator = featureFlagEvaluator,
            meterRegistry = meterRegistry,
            purchaseLimitedDropUseCase = purchaseLimitedDropUseCase,
        )

        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(4L), false)
        } returns false
        every { purchaseLimitedDropUseCase.execute(any()) } returns LimitedDropPurchaseResult(
            orderId = 2L,
            dropId = 12L,
            status = GoodsOrderStatus.PENDING,
        )

        When("토큰 없이 한정판 구매 요청 시") {
            Then("검증을 스킵하고 통과한다(직접 구매 경로)") {
                mockMvc.perform(
                    post("/limited-drops/12/orders")
                        .header("X-User-Id", 4L)
                        .header("Idempotency-Key", "idem-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"quantity":1}"""),
                ).andExpect(status().isAccepted)
            }
        }
    }

    Given("등록되지 않은 경로") {
        val entryTokenGuard = mockk<EntryTokenGuard>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val meterRegistry = SimpleMeterRegistry()
        val getLimitedDropUseCase = mockk<GetLimitedDropUseCase>()
        val mockMvc = buildMockMvc(
            entryTokenGuard = entryTokenGuard,
            featureFlagEvaluator = featureFlagEvaluator,
            meterRegistry = meterRegistry,
            getLimitedDropUseCase = getLimitedDropUseCase,
        )

        every { getLimitedDropUseCase.execute(99L) } returns LimitedDropView(
            dropId = 99L,
            productId = 1L,
            status = LimitedDropStatus.OPEN,
            openAt = ZonedDateTime.now().minusHours(1),
            closeAt = ZonedDateTime.now().plusHours(1),
            remaining = 5,
            perUserLimit = 1,
            totalQuantity = 10,
            price = BigDecimal("10000"),
        )

        When("GET /limited-drops/{dropId} 요청 시 (등록 대상 두 경로 밖)") {
            Then("인터셉터를 거치지 않고 통과한다") {
                mockMvc.perform(get("/limited-drops/99")).andExpect(status().isOk)
            }
        }
    }
})
