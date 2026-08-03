// [W1-01a] common — 공유 커널. 어떤 컨텍스트 모듈도 참조하지 않는다 (SharedKernelPurityRulesTest R4).
// domain.common 은 Spring Data 애노테이션(@CreatedDate 등)·jakarta.persistence(@MappedSuperclass)만
// 예외로 사용한다(JpaAuditingBase — 도메인 layer 가 JPA 어노테이션을 import 하는 유일한 허용 지점).
plugins {
    id("sportsapp.kotlin-conventions")
    kotlin("plugin.jpa")
    kotlin("kapt")
    `java-test-fixtures`
}

val springBootVersion: String by project

// `io.spring.dependency-management`(dependencyManagement{imports{...}} DSL) 대신 Gradle 네이티브
// platform() 을 쓴다 — io.spring.dependency-management 는 기본적으로 프로젝트의 "모든" 컨피규레이션에
// BOM 제약을 적용해, kotlin-stdlib 를 1.9.23 -> 1.9.25 로 끌어올려 detekt 실행 커널(1.9.23 로 컴파일됨)과
// 충돌시킨다(bootstrap 은 org.springframework.boot 플러그인만 쓰고 이 DSL 을 직접 호출하지 않아 영향이 없었다).
// implementation(platform(...)) 은 그 설정을 상속하는 컨피규레이션(compileClasspath/runtimeClasspath/
// testImplementation 계열)에만 적용되어 detekt/kapt 도구 컨피규레이션을 오염시키지 않는다.
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // [S2-02] 공유 런타임 커널 승격 — GlobalExceptionHandler·ProblemDetailBuilder·LoadSheddingFilter·
    // RedisDistributedLock 이 bootstrap main 에서 이 모듈로 이동하며 필요해진 main 의존. 새 기술
    // 스택 도입이 아니라 이미 정확히 같은 목록을 선언 중인 testFixtures(아래)의 main 승격이다 —
    // edge 를 별 프로세스로 띄우면 bootstrap 이 공급하던 이 빈들이 사라지므로, 6개 서비스가 공통으로
    // 의존하는 지점(common)으로 옮긴다.
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.data:spring-data-redis")
    implementation("jakarta.servlet:jakarta.servlet-api")
    implementation("jakarta.validation:jakarta.validation-api")

    // JpaAuditingBase(@MappedSuperclass)가 common 소유라, 이를 상속하는 각 컨텍스트 모듈의 @Entity가
    // 참조할 QJpaAuditingBase 도 common 자신의 kapt 실행에서 생성돼야 한다 — 그래야 bootstrap 등
    // 하위 모듈의 kapt 가 컴파일된 클래스로 그것을 참조할 수 있다 (cross-module Q-supertype 문제).
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // testFixtures(BaseIntegrationTest 등)가 Spring Boot Test + Testcontainers 를 사용한다.
    "testFixturesImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    "testFixturesImplementation"("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    "testFixturesImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "testFixturesImplementation"("org.testcontainers:mysql")
    "testFixturesImplementation"("org.testcontainers:mongodb")
    "testFixturesImplementation"("org.testcontainers:junit-jupiter")
    "testFixturesImplementation"("io.kotest:kotest-runner-junit5:5.9.1")

    // [W1-01b 리뷰 ①] payment·commerce·facility-booking 3모듈이 각자 복제하던 standalone MockMvc
    // 테스트 하네스(GlobalExceptionHandler·ProblemDetailBuilder·PrincipalArgumentResolver)와 외부 계약
    // 테스트 하네스(ExternalContractSupport)를 여기 하나로 통합한다. 소비 모듈은 이미 자기 main
    // dependencies(spring-boot-starter-web/-security/-data-jpa 등)로 같은 타입을 testCompileClasspath에
    // 올려두고 있지만, common 자신의 testFixtures 소스셋을 컴파일하려면 이 모듈이 직접 선언해야 한다.
    "testFixturesImplementation"("org.springframework:spring-web")
    "testFixturesImplementation"("org.springframework:spring-webmvc")
    "testFixturesImplementation"("org.springframework:spring-tx")
    "testFixturesImplementation"("org.springframework:spring-orm")
    "testFixturesImplementation"("org.springframework.security:spring-security-core")
    "testFixturesImplementation"("jakarta.validation:jakarta.validation-api")
    "testFixturesImplementation"("jakarta.servlet:jakarta.servlet-api")
    "testFixturesImplementation"("org.slf4j:slf4j-api")
    "testFixturesImplementation"("com.squareup.okhttp3:mockwebserver")
}

kapt {
    correctErrorTypes = true
}
