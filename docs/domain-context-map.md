# 도메인 · 바운디드 컨텍스트 맵

sports-application 백엔드(`backend`, 단일 모듈)의 도메인 구성과 도메인 간 연결, 바운디드 컨텍스트 경계를 정리한 문서입니다. 코드베이스 조사(2026-07-06 최초 작성, 2026-07-07 BE-22 FR-8 정합 갱신, **2026-07-30 0단계 결합 해소 후 코드 실측 기준 전면 정정**)를 근거로 작성했습니다.

## 구조 개요

> **현재 상태와 목표 구조**: 이 문서는 **현재 코드의 사실**만 기술합니다. 지금은 단일 모듈·단일 배포이며, 목표 구조(서비스 물리 분리 범위·경계·순서)는 여기서 단정하지 않습니다 — `아키텍트/20260728-msa-물리분리-실행설계.md`(레포 밖 설계 문서)를 참조하세요.

- 단일 모듈 `backend`, 레이어 우선 패키지 구조 — `presentation` / `application` / `domain` / `infrastructure` 하위에 도메인 컨텍스트가 놓입니다.
- `domain/` 기준 **20개 도메인 + `common`**(recruitment 신규).
- 도메인 간 연결은 `RoutingDomainEventPublisher`가 `DomainEvent.topic` 유무로 **Kafka(topic 지정) vs Spring 내부 이벤트(topic null)** 로 분기 발행하는 이벤트 방식과, 동기 게이트웨이/도메인서비스 방식이 함께 쓰입니다.

## 도메인 목록

| 분류 | 도메인 | 핵심 엔티티/책임 |
|---|---|---|
| **커머스·주문** | `payment` | Payment — 결제 PG 연동. 확정은 **콜백이 아니라 이벤트** — 발행만 하고 주문 컨텍스트를 모른다(역참조 0건) |
| | `booking` | Booking, Slot — 시설 예약 |
| | `goods` | Cart, GoodsOrder, Product, Stock, LimitedDrop — 굿즈 커머스 |
| | `ticketing` | Event, Seat, Ticket, TicketOrder — 티켓 판매 |
| | `recruitment` | Recruitment, Application — 모집 개설·정원 내 신청, 단계별 취소수수료(`TieredCancellationPolicy`). payment Customer 관계(`OrderType.RECRUITMENT` 배선 완료 — `ApplyRecruitmentUseCase`·`ConfirmRecruitmentPaymentUseCase`·`RecruitmentPaymentEventWorker`), community ID 참조(`communityId`, nullable) |
| **콘텐츠·소통** | `post` | Post, Comment — 게시판 |
| | `community` | Community, CommunityMember, CommunityBooking — 멤버십 커뮤니티(`CommunityVisibility` 개방/폐쇄 겸용, `CommunityRole` HOST/MEMBER, `CommunityChatIntegrationEventWorker`로 채팅 자동연동) |
| | `message` | Message, Room, RoomParticipant — 실시간 채팅 |
| **알림·이벤트** | `notification` | Notification, PushToken — 통합 알림 발송 |
| | `alerting` | Alert — 이상징후 알림 + LLM 분석 |
| | `mcp` | McpAnomalyEvent, McpToken — MCP 이상탐지/토큰 |
| **플랫폼·운영** | `user` | User, Role, **Permission**, UserRole, RolePermission — 인증·인가. `permissions` 테이블 소유권이 공유 커널(`domain/common`)에서 user로 이관돼 공유 커널은 물리 테이블을 갖지 않는다 |
| | `partner` | Partner, PartnerApiKey — B2B 파트너. 연동 계정 프로비저닝은 컨텍스트별 로컬 트랜잭션 3단 + 보상(SAGA)으로 분리 — 한 `@Transactional` 안의 크로스 컨텍스트 쓰기 없음 |
| | `operator` | OperatorInboxNotification — 운영자 인박스 |
| | `featureflag` | FeatureFlag, FeatureFlagAuditLog — 기능 플래그 |
| | `virtualqueue` | 가상 대기열 — 인그레스 입장 제어(HMAC 입장토큰). Redis 전용, 코어 역참조 0건. `EntryTokenGuard`(common)로만 노출 |
| | `dashboard` | (집계) B2B 인사이트 대시보드 |
| **정보·부가** | `facility` | Entity: Facility, Program — 시설 마스터(공공데이터 임포트) + 시설상품. VO: OperatingHours·Holiday 등 |
| | `weather` / `airquality` | Forecast / AirQuality — 외부 API 게이트웨이 (VO 중심) |
| | `featuredemo` / `image` | 데모 / 이미지 스토리지 |
| **조회 조합(application 전용)** | `catalog` / `order` | 도메인 레이어 없는 **읽기 조합 파사드**. 코어 DomainService를 병렬 fan-out 하고 도메인당 300ms 타임아웃 + 실패 도메인 부분 저하(`failedDomains`)로 응답한다 |

