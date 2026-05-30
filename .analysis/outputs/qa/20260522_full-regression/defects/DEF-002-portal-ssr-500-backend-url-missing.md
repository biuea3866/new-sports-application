# DEF-002 /portal SSR 500 — BACKEND_URL 환경 변수 미설정 시 be-client 모듈 로드 단계 throw

## 메타
- layer: FE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-07-01, E2E-07-02, E2E-07-03, E2E-07-04, E2E-07-05, E2E-07-R01, E2E-07-E01, E2E-07-E02, E2E-07-E03 (9건)
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E 9건 모두 `expect(response?.status()).toBeLessThan(500)` 단언이 `Received: 500` 으로 실패 (artifacts/playwright-output/portal-dashboard-* 9개 error-context.md 공통)
- 페이지 스냅샷이 `alert [ref=e1]` 한 줄만 — 페이지 자체는 응답하나 Next.js dev 서버가 SSR 단계에서 5xx 페이지(Internal Server Error) 를 렌더
- `web/lib/server/be-client.ts:11-15`:
  ```ts
  const BACKEND_URL = process.env["BACKEND_URL"];
  if (!BACKEND_URL) {
    throw new Error("[be-client] BACKEND_URL 환경 변수가 설정되지 않았습니다. ...");
  }
  ```
  → 모듈 import 시점(top-level)에서 throw. `web/app/portal/page.tsx`가 `getMyDashboardSummary → beClient` 체인을 로드하므로 SSR 진입 즉시 500
- `web/.env.local`, `web/.env.development.local`, `web/.env` 미존재 (`.env.local.example`만 존재). `qa/e2e/` 부트 스크립트에서 web 서버용 `BACKEND_URL` 환경 변수가 inject 되지 않음
- 즉 BE API 호출 실패가 아니라 **Next.js 서버 모듈 초기화 실패** → layer: FE (BFF 측 환경/모듈 결함)
- 9건이 모두 같은 SSR 페이지(`/portal`) 진입이므로 한 결함으로 묶음

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d` (인프라 기동)
2. `cd backend && ./gradlew bootRun` (BE 정상 기동)
3. `web/` 디렉토리에서 `.env.local` 없이 (또는 `BACKEND_URL` 환경 변수 없이) `pnpm dev` 실행
4. 브라우저에서 `http://localhost:3000/portal` 진입
5. **Next.js 500 Internal Server Error 페이지 반환**

## 기대 동작
인증 없이 진입 시 로그인 페이지로 리다이렉트되거나, BE에서 401/403 응답을 받아 `PortalApiError`로 잡힌 후 `errorMessage` 안내와 함께 페이지 200 렌더 (시나리오 md `qa/e2e/scenarios/portal-dashboard.md`의 Then 그대로).

## 실제 동작
9개 시나리오 모두 동일:
```
Error: expect(received).toBeLessThan(expected)
Expected: < 500
Received:   500
```

페이지 스냅샷:
```yaml
- alert [ref=e1]
```

콘솔/네트워크 트레이스: `trace.zip` 참조 — Next.js 5xx 페이지 마크업만 표시.

## 영향 범위
- 영향 사용자: 사업자 포털 진입 사용자 100% (FACILITY_OWNER / EVENT_HOST / GOODS_SELLER 전 역할)
- 영향 화면/엔드포인트: `/portal` 전 페이지 — `/portal/bookings`, `/portal/events`, `/portal/products`, `/portal/slots` 까지 같은 `be-client` import 체인이면 동일 증상 추정
- 데이터 영향: 없음 (read 만)

## 아티팩트
- [E2E-07-01 error-context](../artifacts/playwright-output/portal-dashboard-E2E-07-po-d8046-입-—-portal-페이지가-렌더된다-시드-의존--chromium/error-context.md)
- [E2E-07-01 video](../artifacts/playwright-output/portal-dashboard-E2E-07-po-d8046-입-—-portal-페이지가-렌더된다-시드-의존--chromium/video.webm)
- [E2E-07-01 screenshot](../artifacts/playwright-output/portal-dashboard-E2E-07-po-d8046-입-—-portal-페이지가-렌더된다-시드-의존--chromium/test-failed-1.png)
- 나머지 8건은 `playwright-output/portal-dashboard-*` 동일 패턴

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `web/lib/server/be-client.ts` | 9-15 | `BACKEND_URL` 미설정 시 모듈 import 단계 throw — top-level fail-fast가 BFF 페이지 전체 5xx로 전파 |
| `web/lib/portal/dashboard.ts` | 1-16 | `beClient` 호출부 — be-client import만 해도 모듈 로드 실패 |
| `web/app/portal/page.tsx` | 1-92 | SSR 페이지. `try/catch`로 `getMyDashboardSummary` 호출하지만 모듈 로드 단계 throw는 잡지 못함 |
| `web/.env.local.example` | — | `.env.local`이 실제 존재하지 않아 dev 환경 누락 |
| `qa/e2e/docker-compose.qa.yml` 또는 web 서버 기동 스크립트 | — | `BACKEND_URL=http://localhost:8080` 주입 누락 가능성 |

가설:
- A) `.env.local` 미존재 + Playwright runner가 web dev 서버 기동 시 `BACKEND_URL` 환경 변수를 export하지 않음 → 모듈 초기화 실패
- B) be-client.ts의 top-level throw 자체를 lazy 평가로 바꾸거나 default value(예: `http://localhost:8080`)로 fallback 처리해도 됨 — 단 BFF 패턴 원칙(환경 변수 강제)을 깨면 안 됨
- C) E2E 환경 한정으로 `web/.env.test.local` 같은 fixture를 추가 + Playwright `webServer` 옵션에 `env: { BACKEND_URL: '...' }` 명시

## 자동 수정 지시
대상 에이전트: fe-implementer

작업 범위:
- 결함 한정 — `web/lib/server/be-client.ts` 의 초기화 전략 + E2E 환경에서 `BACKEND_URL` 주입 경로 한정. `web/app/portal/`, `web/lib/portal/` 인접 리팩토링 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `web/lib/server/__tests__/be-client.test.ts`에 `BACKEND_URL` 미설정 시 (모듈 import 단계가 아닌) 호출 시점에 명확한 에러를 던지거나 `process.env.BACKEND_URL` fallback이 동작함을 검증하는 테스트 작성. 추가로 `web/app/portal/__tests__/dashboard-guard.test.ts`에 SSR 페이지가 BE 에러를 받아도 5xx가 아닌 200 + alert을 렌더하는 케이스 추가
  2. **GREEN**: 가장 단순한 fix — `be-client.ts`의 top-level throw를 호출 시점 검증으로 옮기거나, `qa/e2e/` 환경에 한해 `BACKEND_URL` 기본값/env 주입을 추가. 어느 방향이든 SSR 모듈 import 실패는 발생하지 않아야 함
  3. **GREEN 검증**: Playwright `portal-dashboard.spec.ts` 9건이 모두 `status < 500` 통과
- 테스트 위치 제안: `web/lib/server/__tests__/be-client.test.ts`, `web/app/portal/__tests__/dashboard-guard.test.ts`
- 예상 변경 파일 수: 2~3개 (be-client.ts + 환경 fixture 또는 E2E webServer 설정)
- **반드시 점검**: 동일 패턴이 다른 BFF 클라이언트에 있는지 확인:
  ```bash
  grep -rn "process.env\[\"BACKEND_URL\"\]\|process.env.BACKEND_URL" web/lib --include="*.ts"
  grep -rn "if (!.*) {\s*throw" web/lib/server --include="*.ts"
  ```
