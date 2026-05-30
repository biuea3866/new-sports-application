# QA 시나리오 작성 가이드

`/qa` 파이프라인이 생성·실행하는 **화면 레벨 E2E 시나리오 md**의 형식과 규칙. qa-scenario-author·qa-e2e-runner·qa-defect-router가 공통 참조합니다.

> 단위/레포지토리/시나리오 3계층 테스트 컨벤션은 [`ticket-guide.md`](./ticket-guide.md)에 정의돼 있습니다. 본 문서는 **사용자 관점 화면 시나리오**에 한정한 추가 규칙만 다룹니다.

## 시나리오 파일 위치

| 위치 | 용도 | 보존 |
|---|---|---|
| `qa/e2e/scenarios/{flow-slug}.md` | 영구 회귀 시나리오 (모든 회귀에서 실행) | 레포 영구 |
| `.analysis/outputs/qa/{YYYYMMDD}_{topic}/scenarios/e2e/{flow-slug}.md` | 1회성 — 특정 PR/티켓에 한정한 임시 시나리오 | 산출물 |

산출물에 만든 시나리오 중 회귀 가치 있는 것만 사람 검토 후 `qa/e2e/scenarios/`로 승격.

## md 구조

```markdown
# E2E-{NN} {플로우명}

## 메타
- severity: Critical | Major | Minor
- layer-hint: FE | BE | FULL-STACK
- related-files: [...]            # 이 시나리오를 트리거한 변경 파일
- related-ticket: <TICKET-ID> | none
- estimated-duration: 30s | 2m    # 예상 실행 시간

## 사전 조건
- DB 시드: {필요한 fixture 이름 — qa/e2e/fixtures/ 참조}
- 인증 상태: {anonymous | user-A | admin}
- 환경 변수: (필요 시)

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-{NN}-01 | ... | ... | ... |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|

## 엣지 케이스
| ID | 케이스 |
|---|---|
```

## Severity 분류 기준

| severity | 기준 | /qa 자동 수정 대상 |
|---|---|---|
| Critical | 결제·로그인·핵심 비즈니스 플로우 실패. 즉시 핫픽스 필요 | ✅ |
| Major | 주요 기능 실패. 회피 가능한 워크어라운드 존재 | ✅ |
| Minor | UI 깨짐·문구 오류·비핵심 케이스. 백로그로 관리 | ❌ |

## 도출 체크리스트

각 플로우당 다음을 커버:

| 케이스 종류 | 최소 개수 |
|---|---|
| Happy path | 1 |
| 검증 실패 (필수 필드 누락·형식 오류) | 1 이상 |
| 권한 실패 (비로그인·타 워크스페이스·역할 부족) | 1 이상 |
| 데이터 경계 (빈 목록·최대 페이지·null) | 1 이상 |
| 동시성 (해당 시) | 0~1 |

미충족 시 qa-scenario-author는 보완 후 산출.

## 형식 규칙

- 케이스는 **한 줄**. Given/When/Then을 풀어쓰지 않음 ([ticket-guide.md](./ticket-guide.md) 형식 규칙 그대로)
- 관찰 가능한 결과를 Then에 명시:
  - ✅ "내 시설 카드에 '비어있음' 텍스트가 표시된다"
  - ❌ "정상 동작한다"
- ID 컨벤션: `E2E-{시나리오번호}-{케이스번호}`. 시나리오 = md 파일 1개, 케이스 = 표 1행
- `related-files`는 비어있으면 안 됨 — 어떤 변경이 이 시나리오를 트리거했는지 추적 가능해야 함

## 회귀 vs 1회성 구분

- **회귀 시나리오**: 핵심 비즈니스 플로우. 모든 `/qa` 실행에서 돌아야 함. `qa/e2e/scenarios/`에 영구 보존.
- **1회성 시나리오**: 특정 PR/티켓의 변경 범위에만 의미 있음. 산출물에만 두고 다음 회귀에서는 제외.

승격 기준 (산출물 → 영구 회귀):
- 결함이 실제로 발견된 시나리오 (회귀 방지 목적)
- Critical/Major severity면서 사용자가 빈번하게 거치는 플로우
- 사람이 검토 후 명시적으로 승격

## 안티 패턴

- 한 시나리오 md에 10개 초과 케이스 → 플로우를 더 잘게 분리
- `related-files`가 없거나 `[]` → 어떤 변경 때문에 도출됐는지 불명, 회귀 추적 불가
- Critical 시나리오가 1개 플로우당 5개 초과 → severity 분류 재검토 (모든 게 Critical이면 우선순위 없음)
- 시나리오 md에 구현 디테일 노출 (`button.btn-primary` 같은 CSS 셀렉터) → Page Object Model에 두고 시나리오 md에는 사용자 의도만

## 참고

- [ticket-guide](./ticket-guide.md) — Given/When/Then 한 줄 형식, ID 컨벤션
- [defect-ticket-guide](./defect-ticket-guide.md) — 결함 발견 시 md 구조
- [qa-load-guide](./qa-load-guide.md) — 부하 시나리오 별도 규칙
