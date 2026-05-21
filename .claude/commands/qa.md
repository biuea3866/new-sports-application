---
description: E2E(Playwright) + 사용자 시나리오 + 부하(k6) 테스트를 docker-compose로 띄운 로컬 환경에서 실행하고, 결함 발견 시 결함 md 작성 → be-implementer/fe-implementer 자동 호출까지 수행하는 QA 파이프라인.
---

# /qa — QA 파이프라인

## 입력
`$ARGUMENTS` — PR 번호(`#123`), 티켓 번호(`<TICKET-ID>`), 기능명, 또는 `--full-regression`

## 언제 사용하는가
- PR 머지 전 회귀 검증
- 신기능 배포 전 시나리오·부하 검증
- 정기 회귀 (`--full-regression`)
- 가벼운 단위/통합 테스트는 `/implement`의 TDD 단계에서 처리. `/qa`는 화면 레벨 검증이 목적.

---

## 파이프라인 개요

```
[Step 0] 로컬 환경 기동 (docker-compose -f qa/e2e/docker-compose.qa.yml up -d)
   │
   ▼
[Step 1] qa-scenario-author — PR diff·티켓·기존 시나리오 분석 → 시나리오 md 도출
   │      산출: scenarios/{flow}.md (E2E 시나리오) + scenarios/load/{flow}.md (부하)
   ▼
[Step 2] qa-e2e-runner ─┐
                        ├─ 병렬 실행
[Step 2'] qa-load-tester ─┘
   │      산출: artifacts/, e2e-report.md, load-report.md
   ▼
[Step 3] qa-defect-router — 실패 로그·스크린샷·diff 분석 → 담당 레이어(FE/BE/Infra) 분류
   │      산출: defects/{id}-{slug}.md
   │      이 단계는 Jira 등록을 수행하지 않는다 — md만 생성. Jira는 사용자 검토 후 jira-ticket skill로 수동.
   ▼
[Step 4] 결함 단위로 be-implementer / fe-implementer 자동 호출 (병렬, wave 스케줄러)
   │      각 에이전트는 결함 md 1건을 받아 TDD 사이클로 수정 → push → PR 생성
   ▼
[Step 5] 환경 정리 (docker-compose down -v)
   │
   ▼
[Step 6] QA 리포트 요약 출력 — 통과/실패/생성된 결함 수, 자동 수정 PR 링크 목록
```

산출물 루트: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/`

---

## Step 0 — 로컬 환경 기동

QA 회귀 스위트는 `qa/` 디렉토리에 위치한다. docker-compose로 인프라(DB/Kafka/Redis/MongoDB)를 띄우고 BE/FE는 호스트에서 실행한다.

```bash
docker-compose -f qa/e2e/docker-compose.qa.yml up -d
qa/e2e/wait-for-healthy.sh   # 모든 컨테이너 healthy 대기 (최대 120s)
```

환경 변수:
- `QA_BASE_URL` — FE base URL (기본 `http://localhost:3000`)
- `QA_API_URL` — BE base URL (기본 `http://localhost:8080`)
- `QA_TOPIC` — `$ARGUMENTS`에서 추출한 기능명·티켓번호 (산출물 경로 슬러그)

기동 실패 시 즉시 중단. `docker-compose logs`를 산출물 루트에 캡처.

---

## Step 1 — 시나리오 도출 (qa-scenario-author)

