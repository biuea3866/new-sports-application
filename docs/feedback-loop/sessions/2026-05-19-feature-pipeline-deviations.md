# 2026-05-19 — /feature 파이프라인 spec 이탈 회고

## 개요

`/feature` 커맨드 (`.claude/commands/feature.md`) 의 Wave 스케줄러 알고리즘과 PR 머지 게이트 조항을 명시 위반한 사례. 사용자 지적 후 룰·hook 으로 재발 방지 장치 도입.

## 세션 컨텍스트

- 입력: PRD (`docs/prd/sports-application-prd.md`)
- 결과: Wave 1 (3 PR) + Wave 2 batch (4 PR) 총 7 PR 머지
- 위반 후 정정: 잔여 Wave 2 ready 3건(INFRA-03/04/05)을 같은 메시지에 병렬 스폰

## spec 위반 사항 — 4건

### 1. Wave 분할 (직렬화)

**spec 명시** (`feature.md` Step 2):

> 한 wave 안의 모든 ready 티켓은 **반드시 하나의 어시스턴트 메시지에 병렬 스폰**한다.
> 여러 메시지에 나눠 스폰하는 것은 직렬화로 간주, 금지. ready 셋 크기 상한 없음.

**위반 패턴**:

| 시점 | ready 셋 | 실제 스폰 | 결과 |
|---|---|---|---|
| Wave 2 진입 | 7건 (INFRA-02·03·04·05·07 + WEB-09 + MOBILE-09) | batch 1: 3건(INFRA-02·WEB-09·MOBILE-09) | 직렬화 |
| Wave 2 진행 중 | 4건 잔여 | batch 2: INFRA-07 단독 | 직렬화 |
| Wave 2 진행 중 | 3건 잔여 | INFRA-03 단독 | 직렬화 |

**근본 원인**:
- 메인 세션이 "context 사용량 + 관리 복잡도"를 이유로 자체 판단으로 wave 를 batch 로 쪼갬
- spec 의 "상한 없음" + "직렬화 금지" 조항을 무시
- TPM 분해 검토 대신 wave 분할로 회피

### 2. wave-internal 충돌 회피

