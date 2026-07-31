// [W1-01a] platform — user·partner·notification·alerting·operator·featureflag·mcp·airquality·weather·
// featuredemo·dashboard. platform->{commerce,facility-booking} 12파일(MCP tool + NotificationEventWorker,
// §11-1 W1-01 근거 각주 — R3 미스캔 결합, 1단계는 모듈 의존으로 허용).
// [W1-01c] 10 컨텍스트 + dashboard(D1 방안 a — edge→platform 순환 해소) 소스 이관 +
// 이 모듈이 실제로 쓰는 기술 의존만 선언한다. kotlin("plugin.spring")(all-open)은 컨벤션
// 플러그인이 이미 적용하므로 여기서 개별 선언하지 않는다(W1-01b 회귀 재발 방지 — 상단 buildSrc 주석 참고).
plugins {
    id("sportsapp.kotlin-conventions")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

val springBootVersion: String by project
val kotlinVersion: String by project

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))
    implementation(project(":commerce"))
    implementation(project(":facility-booking"))

    // [W1-01c 리뷰 후속 ①] enforcedPlatform — spring-ai-bom(1.1.6)이 spring-boot-starter-web을
    // 3.5.14로 전이 요청해(spring-ai-starter-mcp-server-webmvc -> spring-boot-starter-web:3.5.14
    // -> spring-boot-starter-json:3.5.14 -> jackson-bom:2.21.2) 순수 platform()의 "높은 값 승리"
    // 규칙에서 spring-core 6.2.18/jackson-databind 2.21.2/logback 1.5.32가 선택되는 컴파일-런타임
    // 스큐가 있었다(컴파일은 3.5.14, bootJar 실행은 io.spring.dependency-management가 있는 bootstrap
    // 기준 3.3.5) — 최대 모듈(431파일)이 대상이라 NoSuchMethodError/NoClassDefFoundError가 프로덕션
    // 런타임에서만 터진다. io.spring.dependency-management 플러그인(또는 dependencyManagement{imports{}}
    // DSL)은 detekt 자체 tool 컨피규레이션까지 BOM 제약을 적용해 kotlin-stdlib를 1.9.23->1.9.25로
    // 끌어올려 "detekt was compiled with Kotlin 1.9.23 but is currently running with 1.9.25" 크래시를
    // 재현한다(common 모듈의 동일 이력, 이 티켓에서 직접 재현 확인) — 그래서 그 플러그인 대신
    // enforcedPlatform()으로 Spring Boot BOM 버전을 다른 전이 요청보다 우선 강제한다. enforcedPlatform은
    // compileClasspath/runtimeClasspath 계열 컨피규레이션에만 적용되고 detekt 컨피규레이션에는
    // 적용되지 않아 이 크래시가 재현되지 않는다(하단 검증 참고).
    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kafka — NotificationEventWorker(event.payment.payment.v1·event.booking.booking.v1·
    // event.ticketing.ticket.v1 구독, containerFactory 는 bootstrap KafkaConsumerConfig 전역 빈)
    implementation("org.springframework.kafka:spring-kafka")

    // Spring AI MCP — McpToolRegistryConfig·mcp 서브시스템 12 tool (@Tool)
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.6"))
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // Micrometer — FeatureFlagMetricsBinder·AlertCooldown 등 게이지/카운터
    implementation("io.micrometer:micrometer-core")

    // JSON Column — Notification·FeatureFlag·FeatureFlagAuditLog·Alert snapshot data class 매핑(JsonStringType)
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // 좌표 변환 (WGS84 → 에어코리아 TM 좌표계 EPSG:5181, AirKoreaTmProjection)
    implementation("org.locationtech.proj4j:proj4j:1.3.0")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트 — standalone MockMvc(AirQuality·FeatureDemo·Notification·AlertWebhook ApiControllerTest),
    // ArchUnit(FeatureDemoDomainIsolationTest), mockwebserver 계약 테스트(Telemetry·Discord·KmaWeather).
    // GlobalExceptionHandler·fixedPrincipalResolver·ExternalContractSupport 는 common 의 testFixtures 로
    // 통합 참조한다(W1-01b 리뷰 ① 선례 — platform → bootstrap 은 모듈 의존 방향상 성립하지 않는다).
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(testFixtures(project(":common")))
}

kapt {
    correctErrorTypes = true
}

// -------- 기본 test: live 태그 계약 스모크 제외 (ADR-002, bootstrap 과 동일 설정) --------
// KmaWeatherLiveContractTest(weather 컨텍스트 이관분)는 실 키가 있을 때만 유효한 계약 스모크다.
tasks.named<Test>("test") {
    systemProperty("kotest.tags", "!Live")
}

// [W1-01c 리뷰 후속 ②] verifyExternalLive — KmaWeatherLiveContractTest(tags = Live)가 이 모듈로
// 이관됐는데, 짝인 live 태그 opt-in 실행 태스크가 없어 이 계약 스모크를 어떤 태스크로도 돌릴 수
// 없었다. bootstrap(build.gradle.kts)과 동일한 정의를 이 모듈에도 추가한다(실 키가 env 에 있을 때만
// 유효, opt-in).
val verifyExternalLive by tasks.registering(Test::class) {
    description = "외부 API 계약 live 태그 스모크 실행 (opt-in, 실 키 필요)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("kotest.tags", "Live")
    shouldRunAfter(tasks.test)
}

// [W1-01c 리뷰 후속 ①] spring-ai-bom(1.1.6)의 dependency constraint 가 kotlin-reflect/kotlin-stdlib 를
// 1.9.25 로 끌어올린다 — enforcedPlatform(spring-boot-dependencies) 은 그 BOM 이 관리하는 좌표(spring-*,
// jackson-*, logback 등)에만 적용되고 kotlin-stdlib/kotlin-reflect 는 spring-boot-dependencies BOM의
// 관리 범위 밖이라 enforcedPlatform 승격만으로는 해결되지 않는다(jackson-module-kotlin 은 이제
// enforcedPlatform 이 커버해 force 불필요 — 상단 확인). 이 프로젝트의 Kotlin Gradle 플러그인은 1.9.23
// (gradle.properties kotlinVersion)이라 bootstrap 런타임(kotlin-stdlib 1.9.23)과 어긋나면 그 자체가
// 또 다른 컴파일-런타임 스큐이므로, kotlinVersion 으로 강제 고정한다.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }
}
