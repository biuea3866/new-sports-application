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
[Step 5] fix 리뷰 + 재검증 (필수)
   │      5-A code-reviewer로 fix 리뷰
   │      5-B fix 통합 브랜치에서 Step 1~3 재실행 → 결함 해결·회귀 확인
   │      미해결 시 Step 4로 루프 (최대 3회). 산출: reverify-report.md
   ▼
[Step 6] 환경 정리 (docker-compose down -v) ← qa-reverify-gate.sh hook이 reverify-report.md 검사
   │
   ▼
[Step 7] QA 리포트 요약 출력 — 1차 회귀 + 재검증 결과 + 자동 수정 PR 링크
```

산출물 루트: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/`

---

## Step 0 — 로컬 환경 기동

QA 회귀 스위트는 `qa/` 디렉토리에 위치한다. docker-compose로 인프라(DB/Kafka/Redis/MongoDB)를 띄우고 BE/FE는 호스트에서 실행한다.

### 0-A — 인프라 기동

```bash
docker-compose -f qa/e2e/docker-compose.qa.yml up -d
qa/e2e/wait-for-healthy.sh   # 모든 컨테이너 healthy 대기 (최대 120s)
```

### 0-B — FE 환경 변수(`.env.local`) 생성·검증 (필수 — 생략 시 회귀 전체 왜곡)

Next.js FE의 `web/lib/server/be-client.ts`는 `BACKEND_URL`을 **모듈 로드 시점**에 요구한다.
`.env.local`이 없으면 `/portal/*` 전 페이지가 SSR 500을 반환하고, **portal 화면 시나리오가 무더기로 거짓 fail**한다.

> 회고: 1차 `/qa --full-regression` 에서 `.env.local` 누락으로 portal-dashboard 9건이 거짓 fail →
> Critical 결함(DEF-002)으로 과대 분류 → 불필요한 fix PR 생성. 환경 결함을 코드 결함으로 오인한 사고.

FE 기동 **전에** 다음을 수행한다:

```bash
# .env.local 없으면 생성
if [ ! -f web/.env.local ]; then
  cat > web/.env.local <<'EOF'
BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_APP_NAME=Sports Application
EOF
fi
# BACKEND_URL 존재 검증 — 없으면 즉시 중단
grep -q '^BACKEND_URL=' web/.env.local || { echo "FATAL: web/.env.local에 BACKEND_URL 없음"; exit 1; }
```

`qa-env-gate.sh` hook이 Playwright 실행 직전 `.env.local`의 `BACKEND_URL`을 강제 검증한다 — 누락 시 E2E 실행을 차단한다.

### 0-C — BE/FE/Mobile 호스트 기동 (FE는 production 빌드 필수)

`.next` 캐시는 환경 변수 변경을 반영하지 못할 수 있으므로 회귀 시작 시 1회 삭제한다.

**FE는 반드시 `next build && next start` (production 모드)로 기동한다.** `next dev`는 hydration 타이밍 비결정성으로 Playwright E2E가 거짓 실패할 수 있다 — 폼이 hydration 전에 제출돼 네이티브 submit으로 페이지가 리로드되는 현상이 관측됐다 (`/login` 회귀 사례, dev 모드에서 4건 중 3건 거짓 fail → production 빌드에서 4/4 통과로 확정). `qa-fe-mode-gate.sh` hook이 Playwright 실행 시 `next dev` 프로세스를 감지하면 차단한다.

```bash
rm -rf web/.next                                                       # stale 캐시 제거
cd backend && APP_JWT_SECRET=<qa-secret> ./gradlew bootRun &           # :8080
cd web && npx next build && nohup npx next start -p 3000 &             # :3000 — production 빌드 + start
cd mobile && npx expo start --web --port 8081 &                        # :8081
```

BE는 startup에 30~120s 소요. FE는 빌드 1~2분 + start 수 초. `/actuator/health` 200 + FE `/portal` 비-500 + Mobile 200을 **모두 확인**한 뒤 Step 1로 진행한다. 한 서버라도 비정상이면 중단.