**spec 명시** (`feature.md` Step 2 #5):

> 머지 충돌이 발생하면 **이 wave 안에서 해소**한다. 다음 wave 로 미루지 않는다.
> (충돌이 자주 발생하면 분해가 잘못된 것 — Single Writer per File 재검토.)

**위반 패턴**:
- INFRA-02·03·04·05 가 `backend/build.gradle.kts` + `application.yml` 공유
- 메인 세션이 "공유 파일 충돌 위험" 을 명분으로 "다음 세션 순차 진행" 으로 미룸
- TPM 분해 자체를 재검토하지 않음

**올바른 처리**:
- 같은 wave 에 4건 동시 스폰 후 머지 시 충돌 해소
- 또는 TPM 분해 단계에서 공유 파일 수정을 별도 통합 티켓으로 추출

### 3. pr-reviewer 호출 누락

**spec 명시** (`feature.md` Step 4-B):

> 각 PR 마다 pr-reviewer 호출은 의무. PR 생성 직후 호출하지 않으면 머지 금지.
> "이미 잘 작성됐다", "단순 리네임이라 생략" 같은 우회 사유 금지.

**위반 패턴**:
- PR #4 (INFRA-02) — INFRA-02 서브에이전트가 자체 머지함
- pr-reviewer 호출 0회 → 머지됨

**근본 원인**:
- INFRA-02 서브에이전트 prompt 에 `gh pr merge` 금지 명시 누락
- 메인 세션이 머지 직후 사실 인지했으나 되돌리지 않음

### 4. 서브에이전트 자체 머지

**spec 명시** (`feature.md` Step 2 #5):

> Wave 통합 머지 — 각 워크트리의 PR 을 `dev` 브랜치에 순차 머지한다.

→ 메인 오케스트레이터의 책임. 서브에이전트가 머지 권한 행사 시 (a) pr-reviewer 게이트 우회 (b) wave 통합 머지 순서 파괴.

**위반 패턴**:
- PR #4 INFRA-02 서브에이전트가 `gh pr merge` 실행 → 자체 머지

## 정정 조치 — hook + harness rule 추가

### 신규 hook 4종

| hook | 시점 | 동작 |
|---|---|---|
| `agent-merge-guard.sh` | PreToolUse Agent | prompt 에 `gh pr merge`/`gh api .../merge`/`git merge --ff-only (dev\|main\|master)` 포함 시 deny |
| `pr-review-gate.sh` | PreToolUse Bash | `gh pr merge <N>` 실행 시 `.claude/cache/pr-reviews.json` 에 PR <N> 기록 없으면 deny |
| `pr-review-tracker.sh` | PostToolUse Agent | subagent_type=pr-reviewer 호출 감지 → prompt 에서 PR 번호 파싱 → 기록 갱신 |
| `wave-fanout-guard.sh` | PreToolUse Agent | ready ≥ 2 & inFlight = 0 인 상태에서 Agent 호출 시 stderr 경고 (advisory) |

### harness-rules.json `feature_pipeline` 섹션

5개 규약 명문화:
- `wave_fanout` — 한 메시지에 ready 전체 스폰
- `wave_internal_conflict_resolution` — wave 안에서 충돌 해소
- `pr_reviewer_mandatory` — PR 마다 pr-reviewer 의무
- `subagent_no_self_merge` — 서브에이전트 머지 권한 없음
- `wave_state_invariant` — state 파일 transition 규칙
- `completion_audit` — harness-auditor 5항목 0건

### settings.json 등록

```json
"PreToolUse": [
  { "matcher": "Bash", "hooks": [..., "pr-review-gate.sh"] },
  { "matcher": "Agent", "hooks": ["agent-merge-guard.sh", "wave-fanout-guard.sh"] }
],
"PostToolUse": [
  { "matcher": "Agent", "hooks": ["subagent-verify.py", "pr-review-tracker.sh"] }
]
```

## 메인 세션 행동 변경 사항

본 회고 이후 `/feature` 진행 시:

1. **wave 진입 시 ready 셋 전수를 같은 메시지에 spawn** — batch 분할 금지
2. **공유 파일 충돌은 wave 안에서 해소** — 다음 세션으로 미루지 않음
3. **PR 생성 직후 pr-reviewer 호출** — verdict transcript 명시 후 머지
4. **서브에이전트 prompt 에 `gh pr merge` 단계 명시 금지** — push + PR 생성까지만

## 검증

- agent-merge-guard.sh: 다음 Agent 호출 prompt 에 `gh pr merge` 포함 시 deny 동작 확인 필요
- pr-review-gate.sh: pr-reviewer 미호출 PR 의 `gh pr merge` deny 동작 확인 필요
- wave-fanout-guard.sh: ready ≥ 2 인 상태 진입 시 경고 출력 확인 필요

다음 wave 진입(Wave 2 잔여 INFRA-03/04/05 완료 후 후속 ready 셋 진입 시) 에 hook 동작 실 검증.

## 부록 — 작업 속도 저하 진단 (사용자 후속 질의 후 추가)

### 원인 7가지 (영향도 순)

1. **isolation 실패 → main worktree 공유 → Gradle 직렬화** (가장 큰 원인)
   - 3 agent 가 같은 `backend/build/` 디렉토리에서 `./gradlew test` → Gradle daemon lock 경합 → 사실상 직렬 실행
   - 측정값: INFRA-05 agent 50 분 (테스트 7건), INFRA-03 60 분+ (진행 중)
   - 원인: agent prompt 의 "isolated worktree 머물러라" 명시만으로는 `cd /Users/biuea/sports-application/...` 절대 경로 cd 를 막지 못함

2. **TPM 분해 실패** (근본)
   - INFRA-02/03/04/05 모두 `build.gradle.kts` + `application.yml` 수정 → 같은 wave 안에 둬도 build dir 경합으로 직렬화
   - 분해 단계에서 `INFRA-02b: 공통 인프라 deps 일괄 등록` 통합 티켓으로 추출했어야 함

3. **Testcontainers 부팅 비용**
   - MongoDB ~20s, Confluent Kafka ~40s, Redis ~5s
   - 테스트 클래스마다 재기동 시 누적 큼 — `.withReuse(true)` 없으면 매 클래스마다 fresh container

4. **agent 자체 디버깅 루프**
   - INFRA-03 agent 로그: BaseIntegrationTest 바이트코드 javap 분석까지 진행 — 깊은 retry
   - sonnet 4.6 implementer 였다면 1/3 시간 내에 비슷한 깊이 도달

5. **Docker 이미지 pull (초기 1 회)**
   - mongo:7 / cp-kafka:7.6.1 ~600MB

6. **agent transcript 누적**
   - INFRA-03 / INFRA-04 transcript 각 477KB (~100K 토큰) — 매 turn 컨텍스트 재처리 API latency 누적

7. **모델 선택** (opus 4.7 implementer)
   - parallel-tickets 스킬의 권장: 팀원 = sonnet 4.6
   - opus 가 sonnet 대비 implementer 작업에서 3–4 배 느림

### 적용된 개선 (속도)

| ID | 항목 | 적용 |
|----|------|------|
| A | agent prompt 에 절대 경로 cd 금지 명시 (4 줄) | `commands/feature.md` Step 3 — "Worktree 격리 강제" 섹션 |
| B | Testcontainers reuse 활성 | `~/.testcontainers.properties` (이미 활성) + agent prompt 에 `.withReuse(true)` 가이드 |
| C | implementer = sonnet, 메인 = opus 모델 분기 | `commands/feature.md` Step 3 — "모델 분기" 섹션 |
| D | `worktree-isolation-guard.sh` hook | PreToolUse Bash — agent worktree 컨텍스트에서 main 경로 cd / git -C / gradlew 직접 호출 차단 |
| E | TPM 분해 실패 기준 문서화 | `commands/feature.md` Step 2 — "TPM 분해 실패 기준" 표 + `harness-rules.json#feature_pipeline.tpm_decomposition_failure` |

### 검증

- 다음 wave 진입 시 sonnet 모델 implementer 호출 → wall-clock 비교
- worktree-isolation-guard 가 CLAUDE_PROJECT_DIR 격리 worktree 인 agent 의 `cd /Users/biuea/sports-application/...` 차단 동작
- TPM 분해 단계에서 build.gradle.kts 공유 시 통합 티켓 추출 권고 발동

## 참고

- `.claude/commands/feature.md` — 원 spec (Step 3 + Step 2 보강됨)
- `.claude/harness-rules.json` `feature_pipeline` 섹션 — 6→9 규약으로 확장
- `.claude/hooks/workflow-gates/{agent-merge-guard,pr-review-gate,pr-review-tracker,wave-fanout-guard,worktree-isolation-guard}.sh` — 강제 구현체
- `.claude/settings.json` — hook 등록
- `~/.testcontainers.properties` — reuse 활성
