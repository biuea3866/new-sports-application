// [S2-08] edge-app — edge 를 조립하는 **두 번째 컴포지션 루트**.
//
// `edge` 모듈에 직접 org.springframework.boot 플러그인을 붙이지 않는 이유는 두 가지다:
//  1. 산출물 충돌 — bootstrap 이 `implementation(project(":edge"))` 로 edge 의 plain jar 를 쓴다.
//     plain jar 를 끄면 모놀리스 빌드가 깨지고, 켜 두면 build/libs 에 jar 가 2개가 되어 Dockerfile 의
//     단일 COPY 와일드카드가 깨진다(bootstrap 이 정확히 같은 이유로 plain jar 를 끈다).
//  2. 중첩 @SpringBootApplication — SportsApplication 이 com.sportsapp 을 스캔하므로 edge 안의 두 번째
//     @SpringBootApplication 을 모놀리스 컨텍스트가 @Configuration 으로 주워 담는다.
//
// bootstrap 은 :edge-app 을 의존하지 않는다 — 그래서 스캔 충돌이 없고, 롤백은 settings.gradle.kts 의
// include 한 줄 제거로 끝난다.
//
// [이 파일이 의존·설정을 통째로 소유하는 이유] 후속 5개 티켓(S2-09~S2-13)이 각자 이 파일과
// application.yml 을 만지면 같은 파일 충돌로 병렬이 깨진다. 필요한 의존과 설정 키를 여기서 전부
// 선언하고, 후속 티켓은 Kotlin 소스만 추가한다.
plugins {
    id("sportsapp.kotlin-conventions")
    id("org.springframework.boot")
}

group = "com.sportsapp"
version = "0.0.1-SNAPSHOT"

// bootstrap 과 같은 이유 — bootJar 하나만 남긴다(Dockerfile 단일 COPY 와일드카드).
tasks.named<Jar>("jar") {
    enabled = false
}

val springBootVersion: String by project
val kotlinVersion: String by project

dependencies {
    implementation(project(":common"))
    implementation(project(":edge"))

    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // edge 의 유일한 저장소 — 대기열 상태·입장 토큰·Lua 3종. DataSource·JPA·Kafka·Mongo 는 선언하지
    // 않는다(오토컨픽 자체가 뜨지 않는다 — application-edge.yml 이 exclude 로 하던 일을 의존 부재로 대체).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // 조립자 공급 계약 인수 — edge 는 MeterRegistry 를 스스로 오토컨픽하지 못한다(actuator 미보유).
    // 모놀리스에서는 bootstrap 이 공급했고, 독립 실행체가 되는 지금은 자기가 소유해야 한다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // 분산 추적 — 모놀리스와 같은 구성이어야 경계를 넘는 trace 가 이어진다(W1-09).
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(testFixtures(project(":common")))
}

// [W1-01d] BOM 이 관리하는 kotlin-stdlib/reflect 가 Kotlin Gradle 플러그인 버전과 어긋나면
// 컴파일러-런타임 스큐다 — 다른 모듈과 동일 근거로 강제 고정한다.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }
}