## 도메인 간 연결 (구현 관점)

```mermaid
flowchart LR
    subgraph Commerce["커머스·주문"]
        payment
        booking
        goods
        ticketing
        recruitment
    end
    subgraph Alert["알림·이벤트"]
        notification
        alerting
        mcp
    end
    subgraph Content["콘텐츠·소통"]
        message
        community
        post
    end
    subgraph Platform["플랫폼·운영"]
        user
        partner
        dashboard
        operator
    end
    subgraph Info["정보·부가"]
        facility
        weather
        airquality
    end

    booking -->|createPending 동기| payment
    goods -->|createPending 동기| payment
    ticketing -->|createPending 동기| payment
    recruitment -->|createPending 동기| payment

    payment -.->|확정구독| booking
    payment -.->|확정구독| goods
    payment -.->|확정구독| ticketing
    payment -.->|확정구독| recruitment
    payment -.->|Kafka event.payment.payment.v1| notification
    booking -.->|Kafka event.booking.booking.v1| notification
    ticketing -.->|Kafka event.ticketing.ticket.v1| notification
    alerting -.->|Spring AlertDeliveryReady| notification

    facility -->|SlotQueryGateway 공급자경유| booking
    partner -->|연동계정 SAGA| user
    message -->|contextType| community
    message -->|contextType| goods

    dashboard -->|집계 읽기| booking
    dashboard -->|집계 읽기| goods
    dashboard -->|집계 읽기| ticketing
    dashboard -->|집계 읽기| facility
    dashboard -->|집계 읽기| user
```

- 실선 `→` : 동기 호출 (DomainService 주입 / Gateway)
- 점선 `-.->` : 비동기 이벤트 (Kafka Layer 2 / Spring ApplicationEvent Layer 1)
- 이 그림에 엣지가 없는 노드는 `weather`·`airquality`·`operator`·`post`·`mcp` 5개입니다. 이 중 **`weather`·`airquality`·`operator` 만 실제로 독립**이고, `post`는 community 에 동기 결합(인가 5건, ③ 참조), `mcp`는 `McpPermissionGateway`(mcp→user, ④ 참조)를 갖습니다. 이 그림이 담지 않은 도메인은 `featureflag`·`virtualqueue`·`featuredemo`·`image`와 파사드 `catalog`·`order` 입니다(각각 분류표·②·③에 기재).
- **엣지 생략 기준**: 이 그림은 가독성을 위해 컨텍스트 간 흐름을 **선별 표기**합니다(결제 개시·확정 팬아웃·알림·집계 등). 동기 호출·ACL 게이트웨이·소프트 참조의 **전수 목록은 아래 ③·④·⑤ 표**가 담습니다 — 그림에 없는 결합이 표에는 있습니다.
- `event.payment.payment.v1` 확정구독 점선 4개가 **제거된 동기 콜백 허브를 대체한 팬아웃**입니다 — payment는 발행만 하고 각 주문 컨텍스트가 자기 `*PaymentEventWorker`로 확정합니다(컨슈머 그룹은 notification 포함 5개, 아래 ① 표 참조).
- `recruitment`는 payment 연동(`createPending` + 확정 구독)과 community ID 참조가 모두 **배선 완료**돼 이 그림에 포함했습니다.
- 이 그림은 컨텍스트 수가 많아 mermaid 가이드의 노드 15개 권장을 넘습니다(18개). 컨텍스트 맵의 성격상 전체 조망이 목적이라 `subgraph` 그룹핑으로 가독성을 확보했습니다.

