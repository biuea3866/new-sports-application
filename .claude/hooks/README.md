# `.claude/hooks/`

프로젝트 로컬 hook 스크립트 모음. 목적별 하위폴더로 분류되며,
[`_run.sh`](./_run.sh) 한 launcher를 통해 안전하게 호출된다.

> 등록 위치는 [`.claude/settings.json`](../settings.json) 한 곳. **전역(`~/.claude/settings.json`)에는
> 등록하지 않는다** — 한 프로젝트의 디렉토리 이동/삭제가 다른 프로젝트의 hook을 깨뜨리지 않도록 격리한다.

## 구조

```
hooks/
├── _run.sh                  ← graceful launcher (모든 hook이 이걸 거침)
├── README.md
├── code-rules/              ← 코드/명령 패턴 강제 (harness-rules.json 기반)
│   └── harness-check.py
├── verify/                  ← 사후 검증 (PostToolUse)
│   └── subagent-verify.py
├── workflow-gates/          ← 워크플로우 단계 게이트 (Bash)
│   ├── feature-gate.sh           (브랜치/파이프라인 단계 + 메인 워크트리 직접 구현 차단)
│   ├── feature-tdd-gate.sh
│   ├── agent-worktree-guard.sh   (구현 에이전트 워크트리 격리 강제)
│   ├── agent-merge-guard.sh      (구현 에이전트 PR 머지 차단)
│   ├── wave-fanout-guard.sh      (wave 병렬 스폰 advisory 경고)
│   ├── worktree-isolation-guard.sh (워크트리 에이전트의 main 경로 침범 차단)
│   ├── worktree-quota-guard.sh   (활성 워크트리 임계 초과 시 신규 생성 차단)
│   ├── completion-merge-gate.sh  (Stop/SubagentStop — dev 미머지 작업 1회 차단)
│   ├── qa-env-gate.sh            (/qa — .env.local BACKEND_URL 검증)
│   ├── qa-fe-mode-gate.sh        (/qa — next dev 모드 차단, production 빌드 강제)
│   ├── qa-reverify-gate.sh       (/qa — reverify-report.md 없으면 down 차단)
│   ├── qa-pr-defect-gate.sh      (/qa — PR 결함 게이트)
│   ├── pr-review-gate.sh
│   ├── pr-review-tracker.sh
│   ├── push-review.sh       (현재 미등록 — 비용 발생 가능, 옵션)
│   └── push-test.sh
└── util/                    ← 수동 호출용 유틸리티 (hook 아님)
    ├── lockfile-writer.py
    └── resource-resolver.py
```

## `_run.sh` — graceful launcher

모든 hook 호출은 settings.json에서 다음 형식으로 등록한다:

```json
{ "type": "command",
  "command": "bash /abs/.claude/hooks/_run.sh /abs/.claude/hooks/<dir>/<script> [args...]" }
```

`_run.sh`는 다음을 보장한다:

- 대상 스크립트 파일이 **없으면 silently exit 0** (block 안 함)
  → 스크립트 이동/삭제로 인한 환경 잠금 deadlock 방지
- 있으면 정상 실행하고 **exit code를 그대로 전달**
  → exit 2 블로킹 시맨틱 보존
- `.py`는 `python3`로, `.sh`는 `bash`로 launch (확장자 기반)

## 현재 등록된 hook (settings.json 기준)

