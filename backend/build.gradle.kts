import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val javaVersion: String by project

group = "com.sportsapp"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
    }
}

repositories {
    mavenCentral()
}

dependencies {
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

    // Retry (동시 INSERT 경합 → fresh tx 재시도)
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

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

    // QueryDSL
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

    // External API 계약 검증 하네스 (ADR-002) — 버전은 Spring Boot BOM(okhttp-bom)이 관리
    testImplementation("com.squareup.okhttp3:mockwebserver")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "2g"
    testLogging {
        showStandardStreams = true
        showExceptions = true
        showCauses = true
    }
    // 테스트 종료 후 Spring 컨텍스트의 non-daemon 스레드(Kafka 리스너·Tomcat) 누수로
    // 워커 JVM 이 종료 단계에서 멈추면, 이 JVM 이 소유한 Testcontainers 가 무한정 남는다.
    // timeout 으로 멈춘 워커를 강제 종료해 ryuk 가 컨테이너를 회수하도록 한다.
    timeout.set(Duration.ofMinutes(30))
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
// 기존 test 소스셋의 testClassesDirs·classpath 를 재사용하고, useJUnitPlatform/maxHeapSize/
// testLogging/timeout 은 위 tasks.withType<Test> 설정이 Test 타입 전체에 이미 적용된다.
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

// -------- detekt --------
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("${rootDir}/config/detekt/detekt.yml"))
    baseline = file("${rootDir}/detekt-baseline.xml")
    source.setFrom(
        "src/main/kotlin",
        "src/test/kotlin"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
    }
}

// -------- harness-rules 정적 패턴 검증 --------
// detekt 의 ForbiddenImport 와 별개로, 행 단위 정규식 패턴을 강제합니다.
// 위반 시 빌드 실패 (severity: error). 새 위반 패턴은 forbidden 목록에 추가합니다.
val harnessCheck by tasks.registering {
    description = "Harness rules: forbid @Query, LocalDateTime, ConsumerRecord<String,String>, !! 연산자"
    group = "verification"

    val sourceDir = file("src")
    val reportFile = layout.buildDirectory.file("reports/harness/harness-check.txt").get().asFile
    val projectDirPath = projectDir

    inputs.dir(sourceDir)
    outputs.file(reportFile)

    doLast {
        val forbidden: List<Triple<String, Regex, String>> = listOf(
            Triple("no-jpa-query", Regex("""@Query\s*\("""), "QueryDSL CustomRepository 패턴을 사용합니다."),
            Triple("no-local-datetime", Regex("""\bLocalDate(Time)?\b"""), "ZonedDateTime / Instant 를 사용합니다."),
            Triple("no-consumer-record-raw", Regex("""ConsumerRecord<\s*String\s*,\s*String\s*>"""), "DTO + JsonDeserializer 로 매핑합니다."),
            Triple("no-non-null-assertion", Regex("""(?<!!)!!(?!=)"""), "requireNotNull / ?: / ?.let 으로 대체합니다."),
        )

        val violations = mutableListOf<String>()
        if (sourceDir.exists()) {
            sourceDir.walkTopDown().forEach { f ->
                if (!f.isFile || f.extension != "kt") return@forEach
                if (f.absolutePath.contains("${File.separator}build${File.separator}")) return@forEach
                f.readLines().forEachIndexed { idx, raw ->
                    val trimmed = raw.trimStart()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                    forbidden.forEach { rule ->
                        if (rule.second.containsMatchIn(raw)) {
                            violations += "${f.relativeTo(projectDirPath)}:${idx + 1}  [${rule.first}]  → ${rule.third}\n    ${raw.trim()}"
                        }
                    }
                }
            }
        }

        reportFile.parentFile.mkdirs()
        if (violations.isEmpty()) {
            reportFile.writeText("PASS — harness-rules 위반 0건\n")
            logger.lifecycle("harnessCheck: PASS (0 violations)")
        } else {
            val message = "Harness rules 위반 ${violations.size}건:\n" + violations.joinToString("\n")
            reportFile.writeText(message)
            throw GradleException(message)
        }
    }
}

tasks.named("check") {
    dependsOn(harnessCheck)
    dependsOn(archTest)
}
