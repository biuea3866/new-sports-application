import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

// [W1-01a] 8모듈 공통 Kotlin/detekt/harnessCheck/test 설정 컨벤션 플러그인.
// 모듈별 build.gradle.kts 가 `id("sportsapp.kotlin-conventions")` 로 적용한다.
// 기술별 의존성(spring-boot BOM, kafka, mongo, querydsl kapt 등)은 이 플러그인에 넣지 않는다 —
// 각 모듈이 실제로 쓰는 것만 자기 build.gradle.kts 에 선언한다 (근거: 티켓 ⑤ dependencies 분리 원칙).
//
// [W1-01b 리뷰 후속 — 회귀 수정] kotlin("plugin.spring")(all-open)을 8모듈 공통으로 올린다.
// Kotlin 클래스는 기본이 final 이라 @Configuration/@Component/@Service/@Transactional/@Async/
// @Cacheable 이 붙은 클래스를 all-open 없이 두면 ① @Configuration 은 "may not be final" 로 즉시
// 실패하고 ② @Transactional/@Retryable/@Async 는 CGLIB 프록시 생성이 안 돼 트랜잭션·재시도가
// 조용히 무력화된다(기동은 되지만 AOP 가 안 걸린 채로). W1-01a 골격 추출 이후 bootstrap 모듈에만
// 개별 선언돼 있어, 소스가 이관된 payment/commerce/facility-booking(및 앞으로 소스가 채워질
// platform/social/edge)의 모든 UseCase/DomainService 가 이 보호를 받지 못하는 회귀가 있었다.
// 개별 모듈마다 선언하게 두면 새 모듈이 추가될 때마다 같은 함정이 반복되므로 컨벤션 플러그인
// 한 곳으로 강제한다.
//
// kotlin("plugin.jpa")(no-arg)는 이 컨벤션에 올리지 않고 @Entity 를 소유하는 모듈(common/payment/
// commerce/facility-booking/bootstrap)이 각자 선언하는 현재 방식을 유지한다 — plugin.spring 과 달리
// 이미 필요한 모듈 전부가 명시적으로 선언 중이라 발견된 회귀가 없고, @Entity 가 아직 없는 platform/
// social/edge 에는 불필요한 플러그인 적용을 늘릴 이유가 없다(무해하지만 "이 모듈이 실제로 쓰는 것만
// 선언한다" 는 이 파일 상단 원칙과도 맞는다). 이후 플랫폼 등에 @Entity 가 추가되면 그 모듈이 개별
// 선언하면 된다.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
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
    // Gradle 의 `--tests` 는 Kotest spec 을 걸러내지 못한다 — 단일 클래스만 지정해도 모듈의
    // 다른 spec 이 함께 실행돼 Testcontainers 를 줄줄이 띄운다(bootstrap 에서 확인).
    // Kotest 자체 필터(`kotest.filter.specs`)를 gradle JVM → test JVM 으로 전달해
    // 한 spec 만 실행할 수 있게 한다. 예:
    //   ./gradlew :bootstrap:test -Dkotest.filter.specs='*HttpResponseJsonNullInclusionTest*'
    System.getProperty("kotest.filter.specs")?.let { systemProperty("kotest.filter.specs", it) }
    System.getProperty("kotest.filter.tests")?.let { systemProperty("kotest.filter.tests", it) }
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
            // [W1-DEBT-01] 패턴을 컨벤션 범위로 좁혔다.
            // 이전 패턴은 날짜 전용 타입(시각 없는 달력 날짜)까지 함께 잡아 facility-booking 56건 +
            // bootstrap 2건, 총 58건의 **오탐**을 만들었다. 대상은 전부 시설 운영시간·휴무일이며,
            // 영업 개시 시각은 벽시계 시각이고 휴무일은 달력 날짜라 그 타입들이 올바른 도메인 모델링이다.
            // 컨벤션(private-be-code-convention no-local-datetime)이 금지하는 것은 "날짜+시각" 타입과
            // 절대시각·시계 추상뿐이고, 날짜 전용·시각 전용 타입은 금지 대상이 아니다.
            //
            // 메시지의 절대시각 권유도 컨벤션과 모순이라 바로잡았다 — 그 타입 역시 금지다.
            // 절대시각(3파일)·시계 추상(1파일)은 이 규칙이 애초에 검사하지 않아 숨어 있던 실제 위반이며,
            // JWT exp 클레임 등 판단이 필요해 이 티켓에서 함께 고치지 않는다(부채 항목으로 남긴다).
            // [W1-DEBT-01 후속] 컨벤션이 금지하는 나머지 두 타입도 함께 검사한다 — 규칙이 이들을
            // 아예 보지 않아 JWT 경로 3파일에 실제 위반이 숨어 있었다(그 3파일을 먼저 정리한 뒤 확장).
            Triple("no-local-datetime", Regex("""\b(LocalDateTime|Instant|Clock)\b"""), "ZonedDateTime 을 사용합니다."), // private-allow:no-local-datetime
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