### 연결 방식 5종

**① Kafka 이벤트 (Layer 2 — 무관 도메인, 비동기)**

| 발행 도메인 | 토픽 | 구독 |
|---|---|---|
| payment | `event.payment.payment.v1` | **5 groupId 팬아웃** — `booking-payment`·`goods-payment`·`ticketing-payment`·`recruitment-payment`·`notification-payment` |
| booking | `event.booking.booking.v1` | `notification-booking` |
| ticketing | `event.ticketing.ticket.v1` | `notification-ticketing` |

**② Spring ApplicationEvent (Layer 1 — AFTER_COMMIT 비동기)**

| 발행 | 이벤트 | 구독 처리 |
|---|---|---|
| alerting | AlertProcessingRequested | alerting → ProcessAlert (LLM 분석) |
| alerting | AlertDeliveryReady | **notification** → SendRawNotification (크로스 도메인) |
| notification | NotificationDispatchRequested | notification → DispatchNotification |
| featureflag | FeatureFlagChanged | featureflag → PropagateFeatureFlagChange |
| goods | LimitedDropOversold | goods → `LimitedDropOversoldEventWorker` (UseCase·Gateway 미경유, 지표 카운터만 증가) |
| mcp | McpAnomalyDetected | mcp → PersistAnomalyEvent |
| booking | BookingRefundRequested | booking → `BookingRefundEventWorker` → `PaymentRefundGateway` (UseCase 미경유, 게이트웨이 직접 주입) |
| community | CommunityCreatedEvent / CommunityMemberJoinedEvent / CommunityMemberLeftEvent | **message** → ProvisionContextRoom / JoinContextRoom / LeaveContextRoom (`CommunityChatIntegrationEventWorker`, 크로스 도메인) |
| recruitment | ApplicationRefundRequestedEvent | recruitment → `RecruitmentRefundEventWorker` → `RecruitmentRefundGateway.requestRefund` (UseCase 미경유, 게이트웨이 직접 주입) |
| message | MessageSentEvent | message → BroadcastMessage (`chat.realtime.enabled` 플래그 게이트) |
| message | RoomReadEvent | message → BroadcastRead (동일 플래그 게이트) |

**③ 동기 호출 — application 레이어가 타 컨텍스트 DomainService 주입 (전수, 2026-07-30 실측)**

소비자 `application/{context}/**` 에서 `domain.{other}.service.*` 를 생성자 주입하는 쌍을 전수 스캔한 결과입니다(괄호 = 주입 파일 수). `domain.common` 은 공유 커널이라 제외합니다.

| 소비자 → 공급자 | 주입 지점 | 성격 |
|---|---|---|
| booking → payment (3) / goods → payment (1) / ticketing → payment (1) / recruitment → payment (1) | 6 | 결제 개시 (`createPending`·`findStatuses`) — Customer/Supplier |
| post → community (5) | 5 | 멤버십·가시성 인가 — **쓰기 2건**(`requireActiveMember`) + **읽기 3건**(`getCommunity` 가시성 재판정, `@Transactional(readOnly = true)`). 게시글 상세·목록이라는 **핫 읽기 경로가 요청마다 동기 호출**한다 |
| community → message (3) | 3 | 채팅방 표시정보 조회(`RoomContextQueryService`) — **3건 전부 읽기 경로**(`@Transactional(readOnly = true)`). 커뮤니티 상세·목록·내 목록이 매 요청 동기 호출한다 |
| recruitment → community (2) | 2 | 가시성 인가 — **2건 전부 읽기 경로**(`@Transactional(readOnly = true)` + `getCommunity`). 모집 상세·목록이 post 와 동일 구조의 핫 읽기 경로다. Entity 참조는 없고 `communityId` 로만 연결 |
| facility → booking (1) | 1 | `CreateProgramSessionUseCase` — Program 회차를 Slot 으로 생성. **ACL 게이트웨이가 아니라 `SlotDomainService` 직접 주입** |
| dashboard → booking(2)·goods(2)·ticketing(2)·facility(1)·user(1) | 8 (2개 파일) | 읽기 집계 (R3 화이트리스트, Conformist) |
| catalog → facility·goods·recruitment·ticketing (각 1) | 4 (1개 파일) | 읽기 조합 파사드 |
| order → booking·goods·recruitment·ticketing (각 1) | 4 (1개 파일) | 읽기 조합 파사드 |
| partner → user (1) | 1 | 연동 계정 프로비저닝 **쓰기** (SAGA, R3 화이트리스트) |

