// [W1-01a] social — community·post·message·recruitment.
// [W1-01d] 4 컨텍스트 254 main 파일(실측) + realtime Gateway 2(MessageBroadcastGatewayImpl·
// NoOpMessageBroadcastGateway) 이관 + 이 모듈이 실제로 쓰는 기술 의존만 선언한다.
// social->payment 4(ApplyRecruitmentCommand·ApplyRecruitmentUseCase·ApplyRecruitmentRequest·
// RecruitmentPaymentEventWorker) / commerce 1(GoodsProductGatewayImpl) / facility-booking
// 1(SlotInfoGatewayImpl) / platform 2(GuestExpiryScheduler·ReadCursorApiController의 CurrentUser,
// 실측 — 티켓 표는 1로 기재하나 CurrentUser 참조가 별도 1건 더 있다) 실측.
//
// [W1-01d 실측 — BOM은 enforcedPlatform] W1-01c에서 platform 모듈이 순수 platform() 사용 시
// 컴파일-런타임 스큐(spring-ai-bom 경로로 Spring Boot 3.5.14가 선택되고 bootstrap 런타임은
// io.spring.dependency-management 로 눌린 3.3.5)를 재현했다. 이 모듈은 spring-ai 의존이 없어
// 즉시 재현되지는 않으나, 향후 어떤 전이 의존이 더 높은 BOM 버전을 요구해도 constraint(순수
// platform())는 "높은 값 승리" 규칙을 따르므로 동일 스큐가 잠복한다 — enforcedPlatform으로 처음부터
// 방지한다. io.spring.dependency-management 플러그인은 쓰지 않는다(detekt tool 컨피규레이션까지
// BOM을 적용해 kotlin-stdlib를 1.9.23->1.9.25로 끌어올려 "compiled with Kotlin 1.9.23 but running
// with 1.9.25" 크래시를 유발한다 — platform/common 모듈에서 실측 재현된 이력과 동일 근거).
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
    implementation(project(":platform"))

    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // WebSocket / STOMP — ChatStompController(`@MessageMapping`), 실시간 채팅 진입점(BE-04,
    // chat.realtime.enabled 플래그로 조건부 활성화). WebSocketConfig 자체는 bootstrap 잔류(전역 설정).
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Kafka — RecruitmentPaymentEventWorker(event.payment.payment.v1 구독)
    implementation("org.springframework.kafka:spring-kafka")

    // QueryDSL — community·post·message·recruitment 11테이블(Community·CommunityBooking·
    // CommunityMember·Post·Comment·Room·RoomParticipant·RoomInvitation·Message·Application·
    // Recruitment) 소유.
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트 — standalone MockMvc(Community·Post·Recruitment ApiControllerTest 등).
    // ArchUnit 은 선언하지 않는다(전 모듈을 스캔하는 아키텍처 규칙은 bootstrap:archTest 소유). 대신
    // 이 모듈에는 프록시 canary(CreatePostUseCaseProxyTest)를 둬 all-open 적용 여부를 직접 단언한다 —
    // 티켓의 "모듈 로컬 경량 컨텍스트 스모크 1개".
    // GlobalExceptionHandler·fixedPrincipalResolver·withAuthenticatedPrincipal 는 common 의
    // testFixtures 로 통합 참조한다(W1-01b 리뷰 ① 선례 — social → bootstrap 은 모듈 의존 방향상
    // 성립하지 않는다).
    // spring-security-test 는 선언하지 않는다 — social 테스트 전수에 `@WithMockUser`·
    // `SecurityMockMvcConfigurers` 참조가 0건이고, `withAuthenticatedPrincipal` 이 쓰는
    // `SecurityContextHolder` 는 starter-security 경유 spring-security-core 로 충분하다.
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(testFixtures(project(":common")))
}

kapt {
    correctErrorTypes = true
}

// [W1-01d] 1.9.25 압력의 출처는 spring-boot-dependencies:$springBootVersion BOM 자신이다
// (enforcedPlatform 의 constraint 는 strict 이므로 이 BOM 이 관리하는 kotlin-stdlib/kotlin-reflect
// 버전이 무조건 선택된다 — platform 모듈 dependencyInsight 실측과 동일 근거, 상단 build.gradle.kts
// 주석 참고). 이 프로젝트 Kotlin Gradle 플러그인 버전(1.9.23, gradle.properties)과 어긋나면
// 컴파일러-런타임 스큐이므로 강제 고정한다.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }
}
