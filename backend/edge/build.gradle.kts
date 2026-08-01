// [W1-01a] edge — virtualqueue + catalog·order 파사드 + image + 게이트웨이 라우팅.
// [W1-01d] 4 컨텍스트 55 main 파일(virtualqueue 37·catalog 7·order 6·image 5) + 대기열 Lua 3
// (resources/redis/{enter,admit,evict}.lua) 이관 + 이 모듈이 실제로 쓰는 기술 의존만 선언한다.
// [S2-01] edge->commerce 13 / facility-booking 6 / social 2 (W1-01d 실측)였던 파사드 읽기 fan-out
// (C6·C7)을 edge 소유 CatalogSearchGateway·OrderHistoryGateway 뒤로 역전했다 — 1단계 구현(로컬
// 어댑터)은 bootstrap이 소유해 commerce·facility-booking·social을 컴파일 의존한다. edge main은
// 더 이상 이 3모듈을 의존하지 않는다(`ModuleDependencyGraphTest`). 2단계에는 어댑터를 edge 안의
// RestClient 구현으로 교체한다(300ms 타임아웃·`failedDomains` 부분 저하 로직은 CatalogCompositionService·
// OrderCompositionService에 그대로 남아 동작 변화가 없다).
// [S2-01] commerce는 virtualqueue 테스트(EntryTokenGateInterceptorTest·VirtualQueueWebMvcConfigTest가
// LimitedDropApiController·EventApiController를 실 컨트롤러로 라우팅 검증)가 여전히 필요해
// testImplementation으로 유지한다 — platform이 이미 이 패턴(VirtualQueueFeatureFlagKeysTest 전용)이다.
//
// [소유 테이블 0] edge 는 자기 테이블이 없다 — W1-04 GRANT 에서 `svc_edge` 무권한이고 상태는 전부
// Redis(대기열·입장 토큰)에 있다. 따라서 `kotlin("plugin.jpa")`·QueryDSL kapt·starter-data-jpa 를
// 선언하지 않는다. 파사드가 읽는 상품·예약 데이터는 commerce·facility-booking 의 DomainService
// 경유이며(각자 자기 DataSource 로 읽는다) edge 가 직접 접근하지 않는다.
//
// [BOM 은 enforcedPlatform] W1-01c 에서 순수 platform() 사용 시 컴파일-런타임 스큐(전이 의존이
// 더 높은 Spring Boot 를 요구하면 constraint 는 highest-wins 로 그쪽이 선택되고, testRuntimeClasspath
// 도 같이 올라가 테스트가 전부 GREEN 인 채 런타임에만 NoSuchMethodError 로 드러남)를 재현했다.
// io.spring.dependency-management 플러그인은 쓰지 않는다(detekt tool 컨피규레이션까지 BOM 을 적용해
// kotlin-stdlib 를 1.9.23->1.9.25 로 끌어올려 크래시).
plugins {
    id("sportsapp.kotlin-conventions")
}

val springBootVersion: String by project
val kotlinVersion: String by project

dependencies {
    implementation(project(":common"))

    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Redis — 대기열 상태(VirtualQueueStoreImpl)·입장 토큰·Lua 스크립트 3종. edge 의 유일한 저장소다.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Micrometer — VirtualQueueMetricsBinder·VirtualQueueStoreImpl·EntryTokenGateInterceptor 의
    // 관측 지표(MeterRegistry). edge 는 spring-boot-starter-actuator 를 갖지 않아 MeterRegistry 빈을
    // 스스로 오토컨픽하지 못한다 — 조립자(현재 bootstrap)가 공급한다는 암묵 계약이며, 2단계에서
    // edge 가 독립 서비스로 분리되면 자기 actuator 를 직접 소유해야 한다(commerce 와 동일 계약).
    //
    // [조립자 공급 계약 — 2단계 분리 시 edge 가 직접 소유해야 하는 빈 전체 목록]
    //  1. MeterRegistry (위 — actuator 미보유)
    //  2. `@Qualifier("catalogSearchExecutor") AsyncTaskExecutor` — CatalogCompositionService 가
    //     이름으로 주입받는다. 빈 정의는 bootstrap 의 CatalogAsyncConfig(core 4/max 8/queue 50).
    //  3. `orderHistoryExecutor` — OrderCompositionService 용. 빈 정의는 bootstrap 의
    //     OrderHistoryAsyncConfig.
    // 2·3 은 모듈 경계를 넘는 **이름 기반** 바인딩이라 컴파일·모듈 테스트가 잡지 못하고 조립된
    // 컨텍스트(현재 bootstrap 풀부팅)에서만 검증된다 — 파사드 전용 스레드풀이므로 2단계에서
    // 파사드와 함께 반드시 옮긴다.
    implementation("io.micrometer:micrometer-core")

    // 단위 테스트 — standalone MockMvc(VirtualQueue·Catalog·OrderHistory ApiControllerTest),
    // 실 Redis Testcontainers(VirtualQueueStoreImplTest·VirtualQueueLuaScriptsContractTest·
    // HmacEntryTokenGatewayRedisIntegrationTest — common testFixtures 의 SharedTestContainers.redis
    // 재사용, 중첩 @SpringBootApplication + DataSource 오토컨픽 제외의 경량 컨텍스트 패턴).
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(testFixtures(project(":common")))

    // VirtualQueueFeatureFlagKeysTest 만 platform 의 FeatureFlag·EvaluationStrategy 를 참조한다.
    // main 소스는 platform 을 쓰지 않으므로 컴파일 경계를 좁게 유지하려고 testImplementation 이다.
    testImplementation(project(":platform"))

    // [S2-01] EntryTokenGateInterceptorTest·VirtualQueueWebMvcConfigTest 가 실 컨트롤러
    // (LimitedDropApiController·EventApiController)·UseCase(commerce 의 goods/ticketing)를 라우팅
    // 검증 픽스처로 그대로 쓴다. main 소스는 더 이상 commerce 를 쓰지 않으므로(catalog·order 파사드가
    // CatalogSearchGateway·OrderHistoryGateway 로 역전) testImplementation 으로 좁힌다.
    testImplementation(project(":commerce"))
}

// [W1-01d] 1.9.25 압력의 출처는 spring-boot-dependencies BOM 자신이다(enforcedPlatform 의 constraint
// 는 strict 이라 이 BOM 이 관리하는 kotlin-stdlib/kotlin-reflect 버전이 무조건 선택된다). 이 프로젝트
// Kotlin Gradle 플러그인 버전(1.9.23, gradle.properties)과 어긋나면 컴파일러-런타임 스큐이므로
// 강제 고정한다 — social·platform 모듈과 동일 근거.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }
}