| Event | Matcher | 스크립트 | 의도 |
|---|---|---|---|
| PreToolUse | `Write\|Edit` | `code-rules/harness-check.py file-guard` | 파일 경로/이름 가드 |
| PreToolUse | `Write\|Edit` | `code-rules/harness-check.py code-pattern` | `harness-rules.json` 금지 패턴 차단 |
| PreToolUse | `Write\|Edit` | `workflow-gates/feature-gate.sh` | ① main/dev 브랜치 + 파이프라인 미진입 상태 구현 코드 차단 ② **APPROVED/IMPLEMENTING 중 메인 워크트리 직접 구현 차단(갭 B)** — `.claude/worktrees/` 밖 구현 파일은 deny, 머지 중(MERGE_HEAD)이면 예외 |
| PreToolUse | `Bash` | `code-rules/harness-check.py git-guard` | git 명령 가드 (예: main 직접 push) |
| PreToolUse | `Bash` | `code-rules/harness-check.py bash-file-guard` | bash 명령이 건드리는 파일 가드 |
| PreToolUse | `Bash` | `workflow-gates/feature-tdd-gate.sh` | push 시 src/main 변경 있는데 src/test 변경 없으면 차단 |
| PreToolUse | `Bash` | `workflow-gates/push-test.sh` | push 전 변경된 Gradle 모듈 테스트 자동 실행 |
| PreToolUse | `Bash` | `workflow-gates/pr-review-gate.sh` | PR 생성 게이트 |
| PreToolUse | `Bash` | `workflow-gates/worktree-isolation-guard.sh` | 워크트리 에이전트가 main 워크트리 경로로 cd/git 침범 차단 |
| PreToolUse | `Bash` | `workflow-gates/worktree-quota-guard.sh` | **활성 워크트리 ≥ `WORKTREE_QUOTA`(기본 80)면 `git worktree add` 차단** — 누적 잔재 폭주 백스톱. 먼저 `util/cleanup-worktrees.sh` 정리 요구 |
| PreToolUse | `Bash` | `workflow-gates/qa-env-gate.sh` | **/qa — Playwright 실행 전 `web/.env.local`의 `BACKEND_URL` 검증** (누락 시 portal SSR 500 거짓 fail 방지) |
| PreToolUse | `Bash` | `workflow-gates/qa-fe-mode-gate.sh` | **/qa — `next dev` 프로세스 감지 시 차단** (E2E는 production 빌드 `next start` 강제) |
| PreToolUse | `Bash` | `workflow-gates/qa-reverify-gate.sh` | **/qa — `reverify-report.md` 없으면 `docker-compose down` 차단** (재검증 생략 방지) |
| PreToolUse | `Bash` | `workflow-gates/qa-pr-defect-gate.sh` | **/qa — PR 결함 게이트** |
| PreToolUse | `Agent` | `workflow-gates/agent-worktree-guard.sh` | **구현 에이전트(`*implementer`/`tdd-implement`/`db-schema-writer`/`kafka-topic-provisioner`)가 워크트리 격리 없이 스폰되면 차단(갭 A)** — `isolation:"worktree"` 또는 prompt 에 `.claude/worktrees/` 경로 必 |
| PreToolUse | `Agent` | `workflow-gates/worktree-quota-guard.sh` | **활성 워크트리 ≥ `WORKTREE_QUOTA`(기본 80)면 `isolation:"worktree"` Agent 스폰 차단** |
| PreToolUse | `Agent` | `workflow-gates/agent-merge-guard.sh` | 구현 에이전트의 `gh pr merge` 직접 수행 차단 |
| PreToolUse | `Agent` | `workflow-gates/wave-fanout-guard.sh` | wave ready≥2 직렬 스폰 시 경고 (advisory, 차단 안 함) |
| PostToolUse | `Agent` | `verify/subagent-verify.py` | 구현 서브에이전트의 push/PR/리뷰어 호출 검증 |
| PostToolUse | `Agent` | `workflow-gates/pr-review-tracker.sh` | PR 리뷰 추적 |
| Stop | (전체) | `workflow-gates/completion-merge-gate.sh` | **종료 시 미커밋/미push/`origin/dev` 미머지 작업이 있으면 1회 차단** — "끝났다"면서 로컬·feature 브랜치에 작업 남기는 거짓 완료 방지. `stop_hook_active` 면 재차단 안 함 |
| SubagentStop | (전체) | `workflow-gates/completion-merge-gate.sh` | 서브에이전트가 자기 워크트리에 미머지 작업 남기고 종료 시 동일 검사 |
| SessionStart | (전체) | `util/cleanup-worktrees.sh --auto` | **세션 시작 시 `origin/dev` 병합 완료 + clean + unlocked 워크트리 자동 제거** — locked·미커밋·미머지는 절대 건드리지 않음 |

### git stash 신규 생성 차단 (`harness-rules.json` git_guard)

`no-create-stash` 룰이 `git stash` / `stash push` / `stash save` 등 **신규 stash 생성**을 차단합니다.
stash 누적·유실을 막고 commit(미완성이면 WIP 커밋) 또는 별도 브랜치 분리를 강제합니다.
정리용 `git stash list/show/pop/apply/drop/clear/branch` 는 허용됩니다.

### 워크트리·완료 관련 가드 요약

| 현상 | 차단 메커니즘 |
|---|---|
| 워크트리를 만들고 제거 안 해 누적 | SessionStart 자동정리(병합+clean만) + 개수 임계 가드(백스톱) |
| stash 가 쌓임 | git_guard `no-create-stash` 가 신규 생성 차단 |
| "끝났다"면서 dev 미머지·미push·미커밋 | Stop/SubagentStop `completion-merge-gate.sh` 가 1회 차단 |

