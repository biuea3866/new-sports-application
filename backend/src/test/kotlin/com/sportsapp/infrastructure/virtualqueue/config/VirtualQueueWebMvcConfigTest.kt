package com.sportsapp.infrastructure.virtualqueue.config

import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.application.goods.usecase.CreateLimitedDropUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropStatsUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropUseCase
import com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase
import com.sportsapp.application.ticketing.usecase.GetEventUseCase
import com.sportsapp.application.ticketing.usecase.ListEventsUseCase
import com.sportsapp.application.ticketing.usecase.ReleaseSeatsUseCase
import com.sportsapp.application.ticketing.usecase.SelectSeatsUseCase
import com.sportsapp.domain.common.EntryTokenGuard
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.goods.controller.LimitedDropApiController
import com.sportsapp.presentation.ticketing.controller.EventApiController
import com.sportsapp.presentation.virtualqueue.interceptor.EntryTokenGateInterceptor
import io.kotest.core.spec.style.BehaviorSpec
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.context.annotation.Bean
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * 플래그 ON + 토큰 항상 거부(entryTokenGuard.verify → false)로 세팅해, [VirtualQueueWebMvcConfig]가
 * 등록한 두 경로에서 게이트가 실제로 실행됐는지(403)를 "등록됨"의 증거로 삼는다.
 *
 * 실제 [LimitedDropApiController]·[EventApiController]를 그대로 빈으로 등록한다 — 신규
 * `@RestController` 테스트 더블은 `com.sportsapp` 베이스 패키지 컴포넌트 스캔과 충돌해 다른
 * 전체 부팅 테스트를 깨뜨린다(`EntryTokenGateInterceptorTest` 파일 상단 문서 참조).
 *
 * **`@Configuration` 미부착 — 의도적.** `@Configuration`은 `@Component`를 메타 애노테이션으로
 * 갖고 있어 실제 앱의 `@ComponentScan`(`com.sportsapp` 베이스 패키지, 테스트 소스도 동일
 * 클래스패스라 스캔 대상)에 이 클래스가 그대로 걸려 mockk 빈(`featureFlagEvaluator`·
 * `limitedDropApiController` 등)이 실제 앱 컨텍스트에 흘러 들어간다(재현: `LimitedDropApiControllerTest`
 * 전체 부팅에서 `CreateLimitedDropUseCase`가 mockk로 대체돼 500 회귀). `@Bean` 메서드만으로도
 * `AnnotationConfigWebApplicationContext.register(...)`의 명시적 등록(lite 모드)으로 처리된다 —
 * 컴포넌트 스캔 발견 대상에서는 제외된다.
 */
@EnableWebMvc
private class RoutingTestContextConfig {

    @Bean
    fun entryTokenGuard(): EntryTokenGuard = mockk<EntryTokenGuard> {
        every { verify(any(), any(), any(), any()) } returns false
    }

    @Bean
    fun featureFlagEvaluator(): FeatureFlagEvaluator = mockk<FeatureFlagEvaluator> {
        every { isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, any(), false) } returns true
    }

    @Bean
    fun meterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()

    @Bean
    fun entryTokenGateInterceptor(
        entryTokenGuard: EntryTokenGuard,
        featureFlagEvaluator: FeatureFlagEvaluator,
        meterRegistry: SimpleMeterRegistry,
    ): EntryTokenGateInterceptor = EntryTokenGateInterceptor(entryTokenGuard, featureFlagEvaluator, meterRegistry)

    @Bean
    fun virtualQueueWebMvcConfig(interceptor: EntryTokenGateInterceptor): VirtualQueueWebMvcConfig =
        VirtualQueueWebMvcConfig(interceptor)

    @Bean
    fun globalExceptionHandler(): GlobalExceptionHandler = GlobalExceptionHandler()

    @Bean
    fun limitedDropApiController(getLimitedDropUseCase: GetLimitedDropUseCase): LimitedDropApiController =
        LimitedDropApiController(
            createLimitedDropUseCase = mockk<CreateLimitedDropUseCase>(),
            getLimitedDropUseCase = getLimitedDropUseCase,
            purchaseLimitedDropUseCase = mockk<PurchaseLimitedDropUseCase>(),
            getLimitedDropStatsUseCase = mockk<GetLimitedDropStatsUseCase>(),
        )

    @Bean
    fun getLimitedDropUseCase(): GetLimitedDropUseCase = mockk<GetLimitedDropUseCase> {
        every { execute(1L) } returns LimitedDropView(
            dropId = 1L,
            productId = 1L,
            status = LimitedDropStatus.OPEN,
            openAt = ZonedDateTime.now().minusHours(1),
            closeAt = ZonedDateTime.now().plusHours(1),
            remaining = 5,
            perUserLimit = 1,
            totalQuantity = 10,
            price = BigDecimal("10000"),
        )
    }

    @Bean
    fun eventApiController(): EventApiController = EventApiController(
        listEventsUseCase = mockk<ListEventsUseCase>(),
        getEventUseCase = mockk<GetEventUseCase>(),
        selectSeatsUseCase = mockk<SelectSeatsUseCase>(),
        releaseSeatsUseCase = mockk<ReleaseSeatsUseCase>(),
    )
}

private fun buildRoutingMockMvc(): MockMvc {
    val context = AnnotationConfigWebApplicationContext()
    context.register(RoutingTestContextConfig::class.java)
    context.servletContext = MockServletContext()
    context.refresh()
    return MockMvcBuilders.webAppContextSetup(context).build()
}

/**
 * [VirtualQueueWebMvcConfig.addInterceptors]가 실제 Spring MVC `HandlerMapping`에 등록하는
 * path pattern이 프로덕션 대상 두 경로(한정판 구매·티케팅 좌석선택)와 정확히 일치하는지
 * 검증한다. `InterceptorRegistry`/`InterceptorRegistration`은 등록된 패턴을 공개 API로
 * 노출하지 않으므로(getter 부재), 실제 `DispatcherServlet` 라우팅을 통해 간접 검증한다
 * (`AnnotationConfigWebApplicationContext` + `@EnableWebMvc` — Testcontainers·전체 앱 부팅 불요).
 */
class VirtualQueueWebMvcConfigTest : BehaviorSpec({

    Given("VirtualQueueWebMvcConfig가 실제 Spring MVC 컨텍스트에 배선된 상태 (플래그 ON + 토큰 항상 거부)") {
        val mockMvc = buildRoutingMockMvc()

        When("POST /limited-drops/{dropId}/orders 요청 시") {
            Then("등록된 경로라 게이트가 실행되어 403을 반환한다") {
                mockMvc.perform(post("/limited-drops/1/orders").header("X-User-Id", 1L))
                    .andExpect(status().isForbidden)
            }
        }

        When("POST /events/{eventId}/seats/select 요청 시") {
            Then("등록된 경로라 게이트가 실행되어 403을 반환한다") {
                mockMvc.perform(post("/events/1/seats/select").header("X-User-Id", 1L))
                    .andExpect(status().isForbidden)
            }
        }

        When("GET /limited-drops/{dropId} 요청 시 (등록 대상 두 경로 밖)") {
            Then("게이트를 거치지 않고 200을 반환한다") {
                mockMvc.perform(get("/limited-drops/1")).andExpect(status().isOk)
            }
        }
    }
})
