// [W1-01a] commerce — goods·ticketing. commerce->payment 11파일(실측).
// [W1-01b] goods(150)+ticketing(86) 소스 이관 + 이 모듈이 실제로 쓰는 기술 의존만 선언한다.
plugins {
    id("sportsapp.kotlin-conventions")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

val springBootVersion: String by project

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kafka — GoodsPaymentEventWorker·TicketingPaymentEventWorker(event.payment.payment.v1 구독)
    implementation("org.springframework.kafka:spring-kafka")

    // Spring Batch — SellerTypeBackfillJob (BE-11 products.seller_type 청크 백필)
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // Retry — 한정판 예약 보상(DropReservationCompensationRetryListener) 재시도
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Micrometer — DropReservationStoreImpl 관측 지표(MeterRegistry)
    implementation("io.micrometer:micrometer-core")

    // JSON Column — TicketOrder 등 snapshot data class 매핑(JsonStringType)
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트 — standalone MockMvc(Cart·TicketOrder·Event ApiControllerTest),
    // ArchUnit(DropReservationStoreTest), 실 Redis Testcontainers(DropReservationStoreImplTest —
    // common testFixtures 의 SharedTestContainers.redis 재사용).
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(testFixtures(project(":common")))
}

kapt {
    correctErrorTypes = true
}
