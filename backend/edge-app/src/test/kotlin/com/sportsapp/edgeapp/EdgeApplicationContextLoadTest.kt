package com.sportsapp.edgeapp

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.edgeapp.config.EdgeFacadeAsyncConfig
import com.sportsapp.edgeapp.featureflag.RedisOnlyFeatureFlagEvaluator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * edge 독립 실행체가 **DataSource 없이** 부팅하는지 확인한다 (S2-08 ⑥).
 *
 * 이 테스트가 잡는 것은 컴파일도 모듈 테스트도 못 잡는 배선이다:
 *  - `catalogSearchExecutor`·`orderHistoryExecutor` 는 파사드가 **이름으로** 주입받는다. 모듈
 *    경계를 넘는 이름 기반 바인딩이라 조립된 컨텍스트에서만 드러난다.
 *  - `MeterRegistry` 는 edge 모듈이 오토컨픽하지 못한다(actuator 미보유). 모놀리스에서는 bootstrap 이
 *    공급했고, 독립 실행체는 자기가 소유해야 한다.
 *  - `DistributedLock`(AdmissionPumpScheduler 부팅 필수 의존)이 없으면 기동이 즉시 실패한다.
 *
 * 인증 체인(S2-11)·피처 플래그(S2-12)·이미지 어댑터(S2-13)는 아직 없다 — 확인 범위를 **컨텍스트
 * 로드와 조립자 공급 빈**으로 한정하고, 나머지는 후속 티켓이 채운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        // HmacEntryTokenGateway 가 빈 값이면 부팅을 실패시킨다(약한 기본값 방지) — 실제 배포와
        // 같은 조건을 만들기 위해 값을 넣는다.
        "virtual-queue.token.secret=edge-app-context-load-test-secret",
        // 입장 펌프가 테스트 도중 Redis 를 계속 두드리지 않게 틱을 늘린다(부팅 확인이 목적이다).
        "virtual-queue.admission.tick-seconds=3600",
    ],
)
@ContextConfiguration(initializers = [EdgeApplicationContextLoadTest.RedisInitializer::class])
class EdgeApplicationContextLoadTest(
    @Autowired private val applicationContext: ApplicationContext,
    @LocalServerPort private val serverPort: Int,
    @Autowired private val meterRegistry: MeterRegistry,
    @Autowired @Qualifier("catalogSearchExecutor") private val catalogSearchExecutor: ThreadPoolTaskExecutor,
    @Autowired @Qualifier("orderHistoryExecutor") private val orderHistoryExecutor: ThreadPoolTaskExecutor,
) : DescribeSpec({

    describe("edge-app 컨텍스트") {
        it("DataSource 없이 부팅한다 — edge 는 소유 테이블이 0 이다") {
            applicationContext.getBeanNamesForType(javax.sql.DataSource::class.java).toList() shouldBe emptyList()
        }

        it("서비스 이름이 sports-edge 다 — 메트릭·추적에서 모놀리스와 구분된다") {
            applicationContext.environment.getProperty("spring.application.name") shouldBe "sports-edge"
        }
    }

    // **MockMvc 로 검증하지 않는다.** 이 모듈에는 spring-security-test 가 없어
    // @AutoConfigureMockMvc 가 보안 필터 체인을 적용하지 않는다 — 그 상태로 단언하면 필터를
    // 거치지 않은 결과를 보게 되고(실제로 그렇게 통과했다) 단언이 공허해진다. 실 포트로 호출한다.
    describe("헬스 엔드포인트 (실 HTTP)") {
        it("GET /actuator/health 가 200 UP 을 반환한다 — 컨테이너 헬스체크(S2-14)의 전제다") {
            val response = httpGet(serverPort, "/actuator/health")
            response.statusCode() shouldBe 200
            response.body() shouldContain "\"status\":\"UP\""
        }
    }

    describe("자립 구현 배선") {
        it("FeatureFlagEvaluator 가 Redis 전용 구현으로 주입된다 (S2-12)") {
            // platform 의 구현은 @PostConstruct 에서 MySQL 을 조회한다 — edge 는 DataSource 가 없어
            // 그 구현이 섞이면 부팅이 깨진다. 타입 기반 주입이라 컴파일은 통과하고 풀부팅만 드러낸다.
            val evaluators = applicationContext.getBeanNamesForType(FeatureFlagEvaluator::class.java)
            evaluators.size shouldBe 1
            applicationContext.getBean(FeatureFlagEvaluator::class.java)
                .shouldBeInstanceOf<RedisOnlyFeatureFlagEvaluator>()
        }
    }

    describe("조립자 공급 계약 인수") {
        it("MeterRegistry 를 자기가 소유한다 — 모놀리스에서는 bootstrap 이 공급하던 빈이다") {
            meterRegistry.config().commonTags(emptyList()) // 접근 가능하면 주입 성립
        }

        it("catalogSearchExecutor 가 이름으로 주입되고 값이 모놀리스와 같다") {
            catalogSearchExecutor.corePoolSize shouldBe EdgeFacadeAsyncConfig.CATALOG_CORE_POOL_SIZE
            catalogSearchExecutor.maxPoolSize shouldBe EdgeFacadeAsyncConfig.CATALOG_MAX_POOL_SIZE
            catalogSearchExecutor.queueCapacity shouldBe EdgeFacadeAsyncConfig.CATALOG_QUEUE_CAPACITY
        }

        it("orderHistoryExecutor 가 이름으로 주입되고 값이 모놀리스와 같다") {
            orderHistoryExecutor.corePoolSize shouldBe EdgeFacadeAsyncConfig.ORDER_CORE_POOL_SIZE
            orderHistoryExecutor.maxPoolSize shouldBe EdgeFacadeAsyncConfig.ORDER_MAX_POOL_SIZE
            orderHistoryExecutor.queueCapacity shouldBe EdgeFacadeAsyncConfig.ORDER_QUEUE_CAPACITY
        }
    }
}) {
    /** 레포 관례 — GenericContainer 는 @ServiceConnection 대상이 아니라 초기화자로 좌표를 주입한다. */
    class RedisInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.redis.host=${SharedTestContainers.redis.host}",
                "spring.data.redis.port=${SharedTestContainers.redis.getMappedPort(6379)}",
            )
        }
    }

    companion object {
        init {
            SharedTestContainers.redis
        }

        private val httpClient: HttpClient = HttpClient.newHttpClient()

        fun httpGet(port: Int, path: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }
}