> `util/cleanup-worktrees.sh` 는 인자 없이 실행하면 dry-run(제거 대상만 보고), `--execute` 로 실제 제거, `--auto` 는 SessionStart 용 조용한 모드. 제거 조건은 **메인 아님 + unlocked + uncommitted 없음 + `origin/dev` 병합 완료** 4개를 모두 충족할 때만 → 작업물 유실 0.

> 미등록: `workflow-gates/push-review.sh` — push 마다 `claude -p`로 자동 PR 리뷰를 돌리는 게이트.
> 비용 발생 가능성 때문에 기본 비활성. 필요 시 settings.json에 위 패턴으로 등록.

## `code-rules/harness-check.py` 서브커맨드

| check_type | 의미 |
|---|---|
| `file-guard` | 작성 대상 파일 경로/이름 규칙 |
| `code-pattern` | 파일 내용 금지 패턴 검사 |
| `git-guard` | git 명령 가드 |
| `bash-file-guard` | bash 명령의 파일 경로 가드 |
| `jira-guard` | 활성 Jira 티켓 컨텍스트 검사 |
| `pipeline-gate` | 파이프라인 단계 진입 조건 검사 |

규칙은 [`harness-rules.json`](../harness-rules.json)에 집중. 스크립트 수정 없이 JSON만 편집.

## `verify/subagent-verify.py` 동작

PostToolUse(Agent) 시점에 호출:

1. 종료된 서브에이전트의 `subagent_type` 확인 — `*implementer`, `tdd-implement`,
   `kotlin-spring-impl` 등 **구현 역할**일 때만 검증 가동.
2. 트랜스크립트(JSONL)를 읽어 해당 서브에이전트의 도구 호출을 추적.
3. 세 항목 누락 여부 판정:
   - **원격 push** — `git push` / `gh pr create`
   - **PR 생성** — `gh pr create` / GitHub MCP `create_pull_request`
   - **리뷰어 호출** — `pr-reviewer` / `code-reviewer` / `be-senior` 등
4. 누락 시 `<system-reminder>` 형태로 메인 세션에 추가 컨텍스트 주입 → 다음 턴에서 강제 처리.

PostToolUse는 block 불가 (도구 이미 실행됨). 관찰·강제는 `additionalContext` 채널.

## 새 hook 추가 절차

1. 목적에 맞는 하위폴더 선택 (또는 새로 생성):
   - **`code-rules/`** — 파일 내용/명령 패턴 자체에 대한 규칙 (`harness-rules.json` 데이터 주도)
   - **`workflow-gates/`** — 워크플로우 단계·브랜치·테스트 조건 (Bash 스크립트가 자연스러움)
   - **`verify/`** — PostToolUse 사후 검증
   - **`util/`** — hook은 아니지만 같이 두면 좋은 유틸리티 (settings.json 등록 X)
2. 스크립트 작성 (`.py` 또는 `.sh`). stdin으로 Claude Code hook payload(JSON) 수신.
   - 차단: stderr 출력 + `exit 2`
   - 통과: `exit 0` (선택적으로 `hookSpecificOutput` JSON을 stdout으로)
3. 실행 권한: `chmod +x .claude/hooks/<dir>/<name>`
4. [`.claude/settings.json`](../settings.json)에 `_run.sh` 래퍼로 등록:
   ```json
   {
     "matcher": "Write|Edit",
     "hooks": [
       { "type": "command",
         "command": "bash /Users/biuea/sports-application/.claude/hooks/_run.sh /Users/biuea/sports-application/.claude/hooks/<dir>/<script>" }
     ]
   }
   ```
5. 문법 검증: `python3 -m py_compile <script.py>` 또는 `bash -n <script.sh>`.

## 격리 원칙

- 전역 hook 금지. 모든 hook은 프로젝트 내부에서 닫혀야 한다.
- hook 스크립트는 자신이 사는 디렉토리(`__file__`) 기준으로 경로를 풀어 프로젝트 이동에 견고하게.
- 외부 의존성(MCP 토큰, 사용자명 등)은 하드코딩 금지. 환경변수나 `settings.local.json`에서.
- 스크립트 이동/리네임 전에 settings.json을 먼저 업데이트 — `_run.sh`가 deadlock을 막아주지만, 그래도 좋은 습관.
