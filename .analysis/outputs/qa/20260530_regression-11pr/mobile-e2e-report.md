# E2E 실행 리포트 — mobile expo web 화면 렌더 검증

## 요약

| 지표 | 값 |
|---|---|
| 총 시나리오 | 9 |
| Pass | 4 |
| Fail | 5 |
| Skip | 0 |
| 실행 시간 | ~47s |
| 환경 | expo web (localhost:8081) + BE (localhost:8080) |
| QA_BASE_URL | http://localhost:8081 |
| QA_API_URL | http://localhost:8080 |
| Playwright version | 1.60.0 |
| 인증 시드 계정 | qa-portal-fixture@test.local |

---

## 실패 시나리오

| ID | 제목 | 에러 원인 | 아티팩트 |
|---|---|---|---|
| E2E-MOB-01-02 | 홈 탭 렌더 | `Uncaught Error: Screen names must be unique: index,store,tickets,community,store,me` — `_layout.tsx` store 탭 중복 등록 | [screenshot](./artifacts/mobile-screen-render-E2E-M-08f52-s-App-타이틀과-다가오는-경기-섹션이-렌더된다-chromium/test-failed-1.png) [trace](./artifacts/mobile-screen-render-E2E-M-08f52-s-App-타이틀과-다가오는-경기-섹션이-렌더된다-chromium/trace.zip) |
| E2E-MOB-01-03 | 스토어 탭 렌더 | 동일: store 중복 → expo-router `withLayoutContext.js:100` 에러 오버레이 | [screenshot](./artifacts/mobile-screen-render-E2E-M-2ce15-탭-—-상품-목록-또는-빈-상태-텍스트가-렌더된다-chromium/test-failed-1.png) [trace](./artifacts/mobile-screen-render-E2E-M-2ce15-탭-—-상품-목록-또는-빈-상태-텍스트가-렌더된다-chromium/trace.zip) |
| E2E-MOB-01-04 | 티켓 탭 렌더 | 동일: store 중복 → 탭 화면 전체 크래시 | [screenshot](./artifacts/mobile-screen-render-E2E-M-afef5-탭-—-경기-목록-또는-빈-상태-텍스트가-렌더된다-chromium/test-failed-1.png) [trace](./artifacts/mobile-screen-render-E2E-M-afef5-탭-—-경기-목록-또는-빈-상태-텍스트가-렌더된다-chromium/trace.zip) |
| E2E-MOB-01-05 | 커뮤니티 탭 렌더 | 동일: store 중복 | [screenshot](./artifacts/mobile-screen-render-E2E-M-3a66a--—-게시글-목록-또는-빈-상태-텍스트가-렌더된다-chromium/test-failed-1.png) [trace](./artifacts/mobile-screen-render-E2E-M-3a66a--—-게시글-목록-또는-빈-상태-텍스트가-렌더된다-chromium/trace.zip) |
| E2E-MOB-01-06 | 마이페이지 탭 렌더 | 동일: store 중복 | [screenshot](./artifacts/mobile-screen-render-E2E-M-a416d-사용자-정보-이메일-또는-로그아웃-버튼이-렌더된다-chromium/test-failed-1.png) [trace](./artifacts/mobile-screen-render-E2E-M-a416d-사용자-정보-이메일-또는-로그아웃-버튼이-렌더된다-chromium/trace.zip) |

### 결함 상세: Tabs 중복 screen name

**에러 메시지 (runtime)**
```
Uncaught Error: Screen names must be unique: index,store,tickets,community,store,me
  at node_modules/expo-router/build/layouts/withLayoutContext.js:100:23
```

**원인 파일**: `mobile/app/(tabs)/_layout.tsx`

`_layout.tsx` 25~29번째 라인과 44~49번째 라인에 `name="store"` 인 `<Tabs.Screen>`이 2개 선언되어 있습니다.

```
// 25번째 라인 (중복 1)
<Tabs.Screen name="store" options={{ title: '스토어', ... }} />

// 44번째 라인 (중복 2 — 이것이 잉여)
<Tabs.Screen name="store" options={{ title: '스토어', ... }} />
```

expo-router는 `_layout.tsx`의 Screen name이 고유해야 하므로 런타임에 `Error`를 throw하고 앱 전체가 에러 오버레이로 교체됩니다. `/(tabs)` 경로로 진입하는 **모든 탭 화면(홈/스토어/티켓/커뮤니티/마이)** 이 렌더 불가 상태입니다.

**영향 범위**: `/(tabs)` 하위 5개 화면 전체 렌더 실패 (앱 진입 불가).

---

## Pass 시나리오 (렌더 확인됨)

