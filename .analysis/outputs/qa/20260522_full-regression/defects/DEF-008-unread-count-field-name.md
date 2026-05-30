# DEF-008 GET /notifications/me/unread-count 응답 — count 필드를 unreadCount 로 직렬화

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-08-03, E2E-08-E03
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E-08-03 error-context:
  > spec L48-49: `expect(typeof body.unreadCount).toBe("number")` → `Expected: "number"` / `Received: "undefined"`
  → `unreadCount` 필드가 응답에 없음
- `backend/src/main/kotlin/com/sportsapp/application/notification/NotificationResponse.kt:51`:
  ```kotlin
  data class UnreadCountResponse(val count: Long)
  ```
  → 실제 직렬화 필드는 `count`. 시나리오 계약은 `unreadCount`
- 동일 응답 DTO 사용 — E2E-08-03(정상 응답) + E2E-08-E03(0건 사용자, 응답값 0) 둘 다 같은 필드명 결함 → 한 결함으로 묶음
- BE 응답 직렬화 결함 → layer: BE
- 알림 unread 표시는 헤더 뱃지 등 UX 광범위 영향 → Major

## 재현 단계
1. BE 기동
2. `GET /notifications/me/unread-count` 호출 (`X-User-Id: 1` 헤더)
3. 200 응답 + body 확인:
   ```json
   { "count": 0 }
   ```
4. **`unreadCount` 필드 부재** — FE/모바일이 `body.unreadCount`를 기대하면 undefined

## 기대 동작
```json
{ "unreadCount": <number> }
```

## 실제 동작
```json
{ "count": <number> }
```

## 영향 범위
- 영향 사용자: 헤더 알림 뱃지 등 unread count를 화면에 표시하는 모든 사용자 (현재 FE가 어떤 필드를 사용하는지 확인 필요)
- 영향 화면/엔드포인트: `GET /notifications/me/unread-count`
- 데이터 영향: 없음 (read only)

## 아티팩트
- [E2E-08-03 error-context](../artifacts/playwright-output/notification-message-E2E-0-35e07--count-—-200-unreadCount-숫자-chromium/error-context.md)
- [E2E-08-E03 error-context](../artifacts/playwright-output/notification-message-E2E-0-3fb75-알림-0건-user-—-unread-count-0-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/application/notification/NotificationResponse.kt` | 51 | `data class UnreadCountResponse(val count: Long)` — 필드명을 `unreadCount`로 변경 |
| `backend/src/main/kotlin/com/sportsapp/presentation/notification/NotificationApiController.kt` | 43-47 | 응답 DTO 사용처 — 변경 없을 가능성 (필드명 변경만으로 충분) |

가설:
- A) `count` → `unreadCount`로 필드명 단순 변경 (가장 단순)
- B) 또는 `@JsonProperty("unreadCount")` 어노테이션 추가 — Kotlin data class에서는 필드명 변경이 더 자연스러움

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — `UnreadCountResponse.count` 필드명만 변경. 다른 알림 DTO·UseCase 변경 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `NotificationApiControllerTest.kt`에 `GET /notifications/me/unread-count` 응답에 `unreadCount` 필드가 number로 존재함을 검증하는 케이스 추가
  2. **GREEN**: `UnreadCountResponse(val unreadCount: Long)` 로 변경 + UseCase의 of/생성자 호출부 수정
  3. **GREEN 검증**: E2E-08-03, E2E-08-E03 모두 통과
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/notification/NotificationApiControllerTest.kt`
- 예상 변경 파일 수: 2~3개 (DTO + UseCase 생성자 호출부 + 테스트)
- **반드시 점검**: FE/모바일에서 `body.count` 직접 참조가 있는지:
  ```bash
  grep -rn "unreadCount\|\.count\b" web/lib web/app mobile --include="*.ts" --include="*.tsx"
  ```