> **읽기 경로 동기 결합 16지점** — post→community 3 / recruitment→community 2 / community→message 3 / dashboard→코어 8. 전부 `@Transactional(readOnly = true)` 로, 상세·목록·대시보드 조회가 매 요청 타 컨텍스트를 동기 호출합니다. 서비스 분리 시 원격 홉이 조회 지연에 직결되는 지점입니다. 파사드 8건(catalog·order)은 **이미 300ms 타임아웃 + 부분 저하 설계가 있어** 성격이 다릅니다.
>
> **presentation 레이어의 교차 컨텍스트 결합** — 이 표는 application 레이어 스코프라 아래 두 건이 빠집니다. ArchUnit R3 도 `domain`·`application` 만 스캔해 이 경로를 보지 않습니다.
> - `presentation/mcp/**` 툴 **12파일**이 booking·ticketing·goods·facility·notification·dashboard **코어 6개 컨텍스트의 UseCase** 를 직접 주입합니다(import: booking 12·ticketing 5·goods 4·facility 4·notification 2·dashboard 2). 서브시스템 mcp 의 최대 결합 지점이며, `McpPermissionGateway`(④)보다 훨씬 큽니다.
> - `presentation/message/scheduler/GuestExpiryScheduler` 가 notification `SendRawNotificationUseCase` 를 주입합니다.
>
> `infrastructure/security/**` 의 인증 필터도 타 컨텍스트 DomainService 를 주입하지만(`McpTokenAuthenticationFilter` → user `PermissionDomainService`, `PartnerApiKeyAuthenticationFilter` → partner·user), 바운디드 컨텍스트가 아니라 **횡단 인증 레이어**라 이 표의 대상이 아닙니다. 전자는 자기 컨텍스트 Repository(`McpTokenRepository` 등)도 직접 주입합니다. application 레이어의 교차 컨텍스트 Repository 주입은 실측 **0건**입니다.
>
> `facility → booking` 은 이 표(직접 주입)와 아래 ④(ACL `SlotQueryGateway`) **양쪽에 존재**합니다 — 조회는 게이트웨이 경유, Program 회차 생성은 DomainService 직접 주입으로 경로가 갈립니다.

**④ 확정 흐름과 소비자 ACL 게이트웨이 (infra가 브리지)**
- **payment → 주문 4종**: `OrderConfirmationGateway`류 동기 디스패치 허브는 **존재하지 않습니다**(제거됨). payment는 `event.payment.payment.v1`에 발행만 하고, 각 주문 컨텍스트(booking·goods·ticketing·recruitment)가 **자기 `*PaymentEventWorker`로 확정**합니다 — 공용 컨텍스트가 주문 컨텍스트를 역참조하지 않습니다.
- **소비자 ACL 게이트웨이 7종**: 소비자 domain이 interface를 소유하고, infrastructure 구현체가 **공급자의 공개 행위 계약(DomainService)** 을 경유합니다. 공급자 Repository(=테이블)를 직접 주입하지 않습니다.
  `FacilityOwnershipGateway`·`FacilityScheduleGateway`(booking→facility) / `SlotInfoGateway`(community→booking) / `SlotQueryGateway`(facility→booking) / `GoodsProductGateway`(message→goods) / `RecipientContactGateway`(notification→user) / `McpPermissionGateway`(mcp→user)