| ID | 제목 | 확인 요소 | 아티팩트 |
|---|---|---|---|
| E2E-MOB-01-01 | 로그인 화면 | "로그인" 텍스트, "Sports App에 오신 것을 환영합니다" 렌더 | [screenshot](./artifacts/screenshot-login-render.png) |
| E2E-MOB-01-07 | 결제 수단 선택 화면 (`/payment/new`) | "결제 수단 선택", 카카오페이/토스페이 등 결제수단 목록, "결제하기" 버튼, "50,000원" 금액 표시 | [screenshot](./artifacts/screenshot-payment-new.png) |
| E2E-MOB-01-08 | 알림 목록 화면 (`/notifications`) | "알림" 헤더 렌더, 빈 상태 텍스트 또는 항목 목록 렌더 | [screenshot](./artifacts/screenshot-notifications.png) |
| E2E-MOB-01-09 | 티켓 발권 화면 (`/event/1/order`) | seatIds 없이 진입 시 "선택된 좌석이 없습니다." 텍스트, "< 뒤로" 버튼 렌더 | [screenshot](./artifacts/screenshot-event-order-empty-seats.png) |

---

## 화면별 상세 결과

### 1. 결제 화면 `app/payment/new.tsx` — PASS

진입 경로: `/payment/new?orderType=BOOKING&orderId=1&amount=50000&itemName=테스트%20결제`

- "결제 수단 선택" 제목 텍스트 표시됨
- 결제수단 7종(카카오페이, 토스페이, 네이버페이, 다날, 신용카드, 계좌이체, 모바일결제) 버튼 그리드 렌더됨
- "결제하기" 버튼 렌더됨 (disabled 상태 — 수단 미선택)
- "50,000원" 금액 표시됨
- 번들 에러 없음, placeholder 없음

### 2. 티켓 발권 화면 `app/event/[id]/order.tsx` — PASS (빈 좌석 분기)

진입 경로: `/event/1/order` (seatIds 없음)

- "선택된 좌석이 없습니다." 텍스트 렌더됨 (빈 seatIds 분기 — 정상 동작)
- "< 뒤로" 버튼 렌더됨
- 번들 에러 없음, placeholder 없음
- 참고: seatIds가 있는 경우(좌석 선택 후 진입) 별도 검증 필요. `useEvent` hook API 호출 흐름은 이번 테스트 범위 외.

### 3. 알림 목록 화면 `app/notifications/index.tsx` — PASS

진입 경로: `/notifications`

- "알림" 제목 헤더 렌더됨
- API 응답 대기 후 "알림이 없습니다." 빈 상태 텍스트 렌더됨 (시드 데이터 알림 없음)
- 번들 에러 없음, placeholder 없음
- unread count 배지: 알림 없으면 미표시 (정상)

### 4. 탭 화면 `app/(tabs)/` — ALL FAIL (5개 모두)

**홈/스토어/티켓/커뮤니티/마이 탭 전부 렌더 불가.**

원인: `app/(tabs)/_layout.tsx`에서 `name="store"` Screen이 2개 선언됨.
expo-router가 `useFilterScreenChildren` 내부에서 중복 검사 후 `Error`를 throw.
결과: 에러 오버레이가 앱 전체를 덮어 콘텐츠 렌더 불가.

---

## 로그인 화면 결과

`app/(auth)/login.tsx` — PASS

- "로그인" 텍스트 렌더됨
- "Sports App에 오신 것을 환영합니다" 부제목 렌더됨
- 이메일/비밀번호 입력 필드 렌더됨
- 로그인 버튼 렌더됨
- "회원가입" 링크 렌더됨

---

## 환경 메타

| 항목 | 값 |
|---|---|
| Playwright version | 1.60.0 |
| 실행 브라우저 | chromium (Desktop Chrome) |
| expo web port | 8081 |
| BE port | 8080 |
| 시드 계정 | qa-portal-fixture@test.local |
| spec 파일 | qa/e2e/specs/mobile-screen-render.spec.ts |
| run log | .analysis/outputs/qa/20260530_regression-11pr/e2e-run.log |
| 실행 결과 JSON | .analysis/outputs/qa/20260530_regression-11pr/e2e-results.json |

---

## 결함 요약

| 결함 ID | 파일 | 에러 | 영향 | severity |
|---|---|---|---|---|
| DEF-001 | `mobile/app/(tabs)/_layout.tsx` | `name="store"` Tabs.Screen 중복 선언 (25번째, 44번째 라인) | /(tabs) 5개 화면 전체 렌더 불가 — 앱 진입 불가 | Major |
