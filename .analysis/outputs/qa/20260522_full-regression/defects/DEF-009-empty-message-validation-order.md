# DEF-009 POST /rooms/{roomId}/messages 빈 메시지 검증 — 시드 부재로 RoomNotFound(404)가 먼저 발생

## 메타
- layer: INFRA
- severity: Minor
- auto-fix-eligible: false
- source-scenario: E2E-08-E04
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E error-context (`notification-message-E2E-0-6b5e4--08-E04-*-chromium/error-context.md`):
  > `Expected value: 404` / `Received array: [400, 422]` — playwright `toContain` 메시지 형식상 헷갈리나, 실제 응답이 404, spec은 `[400, 422]` 기대
  > spec L178-186:
  ```ts
  test("E2E-08-E04 빈 메시지 내용 POST 시 400", async () => {
    ...
    data: { content: "" },
    expect([400, 422]).toContain(res.status());
  });
  ```
- 시나리오 spec은 빈 content 입력에 대한 Bean Validation 응답(400/422)을 기대. 실제는 404 — 즉 BE가 `room 1`을 찾지 못해 `RoomNotFoundException` 먼저 throw
- QA 환경에서 `rooms` 테이블에 시드 미주입 (`docker-compose.qa.yml` 의 시드 부재) → roomId=1 조회 실패. Validation 보다 RoomNotFound가 먼저 발생하는 것은 도메인 동작 순서 자체로는 합리적 가능성
- 즉 **BE 도메인 로직 결함이 아니라 QA 인프라(시드)** 결함 + spec 가정 불일치
- layer: INFRA (시드 부재). 단 BE 측에서 Validation을 RoomLookup보다 먼저 수행할지 정책 결정도 필요할 수 있음 — auto-fix는 보류
- 알림/메시지 입력 검증은 비핵심 케이스 → Minor

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d` (시드 fixture 미주입)
2. BE 기동
3. `POST /rooms/1/messages` 호출 (`X-User-Id: 1`, body `{ "content": "" }`)
4. **404 RoomNotFound 응답** — spec은 400/422 기대

## 기대 동작
시나리오 의도: 빈 content 입력은 Bean Validation 단계에서 400/422로 거부.
하지만 BE 도메인 순서가 "room 존재 확인 → 검증" 이라면 시드 없는 환경에서는 404가 자연스러움. spec과 도메인 정책이 충돌.

## 실제 동작
HTTP 404 + RoomNotFound ProblemDetail.

## 영향 범위
- 영향 사용자: QA 환경 한정 — 실 운영 환경(rooms 시드 존재)에서는 400/422 응답 가능성. 운영 영향 없음
- 영향 화면/엔드포인트: `POST /rooms/{roomId}/messages` — 시드 의존
- 데이터 영향: 없음

## 아티팩트
- [E2E-08-E04 error-context](../artifacts/playwright-output/notification-message-E2E-0-6b5e4--08-E04-빈-메시지-내용-POST-시-400-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `qa/e2e/fixtures/` (디렉토리 부재) | — | rooms / room-members 시드 SQL 미주입 |
| `qa/e2e/docker-compose.qa.yml` | (확인 필요) | mysql 컨테이너 init-scripts 마운트 부재 추정 |
| `backend/src/main/kotlin/com/sportsapp/presentation/notification/MessageApiController.kt` 또는 동등 | (확인 필요) | `@Valid @RequestBody`가 부착돼 있다면 Bean Validation이 먼저 동작하는지 — Spring 표준은 controller body validation이 먼저 |

가설:
- A) `@Valid @RequestBody` 검증은 Spring MVC가 컨트롤러 진입 전에 수행 — `content`가 `@NotBlank`라면 RoomNotFound 보다 먼저 400이 떠야 함. 본 결함은 BE 측 DTO에 `@NotBlank` 미부착일 가능성 — 그러면 layer: BE로 재분류 필요
- B) `@NotBlank`가 있는데도 404가 떴다면 검증 미동작 / 컨트롤러가 `@Valid` 미사용 — BE 결함
- C) 시드 주입으로 환경 정합성 확보 — INFRA

분류가 두 갈래(BE or INFRA)라 **AMBIGUOUS 후보**. 본 결함은 우선 INFRA로 두고 사람 검토 권장 — BE 측 DTO 확인이 선행돼야 정확한 분류 가능.

## 자동 수정 지시
대상 에이전트: (없음 — 사람 처리)

`auto-fix-eligible: false`. 다음 두 단계로 사람이 직접 처리:
1. BE 측 메시지 생성 요청 DTO에 `@field:NotBlank` 부착 여부 확인. 없으면 추가 → 별도 BE 결함 티켓으로 분리
2. QA 환경에 rooms 시드 fixture 추가 — `qa/e2e/fixtures/rooms.sql` + docker-compose init-scripts 마운트 → 별도 INFRA 티켓