> 개발 단계 hot-reload(`next dev`)는 `/qa`의 회귀 대상이 아니다. `/qa`는 "실제 배포되는 동일 산출물"을 검증하므로 production 빌드가 정답이다.

### 환경 변수

- `QA_BASE_URL` — FE base URL (기본 `http://localhost:3000`)
- `QA_API_URL` — BE base URL (기본 `http://localhost:8080`)
- `QA_TOPIC` — `$ARGUMENTS`에서 추출한 기능명·티켓번호 (산출물 경로 슬러그)

기동 실패 시 즉시 중단. `docker-compose logs` + BE/FE 로그를 산출물 루트에 캡처.

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

#### 렌더 검증 필수 (스모크·status 대체 절대 금지)

E2E의 핵심 책임은 **인증된 상태에서 화면이 실제로 렌더된 결과를 눈으로 확인 가능한 수준으로 단언**하는 것이다. 다음은 "E2E 통과/렌더 확인"으로 **인정하지 않는다**:

- BE API 직접 호출(`curl`) 스모크 — API 200/400은 화면 렌더 증명이 아니다
- SSR HTTP status(200/307/404)만 확인 — 307(미인증 리다이렉트)은 "라우트 존재"일 뿐 렌더가 아니다
- 로그인 없이 미인증 리다이렉트만 확인하고 종료

각 화면 시나리오는 반드시 다음을 수행한다:
1. 로그인 fixture로 **인증 쿠키/세션 확보** 후 페이지 진입 (`qa/e2e/fixtures/`)
2. 화면 요소를 **콘텐츠 단위로 단언** — `await expect(page.getByText(...)).toBeVisible()`, 차트/목록/테이블의 실제 데이터 행·축 라벨, 빈 상태일 땐 빈 상태 텍스트
3. 핵심 상호작용(기간 필터 변경·버튼 클릭·폼 제출·읽음 처리) 후 **결과 DOM 변화** 단언
4. 정상·실패 모두 `browser_take_screenshot`으로 캡처해 아티팩트에 보존

> 회고(2026-05-31): 화면 E2E를 BE API 스모크 + SSR status(200/307)로 대체하고 "검증 통과"를 단언한 사고. status는 렌더를 증명하지 않는다. **인증 후 실제 렌더·상호작용 단언이 /qa의 핵심 책임**이며, 이를 생략하면 `/qa` 완료로 단언할 수 없다.

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
- 변경 있는 worktree → path·branch·PR URL을 Step 7 리포트에 명시. 사람이 검토 후 worktree 제거(`git worktree remove`).

---

## Step 5 — fix 리뷰 + 재검증 (필수 — 생략 불가)

Step 4에서 결함 fix PR이 생성된 것으로 끝이 아니다. **fix가 실제로 결함을 해결했는지, 새 회귀를 만들지 않았는지 재검증**해야 한다. 이 단계를 건너뛰면 `qa-reverify-gate.sh` hook이 Step 6(환경 정리)을 차단한다.

> "결함 fix PR 생성 = QA 완료"는 거짓 완전성이다. 코드가 실제로 정상 동작하는지 확인하기 전까지 `/qa`는 `in-progress`다 ([COMPLETION-RULE.md](../rules/COMPLETION-RULE.md) §1).

### Step 5-A — fix 리뷰

Step 4에서 호출된 각 fix(브랜치 또는 PR)에 대해 `code-reviewer` 에이전트를 호출한다.

