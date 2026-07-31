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

    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
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

// spring-ai-bom(1.1.6)의 dependency constraint 가 jackson-module-kotlin 을 2.21.2 로 끌어올리며
// kotlin-reflect/kotlin-stdlib 를 2.1.21 로 함께 끌어올린다 — 이 프로젝트의 Kotlin Gradle 플러그인은
// 1.9.23(gradle.properties kotlinVersion) 이라 2.1 메타데이터를 읽지 못해 컴파일이 깨진다(bootstrap 은
// org.springframework.boot/io.spring.dependency-management 플러그인이 자체 BOM 을 더 강하게 적용해
// 이 충돌이 나타나지 않는다 — platform 은 그 플러그인을 쓰지 않아 순수 Gradle platform() 제약이 병합되며
// 최댓값이 선택된다). spring-boot-dependencies BOM 이 관리하는 버전으로 강제 고정한다.
configurations.all {
    resolutionStrategy {
        force("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }
}
