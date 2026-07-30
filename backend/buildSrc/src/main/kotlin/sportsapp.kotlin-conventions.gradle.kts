import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

// [W1-01a] 8모듈 공통 Kotlin/detekt/harnessCheck/test 설정 컨벤션 플러그인.
// 모듈별 build.gradle.kts 가 `id("sportsapp.kotlin-conventions")` 로 적용한다.
// 기술별 의존성(spring-boot BOM, kafka, mongo, querydsl kapt 등)은 이 플러그인에 넣지 않는다 —
// 각 모듈이 실제로 쓰는 것만 자기 build.gradle.kts 에 선언한다 (근거: 티켓 ⑤ dependencies 분리 원칙).

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        val javaVersion = (project.property("javaVersion") as String).toInt()
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

dependencies {
    "testImplementation"("io.kotest:kotest-runner-junit5:5.9.1")
    "testImplementation"("io.kotest:kotest-assertions-core:5.9.1")
    "testImplementation"("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    "testImplementation"("io.mockk:mockk:1.13.12")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test>().configureEach {
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

// -------- detekt (모듈별 소스셋, 공용 config/baseline 은 rootProject 기준 경로) --------
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
    baseline = file("${rootProject.projectDir}/detekt-baseline.xml")
    source.setFrom(
        "src/main/kotlin",
        "src/test/kotlin",
        "src/testFixtures/kotlin",
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
    }
}

// -------- harness-rules 정적 패턴 검증 (모듈별 자기 소스만 스캔) --------
// detekt 의 ForbiddenImport 와 별개로, 행 단위 정규식 패턴을 강제한다. 아래 리터럴은 규칙 이름/설명
// 문자열 자체이지 실제 코드 사용이 아니다 (private-forbidden-patterns.sh 는 이 파일 자체도 스캔하므로 명시 예외 처리).
// 단일 소스셋(file("src")) 하드코딩 대신 project.projectDir 기준 상대 경로를 쓴다 —
// 이래야 모듈마다 harnessCheck 가 "자기 모듈 소스만" 스캔한다 (티켓 ⑤ 표 harnessCheck 전환 항목).
val harnessCheck by tasks.registering {
    description = "Harness rules: forbid at-Query, LocalDateTime, ConsumerRecord raw, double-bang" // private-allow:no-local-datetime,no-double-bang,no-consumer-record
    group = "verification"

    val sourceDir = project.file("src")
    val reportFile = layout.buildDirectory.file("reports/harness/harness-check.txt").get().asFile
    val projectDirPath = project.projectDir
    val modulePath = project.path

    inputs.dir(sourceDir)
    outputs.file(reportFile)

    doLast {
        val forbidden: List<Triple<String, Regex, String>> = listOf(
            Triple("no-jpa-query", Regex("""@Query\s*\("""), "QueryDSL CustomRepository 패턴을 사용합니다."),
            Triple("no-local-datetime", Regex("""\bLocalDate(Time)?\b"""), "ZonedDateTime / Instant 를 사용합니다."), // private-allow:no-local-datetime
            Triple("no-consumer-record-raw", Regex("""ConsumerRecord<\s*String\s*,\s*String\s*>"""), "DTO + JsonDeserializer 로 매핑합니다."), // private-allow:no-consumer-record
            Triple("no-non-null-assertion", Regex("""(?<!!)!!(?!=)"""), "requireNotNull / ?: / ?.let 으로 대체합니다."), // private-allow:no-double-bang
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
            reportFile.writeText("PASS — harness-rules 위반 0건 ($modulePath)\n")
            logger.lifecycle("harnessCheck ($modulePath): PASS (0 violations)")
        } else {
            val message = "Harness rules 위반 ${violations.size}건 ($modulePath):\n" + violations.joinToString("\n")
            reportFile.writeText(message)
            throw GradleException(message)
        }
    }
}

tasks.named("check") {
    dependsOn(harnessCheck)
}
