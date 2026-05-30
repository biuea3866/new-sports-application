# Mock PG (Payment Gateway) Server

실제 PG 계약 없이 결제 플로우를 E2E 검증하기 위한 6사 모킹 독립 서버입니다.

## 지원 Provider

| provider | 설명 |
|---|---|
| `kakao` | 카카오페이 모킹 |
| `toss` | 토스페이먼츠 모킹 |
| `naver` | 네이버페이 모킹 |
| `danal` | 다날 모킹 |
| `bank_transfer` | 무통장 입금 (가상계좌) 모킹 |
| `card` | 일반 카드 결제 모킹 |

---

## 실행

### 직접 실행

```bash
cd mock-pg
npm install
node server.js
# → http://localhost:9090
```

### Docker Compose

```bash
docker-compose up mock-pg
```

### 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `PORT` | `9090` | 서버 포트 |
| `BE_WEBHOOK_URL` | `http://localhost:8080/payments/webhook` | BE 결제 확정 콜백 URL |

---

## API 엔드포인트

### `POST /pg/:provider/ready`

거래 토큰(tid) 생성, 결제창 URL 반환.

**요청 Body (공통)**

```json
{
  "partner_order_id": "ORDER-001",
  "partner_user_id":  "USER-001",
  "item_name":        "수영장 이용권",
  "amount":           30000,
  "return_url":       "http://your-app/payment/result",
  "fail_url":         "http://your-app/payment/fail"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `partner_order_id` | Y | BE 주문 ID |
| `amount` | Y | 결제 금액 (원) |
| `partner_user_id` | N | 사용자 ID |
| `item_name` | N | 상품명 (기본: "상품") |
| `return_url` | N | 결제 완료 후 리다이렉트 URL |
| `fail_url` | N | 취소/실패 후 리다이렉트 URL |

**응답 예시 — kakao**
```json
{
  "tid": "MOCK_KAKAO_abc123...",
  "next_redirect_mobile_url": "http://localhost:9090/pg/kakao/checkout?tid=MOCK_KAKAO_abc123",
  "next_redirect_pc_url":     "http://localhost:9090/pg/kakao/checkout?tid=MOCK_KAKAO_abc123",
  "created_at": "2026-05-30T00:00:00.000Z"
}
```

**응답 예시 — toss**
```json
{
  "paymentKey":   "MOCK_TOSS_abc123...",
  "checkoutPage": "http://localhost:9090/pg/toss/checkout?tid=MOCK_TOSS_abc123",
  "status":       "READY",
  "requestedAt":  "2026-05-30T00:00:00.000Z"
}
```

**응답 예시 — bank_transfer**
```json
{
  "tid":    "MOCK_BANK_TRANSFER_abc123...",
  "status": "PENDING_DEPOSIT",
  "checkoutPage": "http://localhost:9090/pg/bank_transfer/checkout?tid=...",
  "virtualAccount": {
    "bankName":      "신한은행",
    "accountNumber": "1234567890123",
    "holderName":    "스포츠앱(주)",
    "dueDate":       "2026-05-31T00:00:00.000Z"
  }
}
```

---

### `GET /pg/:provider/checkout?tid=...`

간이 HTML 결제창 반환. 모바일 웹뷰·브라우저가 띄웁니다.

- 승인 버튼 → `POST /pg/:provider/approve` 호출 후 `return_url` 리다이렉트
- 취소 버튼 → `POST /pg/:provider/cancel` 호출 후 `fail_url` 리다이렉트

---

### `POST /pg/:provider/approve`

승인 처리. BE webhook 전송.

**요청 Body**
```json
{ "tid": "MOCK_TOSS_abc123..." }
```

**응답 예시 — toss**
```json
{
  "paymentKey":      "MOCK_TOSS_abc123...",
  "orderId":         "ORDER-001",
  "orderName":       "수영장 이용권",
  "status":          "DONE",
  "method":          "카드",
  "totalAmount":     30000,
  "approvedAt":      "2026-05-30T00:00:00.000Z",
  "card": { "amount": 30000, "company": "국민", "approveNo": "MOCK..." }
}
```

**멱등성**: 동일 tid 재호출 시 `{ ...approveResponse, "idempotent": true }` 반환, webhook 재전송 없음.

---

### `POST /pg/:provider/cancel`

취소 처리. BE 취소 콜백 전송.

**요청 Body**
```json
{ "tid": "MOCK_TOSS_abc123..." }
```

**응답 예시 — toss**
```json
{
  "paymentKey": "MOCK_TOSS_abc123...",
  "status":     "CANCELED",
  "cancels": [{ "cancelAmount": 30000, "canceledAt": "2026-05-30T00:00:00.000Z" }]
}
```

**멱등성**: 동일 tid 재호출 시 `{ ...cancelResponse, "idempotent": true }` 반환.

---

## BE Webhook 페이로드

BE_WEBHOOK_URL로 전송되는 콜백 공통 스키마입니다.

```json
{
  "eventType":       "PAYMENT_APPROVED",
  "provider":        "toss",
  "tid":             "MOCK_TOSS_abc123...",
  "orderId":         "ORDER-001",
  "amount":          30000,
  "status":          "APPROVED",
  "timestamp":       "2026-05-30T00:00:00.000Z",
  "providerPayload": { /* provider별 원본 approve/cancel 응답 */ }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `eventType` | string | `PAYMENT_APPROVED` 또는 `PAYMENT_CANCELED` |
| `provider` | string | 결제 provider |
| `tid` | string | 거래 토큰 |
| `orderId` | string | BE 주문 ID (`partner_order_id`) |
| `amount` | number | 결제 금액 |
| `status` | string | 최종 거래 상태 |
| `timestamp` | ISO8601 | 이벤트 발생 시각 |
| `providerPayload` | object | provider별 원본 응답 |

헤더: `X-Mock-PG-Signature: mock_sig_{tid}` (서명 검증 모킹)

---

## 결제 플로우

```
BE                  Mobile WebView          Mock PG
 |                       |                     |
 |-- POST /ready ------->|                     |
 |                       |-- GET /checkout --->|
 |                       |<-- HTML 결제창 ------|
 |                       |-- [승인 클릭] ------>|
 |                       |-- POST /approve --->|
 |<-- POST webhook -------|<--------------------|
 |                       |<-- redirect ---------|
```

### 무통장 플로우

```
BE                  Mobile WebView          Mock PG
 |                       |                     |
 |-- POST /ready ------->|                     |
 |<-- { status: PENDING_DEPOSIT, virtualAccount } --|
 |                       |                     |
 | (입금 대기)              |                     |
 |                       |                     |
 |-- POST /approve -------------------------------->|  (입금 확인 모킹)
 |<-- POST webhook (PAYMENT_APPROVED) -------------|
```

---

## 테스트

```bash
cd mock-pg
npm install
npm test
```

테스트 항목:
- ready → approve 플로우 (toss)
- approve 멱등성 — 동일 tid 2회 호출 시 webhook 1회만
- bank_transfer ready 응답에 가상계좌 포함 + PENDING_DEPOSIT 상태
- cancel 멱등성
- 알 수 없는 provider → 404

---

## Provider별 응답 필드 차이

| provider | ready 키 | approve status | 특이사항 |
|---|---|---|---|
| `kakao` | `tid`, `next_redirect_*_url` | `SUCCESS` | KakaoPay 공식 스키마 준수 |
| `toss` | `paymentKey`, `checkoutPage` | `DONE` | 토스페이먼츠 스키마 준수 |
| `naver` | `paymentId`, `checkoutPageUrl` | `resultCode: Success` | 네이버페이 스키마 준수 |
| `danal` | `TID`, `RETURNURL` | `RETURNCODE: 0000` | 대문자 키명 사용 |
| `bank_transfer` | `tid`, `virtualAccount` | `DEPOSIT_CONFIRMED` | 가상계좌 정보 포함 |
| `card` | `tid`, `checkoutUrl` | `APPROVED` | 일반 카드 |

> BE-07은 위 스키마를 기준으로 providerPayload를 파싱합니다.
> `eventType`과 `orderId`·`amount`·`tid`는 provider 무관하게 공통 필드로 사용합니다.
