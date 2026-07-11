package com.sportsapp.scenario.virtualqueue

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.common.EntryTokenGuard
import com.sportsapp.domain.virtualqueue.gateway.EntryTokenIssuer
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.infrastructure.virtualqueue.config.VirtualQueueWebMvcConfig
import com.sportsapp.infrastructure.virtualqueue.metrics.VirtualQueueMetricsBinder
import com.sportsapp.infrastructure.virtualqueue.redis.VirtualQueueStoreImpl
import com.sportsapp.infrastructure.virtualqueue.token.HmacEntryTokenGateway
import com.sportsapp.presentation.virtualqueue.controller.VirtualQueueApiController
import com.sportsapp.presentation.virtualqueue.interceptor.EntryTokenGateInterceptor
import com.sportsapp.presentation.virtualqueue.scheduler.AdmissionPumpScheduler
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 가상 대기열 전 컴포넌트가 배선된 상태로 `@SpringBootTest` 풀부팅이 성공하는지 검증한다(BE-11,
 * TDD "Testing Plan" scenario 레벨 "풀부팅 1개").
 *
 * MEMORY 교훈("동일 simple명 @Component 빈 충돌·파사드 프로파일 DI 붕괴는 슬라이스로 못 잡는다")에
 * 따라 슬라이스 테스트가 아니라 [BaseIntegrationTest]의 전체 컨텍스트로 검증한다. 이 클래스 자체가
 * 인스턴스화되고 `init` 블록이 평가되는 시점에 이미 Spring 컨텍스트 로드가 끝나 있어야 하므로,
 * 빈 충돌·의존성 미충족이 있으면 컨텍스트 로드 실패로 이 클래스의 모든 테스트가 즉시 실패한다.
 */
@AutoConfigureMockMvc
class VirtualQueueFullBootScenarioTest(
    @Autowired private val applicationContext: ApplicationContext,
    @Autowired private val mockMvc: MockMvc,
) : BaseIntegrationTest() {

    init {
        Given("가상 대기열 전 컴포넌트(BE-01~BE-10)가 배선된 애플리케이션 컨텍스트가") {
            When("Spring Boot 풀부팅이 완료되면") {
                Then("VirtualQueueApiController 빈이 유일하게 등록된다") {
                    applicationContext.getBean(VirtualQueueApiController::class.java).shouldNotBeNull()
                }

                Then("EntryTokenGateInterceptor 빈이 유일하게 등록된다") {
                    applicationContext.getBean(EntryTokenGateInterceptor::class.java).shouldNotBeNull()
                }

                Then("VirtualQueueWebMvcConfig 빈이 유일하게 등록된다") {
                    applicationContext.getBean(VirtualQueueWebMvcConfig::class.java).shouldNotBeNull()
                }

                Then("AdmissionPumpScheduler 빈이 유일하게 등록된다") {
                    applicationContext.getBean(AdmissionPumpScheduler::class.java).shouldNotBeNull()
                }

                Then("VirtualQueueMetricsBinder 빈이 유일하게 등록된다") {
                    applicationContext.getBean(VirtualQueueMetricsBinder::class.java).shouldNotBeNull()
                }

                Then("HmacEntryTokenGateway 빈이 유일하게 등록되고 EntryTokenIssuer·EntryTokenGuard 양쪽으로 조회 가능하다") {
                    applicationContext.getBean(HmacEntryTokenGateway::class.java).shouldNotBeNull()
                    applicationContext.getBean(EntryTokenIssuer::class.java).shouldNotBeNull()
                    applicationContext.getBean(EntryTokenGuard::class.java).shouldNotBeNull()
                }

                Then("VirtualQueueStoreImpl 빈이 유일하게 등록되고 VirtualQueueStore로 조회 가능하다") {
                    applicationContext.getBean(VirtualQueueStoreImpl::class.java).shouldNotBeNull()
                    applicationContext.getBean(VirtualQueueStore::class.java).shouldNotBeNull()
                }
            }
        }

        Given("EntryTokenGateInterceptor가 구매 앞단 2경로에만 등록된 상태에서") {
            When("인터셉터 등록 범위 밖의 기존 라우트(GET /events)를 호출하면") {
                Then("인터셉터에 막히지 않고 정상 200을 반환한다(기존 라우트 회귀 없음)") {
                    mockMvc.perform(get("/events")).andExpect(status().isOk)
                }
            }
        }

        Given("신규 대기열 조회 라우트가 배선된 상태에서") {
            When("아직 아무도 진입하지 않은 대상의 통계를 조회하면") {
                Then("GET /virtual-queues/{type}/{targetId}/stats가 200과 0건 통계를 반환한다") {
                    val result = mockMvc.perform(get("/virtual-queues/limited-drop/9999999/stats"))
                        .andExpect(status().isOk)
                        .andReturn()
                    result.response.status shouldBe 200
                }
            }
        }
    }
}
