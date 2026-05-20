import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
    id("io.gitlab.arturbosch.detekt")
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
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")

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
}

// -------- detekt --------
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("${rootDir}/config/detekt/detekt.yml"))
    source.setFrom(
        "src/main/kotlin",
        "src/test/kotlin"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
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
}
