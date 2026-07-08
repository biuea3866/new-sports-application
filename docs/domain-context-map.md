# 도메인 · 바운디드 컨텍스트 맵

sports-application 백엔드(`backend`, 단일 모듈)의 도메인 구성과 도메인 간 연결, 바운디드 컨텍스트 경계를 정리한 문서입니다. 코드베이스 조사(2026-07-06 기준)를 근거로 작성했습니다.

## 구조 개요

- 단일 모듈 `backend`, 레이어 우선 패키지 구조 — `presentation` / `application` / `domain` / `infrastructure` 하위에 도메인 컨텍스트가 놓입니다.
- `domain/` 기준 **19개 도메인 + `common`**.
- 도메인 간 연결은 `RoutingDomainEventPublisher`가 `DomainEvent.topic` 유무로 **Kafka(topic 지정) vs Spring 내부 이벤트(topic null)** 로 분기 발행하는 이벤트 방식과, 동기 게이트웨이/도메인서비스 방식이 함께 쓰입니다.

## 도메인 목록

| 분류 | 도메인 | 핵심 엔티티/책임 |
|---|---|---|
| **커머스·주문** | `payment` | Payment — 결제 PG 연동, 주문 확정 콜백 허브 |
| | `booking` | Booking, Slot — 시설 예약 |
| | `goods` | Cart, GoodsOrder, Product, Stock, LimitedDrop — 굿즈 커머스 |
| | `ticketing` | Event, Seat, Ticket, TicketOrder — 티켓 판매 |
| **콘텐츠·소통** | `post` | Post, Comment — 게시판 |
| | `community` | (VO만) SportCategory·Role·Visibility — 커뮤니티 (구축 중) |
| | `message` | Message, Room, RoomParticipant — 실시간 채팅 |
| **알림·이벤트** | `notification` | Notification, PushToken — 통합 알림 발송 |
| | `alerting` | Alert — 이상징후 알림 + LLM 분석 |
| | `mcp` | McpAnomalyEvent, McpToken — MCP 이상탐지/토큰 |
| **플랫폼·운영** | `user` | User, Role, Permission, UserRole — 인증·인가 |
| | `partner` | Partner, PartnerApiKey — B2B 파트너 |
| | `operator` | OperatorInboxNotification — 운영자 인박스 |
| | `featureflag` | FeatureFlag, FeatureFlagAuditLog — 기능 플래그 |
| | `dashboard` | (집계) B2B 인사이트 대시보드 |
| **정보·부가** | `facility` | Facility — 시설 마스터(공공데이터 임포트) |
| | `weather` / `airquality` | Forecast / AirQuality — 외부 API 게이트웨이 (VO 중심) |
| | `featuredemo` / `image` | 데모 / 이미지 스토리지 |

## 도메인 간 연결 (구현 관점)

```mermaid
flowchart LR
    subgraph Commerce["커머스·주문"]
        payment
        booking
        goods
        ticketing
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
    payment -->|confirm/cancel 역콜백| booking
    payment -->|confirm/cancel 역콜백| goods
    payment -->|confirm/cancel 역콜백| ticketing

    payment -.->|Kafka payment.completed.v1| notification
    booking -.->|Kafka booking.confirmed.v1| notification
    ticketing -.->|Kafka ticket.issued.v1| notification
    alerting -.->|Spring AlertDeliveryReady| notification

    facility -->|SlotQueryGateway| booking
    partner -->|권한 조회| user
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
- `weather`·`airquality`·`operator`·`post`는 강결합 없는 독립 도메인, `featuredemo`·`image`는 부가 도메인(생략).

### 연결 방식 5종

**① Kafka 이벤트 (Layer 2 — 무관 도메인, 비동기)**

| 발행 도메인 | 토픽 | 구독 |
|---|---|---|
| payment | `payment.completed.v1` | notification (NotificationEventWorker) |
| booking | `booking.confirmed.v1` | notification |
| ticketing | `ticket.issued.v1` | notification |

**② Spring ApplicationEvent (Layer 1 — AFTER_COMMIT 비동기)**

| 발행 | 이벤트 | 구독 → UseCase |
|---|---|---|
| alerting | AlertProcessingRequested | alerting → ProcessAlert (LLM 분석) |
| alerting | AlertDeliveryReady | **notification** → SendRawNotification (크로스 도메인) |
| notification | NotificationDispatchRequested | notification → DispatchNotification |
| featureflag | FeatureFlagChanged | featureflag → PropagateFeatureFlagChange |
| goods | LimitedDropOversold | goods → 오버셀 처리 |
| mcp | McpAnomalyDetected | mcp → PersistAnomalyEvent |
| booking | BookingRefundRequested | booking → 환불 처리 |

**③ 동기 호출 — UseCase가 타 도메인 DomainService 주입**
- `booking`·`goods`·`ticketing` → **payment** (`paymentDomainService.createPending` / `findStatuses`)
- `dashboard` → **booking·facility·goods·ticketing·user** (읽기 집계)
- `partner` → **user**

**④ 역방향 콜백 — Gateway로 도메인 레이어 결합 차단 (infra가 브리지)**
- **payment → booking/goods/ticketing**: `OrderConfirmationGateway.confirm/cancel`. `OrderType`으로 분기해 `confirmBooking`/`markPaid`/`confirmOrder` 호출
- **facility → booking**: `SlotQueryGateway`로 예약 슬롯 조회

**⑤ 소프트 참조 (FK 없음, ID/컨텍스트 값만 보유)**
- `message` Room의 `contextType`(COMMUNITY, GOODS_PRODUCT) + `contextId` → community·goods 컨텍스트 연결
- 주문 3종·대부분 엔티티가 `userId: Long` 보유 → user 도메인 (객체 참조 아닌 ID)

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
    subgraph Social["Community & Chat · Supporting"]
        message
        community
        post
    end
    subgraph Catalog["Facility Catalog · Supporting"]
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
    payment -.->|"ACL 확정콜백"| booking
    payment -.->|"ACL 확정콜백"| goods
    payment -.->|"ACL 확정콜백"| ticketing

    payment -.->|"OHS/PL Kafka"| notification
    booking -.->|"OHS/PL Kafka"| notification
    ticketing -.->|"OHS/PL Kafka"| notification
    alerting -.->|"C/S Spring event"| notification

    facility -->|"ACL SlotQueryGateway"| booking
    partner -->|"C/S 권한조회"| user
    message -->|"소프트참조 contextId"| goods
    dashboard -->|"CF 읽기집계"| booking
    dashboard -->|"CF 읽기집계"| user
```