**⑤ 소프트 참조 (FK 없음, ID/컨텍스트 값만 보유)**
- `message` Room의 `contextType`(COMMUNITY, GOODS_PRODUCT) + `contextId` → community·goods 컨텍스트 연결
- `recruitment` Recruitment의 `communityId`(nullable) → community 컨텍스트 연결 (ID만 보유, community Entity 직접 참조 없음)
- 주문 4종·대부분 엔티티가 `userId: Long` 보유 → user 도메인 (객체 참조 아닌 ID)

## 바운디드 컨텍스트 맵 (DDD 관점)

```mermaid
flowchart LR
    subgraph IAM["Identity & Access · Generic"]
        user
        partner
    end
    subgraph Reservation["Reservation · Core"]
        booking
    end
    subgraph Retail["Retail · Core"]
        goods
    end
    subgraph Ticketing["Ticketing · Core"]
        ticketing
    end
    subgraph Recruit["Recruitment · Core"]
        recruitment
    end
    subgraph Payment["Payment · Core"]
        payment
    end
    subgraph Notify["Notification · Supporting"]
        notification
    end
    subgraph Anomaly["Monitoring & Anomaly · Supporting"]
        alerting
        mcp
    end
    subgraph Social["Community & Chat · Core"]
        message
        community
        post
    end
    subgraph FacilityCatalog["Facility Catalog · Supporting"]
        facility
    end
    subgraph Env["Environment Info · Generic"]
        weather
        airquality
    end
    subgraph Ops["Ops & Insight · Supporting"]
        dashboard
        operator
        featureflag
    end

    booking -->|"C/S 결제요청"| payment
    goods -->|"C/S 결제요청"| payment
    ticketing -->|"C/S 결제요청"| payment
    payment -.->|"OHS/PL 확정"| booking
    payment -.->|"OHS/PL 확정"| goods
    payment -.->|"OHS/PL 확정"| ticketing
    payment -.->|"OHS/PL 확정"| recruitment
    payment -.->|"OHS/PL Kafka"| notification
    booking -.->|"OHS/PL Kafka"| notification
    ticketing -.->|"OHS/PL Kafka"| notification
    alerting -.->|"C/S Spring event"| notification

    facility -->|"ACL SlotQueryGateway"| booking
    recruitment -->|"C/S 결제요청"| payment
    partner -->|"C/S 연동계정 SAGA"| user
    message -->|"소프트참조 contextId"| goods
    dashboard -->|"CF 읽기집계"| booking
    dashboard -->|"CF 읽기집계"| user
```

> `recruitment`는 payment 연동이 배선 완료된 Core 컨텍스트라 결제 요청 엣지를 표기했습니다. 이 DDD 관점 그림은 컨텍스트 수가 많아 mermaid 가이드의 노드 15개 권장을 넘습니다(19개) — 컨텍스트 맵의 성격상 전체 조망이 목적이므로 `subgraph` 그룹핑으로 가독성을 확보했습니다. 상세는 아래 "바운디드 컨텍스트 정의" 표를 보세요.

### 도메인 분류 (ADR-001 / `DomainClassification`)

`domain/` 하위 20개 컨텍스트(+`common` 공유 커널)가 모두 분류돼 있습니다. 이 상수가 ArchUnit R3 스캔 대상을 **직접 구동**하므로, 상수에 도메인을 추가하면 스캔 대상이 함께 늘어납니다.

| 분류 | 도메인 | 수 |
|---|---|---|
| 코어(거래) | booking, facility, goods, payment, ticketing, user, post, message, community, recruitment | 10 |
| 지원(Supporting) | notification, operator, weather, alerting, featureflag, virtualqueue, partner, airquality, featuredemo | 9 |
| 서브시스템 | mcp | 1 |
| 공유 커널 | common (분류 대상 아님, 물리 테이블 미소유) | — |
| 조회·유틸 | dashboard, image, catalog, order (domain 레이어 없음) | — |