**에이전트**: `qa-scenario-author`
**입력**: `$ARGUMENTS` + PR diff (있는 경우) + 기존 `qa/e2e/scenarios/` 목록
**출력 저장**: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/scenarios/`

산출 형식:
- `scenarios/e2e/{flow}.md` — 화면 레벨 E2E 시나리오 (Critical/Major/Minor 라벨)
- `scenarios/load/{flow}.md` — 부하 시나리오 (목표 RPS·p95·ramp-up)

각 시나리오는 [`rules/qa-scenario-guide.md`](../rules/qa-scenario-guide.md)의 형식을 따른다.

---

## Step 2 / 2' — 병렬 실행

`qa-e2e-runner`와 `qa-load-tester`를 **병렬**로 호출한다 (단일 메시지에 두 Agent 콜).

### Step 2 — qa-e2e-runner

**입력**: `scenarios/e2e/` 디렉토리 경로 + `QA_BASE_URL`
**출력 저장**:
- `qa/e2e/specs/{flow}.spec.ts` — Playwright spec (재사용 가능, 회귀 스위트에 영구 보존)
- `.analysis/outputs/qa/{YYYYMMDD}_{topic}/artifacts/` — 스크린샷·트레이스·video·console log
- `.analysis/outputs/qa/{YYYYMMDD}_{topic}/e2e-report.md` — 시나리오별 pass/fail + 아티팩트 링크

### Step 2' — qa-load-tester

**입력**: `scenarios/load/` 디렉토리 경로 + `QA_API_URL`
**출력 저장**:
- `qa/load/k6/{flow}.js` — k6 스크립트 (재사용 가능, 회귀 스위트에 영구 보존)
- `.analysis/outputs/qa/{YYYYMMDD}_{topic}/load-report.md` — RPS·p95·p99·error rate + 임계 위반 시나리오

각 에이전트는 작업 완료 시 raw 출력(터미널 로그)을 산출물 폴더에 보존한다 — `rules/COMPLETION-RULE.md` §2 검증 아티팩트.

---

## Step 3 — 결함 라우팅 (qa-defect-router)

**에이전트**: `qa-defect-router`
**입력**: `e2e-report.md` + `load-report.md` + `artifacts/` + 변경된 코드 경로(있는 경우 PR diff)
**출력 저장**: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/defects/{id}-{slug}.md`

라우터는 각 실패를 다음 중 하나로 분류:
- `layer: FE` — Playwright 화면 검증 실패, 콘솔 에러, 렌더링 깨짐
- `layer: BE` — API 5xx, 데이터 정합성 실패, 비즈니스 규칙 위반
- `layer: INFRA` — 컨테이너 기동 실패, DB 마이그레이션 실패, Kafka 토픽 누락
- `layer: AMBIGUOUS` — 분류 불가. 사람 검토 필수, 자동 호출 대상에서 제외

결함 md는 [`rules/defect-ticket-guide.md`](../rules/defect-ticket-guide.md) 형식. **이 단계에서 Jira에 자동 등록하지 않는다.**

---

## Step 4 — 담당자 자동 호출 (be-implementer / fe-implementer, 워크트리 격리)

라우터 산출 `defects/` 중 `layer: FE`·`BE`만 자동 호출 대상.
**모든 자동 호출은 git worktree로 격리한다** — 각 결함이 독립된 worktree에서 병렬 수정되어 메인 워킹 디렉토리와 결함 간 파일 충돌이 발생하지 않도록 한다.

| 라벨 | 호출 에이전트 | isolation |
|---|---|---|
| `layer: BE` + `severity: Critical|Major` | `be-implementer` | `worktree` 필수 |
| `layer: FE` + `severity: Critical|Major` | `fe-implementer` | `worktree` 필수 |
| `layer: INFRA` | 자동 호출 안 함 — 사람 처리 | — |
| `severity: Minor` | 자동 호출 안 함 — 백로그 | — |
| `layer: AMBIGUOUS` | 자동 호출 안 함 — 사람 검토 | — |

### 스폰 규칙 — 결함 md 인라인 임베드

각 결함 단위로 `Agent` 도구 호출 시 **결함 md 파일 내용을 prompt에 그대로 포함**시킨다. 메인 워크트리의 파일 경로를 전달하지 않는 이유:

- 격리 worktree(`*/.claude/worktrees/agent-*`)에서 `worktree-isolation-guard.sh`가 메인 경로 접근을 차단
- 메인 파일이 작업 중 갱신되어도 에이전트 컨텍스트는 시작 시점 스냅샷으로 고정 (의도된 동작)
- prompt가 self-contained → 디버깅·재실행이 단순

스폰 단계:

1. 메인 세션이 `defects/{id}-{slug}.md`를 `Read`로 읽어 내용을 변수에 보관
2. 각 결함에 대해 다음 형식의 `Agent` 호출:

```
Agent({
  subagent_type: "be-implementer" | "fe-implementer",
  isolation: "worktree",
  description: "QA defect fix: {defect-id} {short-title}",
  prompt: <<PROMPT
# QA 결함 자동 수정 요청

다음 결함 md를 받아 TDD 사이클(RED → GREEN → detekt)로 수정한 후 push → `gh pr create`까지 수행하세요.

## 결함 md 전문 (메인 워크트리 .analysis/outputs/qa/{YYYYMMDD}_{topic}/defects/{id}-{slug}.md)

{여기에 결함 md 파일 내용을 그대로 인라인 임베드}

## 작업 컨텍스트

- 브랜치: fix/qa-{YYYYMMDD}-{slug} (worktree가 자동 생성)
- 변경 범위 제한: 결함 한정. 인접 코드 리팩토링 금지 (CLAUDE.md §3 정밀한 수정)
- 재현 테스트를 먼저 RED로 작성, 그 후 fix → GREEN
- 검증 아티팩트(스크린샷·trace·HAR·load raw.log)는 메인 워크트리에만 있음. 인용이 필요하면 결함 md의 "아티팩트" 섹션 경로를 PR 본문에 참조 링크로 명시. 직접 접근은 시도하지 마세요.
- PR 본문은 pr-guide의 표준 템플릿을 따르고, 본문에 "QA-DEFECT: {id}"를 명시
PROMPT
})
```

- `isolation: "worktree"` — 각 에이전트가 임시 worktree 위에서 동작. 변경 없으면 자동 정리, 변경 있으면 path와 branch가 반환됨
- 결함 간 의존성이 없으면 **단일 메시지에 여러 Agent 콜을 묶어 병렬 스폰**. `wave-fanout-guard.sh` hook의 최대 fan-out 한도 내에서.
- 같은 파일을 동시에 건드릴 가능성이 있는 결함은 같은 wave에 넣지 않는다 ([ticket-guide.md Single Writer per File](../rules/ticket-guide.md)).
- 인라인 prompt가 너무 길어질 우려가 있는 결함(스택트레이스가 100줄+, HAR 인용이 큼)은 결함 md 작성 단계에서 핵심만 발췌하도록 qa-defect-router가 정리한다.

### 브랜치·PR

- 브랜치 네이밍: `fix/qa-{YYYYMMDD}-{slug}` (Jira 티켓 미존재 시 임시 슬러그). worktree가 자동으로 이 브랜치를 생성.
- Jira 티켓이 사용자 검토 후 발행되면 `pr-create` skill 재실행으로 브랜치 rename + PR 본문 갱신.
- 각 에이전트는 TDD 사이클(RED → GREEN → detekt)로 수정 → push → `gh pr create`. 기존 push-test / push-review hook 그대로 적용.

### 워크트리 정리

`/qa` 종료 시 worktree 상태를 요약:
- 변경 없이 종료된 worktree → 자동 cleanup 확인
- 변경 있는 worktree → path·branch·PR URL을 Step 6 리포트에 명시. 사람이 검토 후 worktree 제거(`git worktree remove`).

---

## Step 5 — 환경 정리

```bash
docker-compose -f qa/e2e/docker-compose.qa.yml down -v
```

`-v`로 볼륨 제거. 다음 회귀가 깨끗한 시드로 시작하도록 보장.

---

## Step 6 — 리포트 요약

표준 출력에 다음 요약을 표시:

| 항목 | 값 |
|---|---|
| E2E 시나리오 | pass/fail/skip |
| 부하 시나리오 | pass/fail (p95·error rate 임계 기준) |
| 생성된 결함 md | 개수, layer별 분포 |
| 자동 호출된 에이전트 | be-implementer N건 / fe-implementer M건 |
| 자동 수정 PR | URL 목록 |
| 사람 검토 필요 결함 | `AMBIGUOUS` / `INFRA` / `Minor` md 경로 목록 |
| 다음 액션 | "결함 md 검토 → `/jira-ticket {defect.md}`로 Jira 등록" |

> 완료 단언은 [`rules/COMPLETION-RULE.md`](../rules/COMPLETION-RULE.md)의 §1~4를 모두 충족해야 한다.

---

## 사용 공통 가이드

- [output-style](../rules/output-style.md) — 산출물 문체
- [qa-scenario-guide](../rules/qa-scenario-guide.md) — E2E·시나리오 작성 규칙
- [qa-load-guide](../rules/qa-load-guide.md) — k6 스크립트 작성 규칙
- [defect-ticket-guide](../rules/defect-ticket-guide.md) — 결함 md 구조
- [ticket-guide](../rules/ticket-guide.md) — 결함 md와 정식 티켓의 매핑
- [pr-guide](../rules/pr-guide.md) — 자동 수정 PR 브랜치·제목 규칙