### 바운디드 컨텍스트 정의

| 컨텍스트 | 유형 | 포함 도메인 | 유비쿼터스 언어 |
|---|---|---|---|
| **Payment** | Core | payment | Payment, PG, 확정/취소 |
| **Reservation** | Core | booking | Booking, Slot |
| **Retail** | Core | goods | Cart, GoodsOrder, Product, Stock, LimitedDrop |
| **Ticketing** | Core | ticketing | Event, Seat, Ticket, TicketOrder |
| **Notification** | Supporting | notification | Notification, PushToken |
| **Monitoring & Anomaly** | Supporting | alerting, mcp | Alert, McpAnomalyEvent, McpToken |
| **Community & Chat** | Supporting | message, community, post | Room, Message, Post, Comment |
| **Facility Catalog** | Supporting | facility | Facility, Region |
| **Ops & Insight** | Supporting | dashboard, operator, featureflag | 집계, Inbox, Flag |
| **Identity & Access** | Generic | user, partner | User, Role, Permission, Partner |
| **Environment Info** | Generic | weather, airquality | Forecast, AirQuality |

> `featuredemo`·`image`는 Generic 서브도메인(데모·스토리지)이라 컨텍스트 맵에서 생략했습니다.

### 컨텍스트 매핑 패턴 범례

| 약어 | 패턴 | 이 프로젝트의 구현 |
|---|---|---|
| **C/S** | Customer/Supplier | Reservation·Retail·Ticketing이 Payment에 `createPending` 동기 요청 (upstream=Payment) |
| **ACL** | Anticorruption Layer | `OrderConfirmationGateway`·`SlotQueryGateway` — infra 게이트웨이가 타 컨텍스트 도메인을 격리 호출, 도메인 레이어 결합 차단 |
| **OHS/PL** | Open Host Service + Published Language | Kafka 토픽(`payment.completed.v1` 등) — Payment/Reservation/Ticketing 발행, Notification 구독 |
| **CF** | Conformist | Dashboard가 각 Core 컨텍스트 모델을 그대로 읽어 집계 (읽기 전용) |

## 핵심 관찰

- **Payment가 Core의 공급자 허브**: 3개 판매 컨텍스트(Reservation/Retail/Ticketing)가 Payment의 Customer이고, 확정 콜백은 ACL(`OrderConfirmationGateway`)로 역방향 격리. Payment 도메인 레이어는 판매 컨텍스트를 import하지 않습니다.
- **Notification은 Downstream Conformist**: 상류 4개 컨텍스트의 Published Language(Kafka 이벤트 + Spring 이벤트)를 구독만 합니다.
- **Identity & Access가 사실상 Shared Kernel**: 모든 컨텍스트가 `userId: Long`을 값으로 보유 — 객체 참조가 아닌 ID 소프트 참조라 경계는 유지됩니다.
- **Community & Chat은 진화 중**: `message`의 `contextType`(COMMUNITY/GOODS_PRODUCT)으로 Retail·Community에 느슨히 연결, `community`는 아직 VO만 존재하는 미완성 컨텍스트입니다.

## Document History

| 날짜 | 변경 내용 |
|---|---|
| 2026-07-06 | 최초 작성 — 코드베이스 조사 기반 도메인·컨텍스트 맵 |