`partner`는 `application.partner → domain.user`(연동 계정 프로비저닝)가 ADR-002 rule #4의 사전 등록 예외라, 일반 R3 루프에서 제외되고 전용 화이트리스트 테스트가 담당합니다.

### 바운디드 컨텍스트 정의

| 컨텍스트 | 유형 | 포함 도메인 | 유비쿼터스 언어 |
|---|---|---|---|
| **Payment** | Core | payment | Payment, PG, 확정/취소 |
| **Reservation** | Core | booking | Booking, Slot |
| **Retail** | Core | goods | Cart, GoodsOrder, Product, Stock, LimitedDrop |
| **Ticketing** | Core | ticketing | Event, Seat, Ticket, TicketOrder |
| **Recruitment** | Core | recruitment | Recruitment, Application, CancellationPolicy |
| **Notification** | Supporting | notification | Notification, PushToken |
| **Monitoring & Anomaly** | Supporting | alerting, mcp | Alert, McpAnomalyEvent, McpToken |
| **Community & Chat** | Core | message, community, post | Room, Message, Community, CommunityMember, Post, Comment |
| **Facility Catalog** | Supporting | facility | Facility, Region |
| **Ops & Insight** | Supporting | dashboard, operator, featureflag | 집계, Inbox, Flag |
| **Identity & Access** | Generic | user, partner | User, Role, Permission, Partner |
| **Environment Info** | Generic | weather, airquality | Forecast, AirQuality |
| **Ingress Admission** | Generic | virtualqueue | 대기열, 입장토큰(HMAC). Redis 전용이라 소유 테이블 없음. `EntryTokenGuard`(공유 커널)로만 노출되고 코어 역참조 0건 |

> `featuredemo`·`image`는 Generic 서브도메인(데모·스토리지)이라 컨텍스트 맵에서 생략했습니다.
> **Community & Chat**은 기존 Supporting에서 **Core**로 정정했습니다 — `message`·`post`는 `DomainClassification.core`(`SupportToCoreDependencyRulesTest.kt:21`)에 이미 등록돼 있고, `community`도 같은 상수에 등록돼 있습니다. 세 도메인 모두 사용자 대상 핵심 자산(채팅방·게시글·멤버십)을 소유해 Core 분류가 실제 코드 기준과 일치합니다.
> **Recruitment**는 payment의 Customer(결제 요청 주체)이자 community를 ID로만 참조하는 Core 컨텍스트입니다. `DomainClassification.core` 등록과 결제 연동(`OrderType.RECRUITMENT`) 모두 **완료** 상태입니다.

### 컨텍스트 매핑 패턴 범례

| 약어 | 패턴 | 이 프로젝트의 구현 |
|---|---|---|
| **C/S** | Customer/Supplier | Reservation·Retail·Ticketing·**Recruitment** 4개 컨텍스트가 Payment에 `createPending` 동기 요청 (upstream=Payment) |
| **ACL** | Anticorruption Layer | 소비자 domain이 Gateway interface를 소유하고 infra 구현체가 **공급자 DomainService**를 경유 — 도메인 레이어 결합과 **스키마 결합**을 함께 차단합니다. 7종: `FacilityOwnershipGateway`·`FacilityScheduleGateway`·`SlotInfoGateway`·`SlotQueryGateway`·`GoodsProductGateway`·`RecipientContactGateway`·`McpPermissionGateway`. `RecruitmentRefundGateway`·booking 의 `PaymentRefundGateway`는 성격이 다릅니다 — 둘 다 PG sandbox 확보 전까지 쓰는 **의존 0의 stub**(후자는 `@Profile("!prod")`)이라 공급자 경유가 아닙니다 |
| **OHS/PL** | Open Host Service + Published Language | Kafka 토픽 `event.{domain}.{sub-domain}.v1` 3종 — Payment/Reservation/Ticketing 발행. `event.payment.payment.v1`은 5개 컨슈머 그룹이 팬아웃 구독 |
| **CF** | Conformist | Dashboard가 각 Core 컨텍스트 모델을 그대로 읽어 집계 (읽기 전용) |

