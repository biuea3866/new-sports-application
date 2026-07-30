import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// [W1-01a] bootstrap — 전 7모듈 조립 + 전역 설정(config/security/persistence/messaging/audit/
// external/loadshedding) + ArchUnit + application.yml/db-migration 단일 소유. bootJar 산출은 이 모듈만.
plugins {
    id("sportsapp.kotlin-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

group = "com.sportsapp"
version = "0.0.1-SNAPSHOT"

// Spring Boot Gradle Plugin 3.x는 기본적으로 plain jar(`jar`)와 bootJar 를 모두 산출한다(파일명이
// `-plain` 접미사로 갈릴 뿐). Dockerfile 이 `bootstrap/build/libs/*.jar` 를 단일 대상(app.jar)으로
// COPY 하므로 두 산출물이 남으면 와일드카드가 2개 파일에 매치돼 COPY 가 실패한다 — plain jar 를
// 명시적으로 비활성화해 bootJar 하나만 남긴다.
tasks.named<Jar>("jar") {
    enabled = false
}

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))
    implementation(project(":commerce"))
    implementation(project(":facility-booking"))
    implementation(project(":platform"))
    implementation(project(":social"))
    implementation(project(":edge"))

    // Spring AI MCP (1.1.6 GA — Gate #A 검증, Java 17 minimum 충족)
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.6"))
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    testImplementation("org.springframework.ai:spring-ai-test")

    // Monitoring (Prometheus 메트릭 + OTel 분산 추적 — ADR-001)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.4.0-alpha")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // WebSocket / STOMP (BE-04 실시간 전송 계층 — chat.realtime.enabled 플래그로 조건부 활성화)
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Retry (동시 INSERT 경합 → fresh tx 재시도)
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Spring Batch (BE-11: products.seller_type 청크 백필 — Flyway 인라인 대량 DML 금지 원칙에 따라
    // 애플리케이션 배치가 기존 NULL 행을 청크 단위 커밋으로 채운다. 사용자 지정 도구.)
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:kafka")

    // QueryDSL (bootstrap 이 @Entity 를 소유하는 유일한 모듈 — 이 티켓 기준)
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // MongoDB
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // AWS SDK v2 (S3 + MinIO Presigned URL)
    implementation("software.amazon.awssdk:s3:2.31.19")

    // 좌표 변환 (WGS84 → 에어코리아 TM 좌표계 EPSG:5181, AirKoreaTmProjection)
    implementation("org.locationtech.proj4j:proj4j:1.3.0")

    // JSON Column
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("io.mockk:mockk:1.13.12")

    // ArchUnit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Testcontainers
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // common 의 testFixtures — BaseIntegrationTest·BaseJpaIntegrationTest(자체 유지)가 참조하는
    // SharedTestContainers·BaseIntegrationTest·BaseMongoIntegrationTest 를 여기서 공급받는다.
    testImplementation(testFixtures(project(":common")))

    // External API 계약 검증 하네스 (ADR-002) — 버전은 Spring Boot BOM(okhttp-bom)이 관리
    testImplementation("com.squareup.okhttp3:mockwebserver")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// -------- 기본 test: live 태그 계약 스모크 제외 (ADR-002) --------
// 실 키가 있을 때만 도는 계약 스모크(live 태그)는 CI 상시 test 에서 제외한다.
// Kotest 는 시스템 프로퍼티 kotest.tags 로 포함/제외 태그 표현식을 읽는다.
tasks.named<Test>("test") {
    systemProperty("kotest.tags", "!Live")
}

// -------- verifyExternalLive: 외부 API 계약 live 태그 스모크 (opt-in) --------
// 실 키가 env 에 있을 때만 유효한 검증이 되는 live 태그 스펙만 선택 실행한다.
// 클래스별 와이어업 없이 Kotest 태그 필터만으로 동작한다(BE-02/03/04 는 태그만 부여).
val verifyExternalLive by tasks.registering(Test::class) {
    description = "외부 API 계약 live 태그 스모크 실행 (opt-in, 실 키 필요)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("kotest.tags", "Live")
    shouldRunAfter(tasks.test)
}

// -------- archTest: 아키텍처 규칙 전용 태스크 --------
// com.sportsapp.architecture 패키지의 fitness function 테스트만 별도로 실행한다.
// bootstrap 은 전 7모듈을 implementation 으로 의존하므로 이 모듈의 test runtimeClasspath 에
// 전 모듈 클래스가 모두 올라온다 — ArchUnit importPackages("com.sportsapp") 가 전 모듈을 스캔한다.
// 2단계(게이트 승격, ADR-005): check 가 archTest 에 의존해 규칙 위반 시 빌드를 실패시킨다.
val archTest by tasks.registering(Test::class) {
    description = "아키텍처 경계 규칙 fitness function 실행 (com.sportsapp.architecture)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("com.sportsapp.architecture.*")
    }
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(archTest)
}