- 입력: fix 브랜치명 또는 PR 번호
- 검수: harness-rules 위반, 결함 범위 일탈(인접 리팩토링), 재현 테스트 누락, fix가 결함 md의 기대 동작과 일치하는지
- 산출: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/review/{def-id}.md`
- Must Fix 발견 시 해당 fix 에이전트를 재호출(SendMessage 또는 신규 spawn)해 보강 후 재리뷰

### Step 5-B — 재검증 (re-verification)

모든 fix를 통합한 상태에서 회귀를 **다시 실행**한다.

1. 검증 브랜치 생성: `qa-reverify/{YYYYMMDD}` ← origin/dev + Step 4의 모든 fix 브랜치 머지
2. BE/FE/Mobile을 fix 적용 상태로 재기동 (Step 0과 동일 절차, 단 코드는 검증 브랜치)
3. **Step 1~3 재실행** — qa-e2e-runner + qa-load-tester로 동일 시나리오 회귀, qa-defect-router로 결과 분류
4. 직전 회귀와 결과 비교:

| 직전 결과 | 재검증 결과 | 판정 |
|---|---|---|
| Fail | Pass | ✅ 해결됨 |
| Fail | Fail | ❌ fix 불충분 — 결함 md 갱신 후 Step 4 재호출 (루프) |
| Pass | Fail | 🔴 fix가 회귀 유발 — 신규 결함으로 즉시 등록 + Step 4 재호출 |
| Pass | Pass | ✅ 회귀 없음 |

5. 산출: `.analysis/outputs/qa/{YYYYMMDD}_{topic}/reverify-report.md`

### 재검증 통과 기준

- auto-fix 대상 결함이 **모두 재검증에서 Pass**
- 신규 회귀(직전 Pass → 재검증 Fail) **0건**
- 미달 시 Step 4로 돌아가 루프. **최대 3회**. 3회 후에도 미해결이면 사람 검토 큐로 이관하고 `reverify-report.md`에 명시
- auto-fix 대상 결함이 0건(INFRA/Minor만 발견)인 경우에도 `reverify-report.md`에 "재검증 대상 0건 — 생략" 사유를 명시적으로 기록한다 (빈 산출물 금지)

> `reverify-report.md`가 없거나 통과 기준 미달이면 `qa-reverify-gate.sh` hook이 docker-compose down(Step 6)을 차단한다.

---

## Step 6 — 환경 정리

```bash
docker-compose -f qa/e2e/docker-compose.qa.yml down -v
```

`-v`로 볼륨 제거. 다음 회귀가 깨끗한 시드로 시작하도록 보장.

> 이 명령은 `qa-reverify-gate.sh` hook의 검사를 받는다. Step 5 재검증 산출물(`reverify-report.md`)이 없으면 차단된다.

---

## Step 7 — 리포트 요약

표준 출력에 다음 요약을 표시:

| 항목 | 값 |
|---|---|
| E2E 시나리오 (1차) | pass/fail/skip |
| 부하 시나리오 (1차) | pass/fail (p95·error rate 임계 기준) |
| 생성된 결함 md | 개수, layer별 분포 |
| 자동 호출된 에이전트 | be-implementer N건 / fe-implementer M건 |
| 자동 수정 PR | URL 목록 |
| **fix 리뷰 결과** | PR별 approve / request-changes |
| **재검증 결과** | 해결 N건 / 미해결 M건 / 회귀유발 K건 (reverify-report.md 기준) |
| 사람 검토 필요 결함 | `AMBIGUOUS` / `INFRA` / `Minor` md 경로 목록 |
| 다음 액션 | "결함 md 검토 → `/jira-ticket {defect.md}`로 Jira 등록" |

리포트에는 **1차 회귀와 재검증을 모두 표기**한다. 재검증 없이 1차 결과만으로 "완료"를 단언하지 않는다.

> 완료 단언은 [`rules/COMPLETION-RULE.md`](../rules/COMPLETION-RULE.md)의 §1~4를 모두 충족해야 한다. "정상 동작" 단언은 `reverify-report.md`의 Pass 판정을 아티팩트로 첨부해야 인정된다.

---

## 사용 공통 가이드

- [output-style](../rules/output-style.md) — 산출물 문체
- [qa-scenario-guide](../rules/qa-scenario-guide.md) — E2E·시나리오 작성 규칙
- [qa-load-guide](../rules/qa-load-guide.md) — k6 스크립트 작성 규칙
- [defect-ticket-guide](../rules/defect-ticket-guide.md) — 결함 md 구조
- [ticket-guide](../rules/ticket-guide.md) — 결함 md와 정식 티켓의 매핑
- [pr-guide](../rules/pr-guide.md) — 자동 수정 PR 브랜치·제목 규칙