## 핵심 관찰

- **Payment가 Core의 공급자 허브**: 4개 판매 컨텍스트(Reservation/Retail/Ticketing/Recruitment)가 Payment의 Customer입니다. 확정은 **콜백이 아니라 이벤트**입니다 — payment는 `event.payment.payment.v1`에 발행만 하고 각 주문 컨텍스트가 자기 EventWorker로 확정합니다. Payment 도메인 레이어는 판매 컨텍스트를 import하지 않고, 역참조 디스패치 허브도 없습니다.
- **Notification은 Downstream Conformist**: 상류 4개 컨텍스트의 Published Language(Kafka 이벤트 + Spring 이벤트)를 구독만 합니다.
- **Identity & Access가 사실상 Shared Kernel**: 모든 컨텍스트가 `userId: Long`을 값으로 보유 — 객체 참조가 아닌 ID 소프트 참조라 경계는 유지됩니다.
- **Community & Chat은 완성된 Core 컨텍스트**: `community`는 Community·CommunityMember aggregate(Entity 2·DomainService 12메서드·UseCase 10·Repository 3·이벤트 3·예외 6·VO 3, 테스트 22 — `CommunityBooking` 계열 제외 기준)로 구현이 끝났고, `CommunityChatIntegrationEventWorker`(Layer 1, AFTER_COMMIT)가 커뮤니티 생성·가입·탈퇴 이벤트를 구독해 `message` 전용 그룹채팅방을 자동 provision·가입·퇴장시킵니다. `message`의 `contextType`(COMMUNITY/GOODS_PRODUCT)으로 Retail·Community에 느슨히 연결되는 소프트 참조 구조는 유지됩니다.
- **Recruitment는 post/community와 별도 수명주기를 가진 신규 Core 컨텍스트**: 모집 정원 상태 머신·신청 라이프사이클·단계별 취소 수수료(`TieredCancellationPolicy`)를 자체 소유하며, community는 `communityId`(nullable) ID로만 참조합니다(Entity 직접 참조 없음). post 도메인 자체와는 아직 참조 관계가 없고, 모집을 post PostType으로 흡수하지 않기로 한 설계 결정(게시판 오염 방지)의 결과입니다. payment와는 Customer 관계로 **편입 완료**(`OrderType.RECRUITMENT`)입니다.

## Document History

| 날짜 | 변경 내용 |
|---|---|
| 2026-07-30 | PH0-07 0단계 정합 갱신 — 코드 실측 기준 전면 정정. ① `virtualqueue`·`catalog`·`order` 신규 기재 ② recruitment 결제·community 연동을 "예정"에서 **배선 완료**로 정정 ③ 제거된 `OrderConfirmationGateway` 구조 서술 삭제(payment는 발행만, 각 주문 컨텍스트가 자기 EventWorker로 확정) ④ 토픽명 3종을 `event.{domain}.{sub-domain}.v1` 실제 값으로 정정 + `event.payment.payment.v1`의 5 groupId 팬아웃 반영 ⑤ 소비자 ACL 게이트웨이 7종이 공급자 Repository가 아니라 **공급자 DomainService를 경유**함을 반영 ⑥ `permissions` 소유권이 공유 커널에서 user로 이관돼 공유 커널이 물리 테이블을 갖지 않음을 명시 ⑦ partner 연동 계정이 SAGA + 보상 구조임을 명시 ⑧ 도메인 분류표(20/20) 신설 ⑨ 목표 구조는 단정하지 않고 물리분리 실행설계 문서로 연결 |
| 2026-07-07 | BE-22 FR-8 문서 정합 — community stale 서술(VO만·미완성) 정정, message/post/community 분류를 `DomainClassification.core` 기준으로 Core 정합, recruitment 신규 Core 도메인 행 추가 |
| 2026-07-06 | 최초 작성 — 코드베이스 조사 기반 도메인·컨텍스트 맵 |
