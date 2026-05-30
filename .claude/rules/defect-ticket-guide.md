# 결함 티켓 md 작성 가이드

`/qa` 파이프라인의 qa-defect-router가 산출하고 be-implementer/fe-implementer가 입력으로 받는 **결함 md 파일**의 형식과 규칙.

> 정식 신기능 티켓 형식은 [`ticket-guide.md`](./ticket-guide.md)에 정의돼 있습니다. 본 문서는 **QA 결함에 한정한 추가 규칙**만 다룹니다.

## 위치

```
.analysis/outputs/qa/{YYYYMMDD}_{topic}/defects/
├── _summary.md
├── DEF-001-{slug}.md
├── DEF-002-{slug}.md
└── ...
```

`DEF-{NNN}-{slug}`:
- `NNN` — 산출물 디렉토리 내 일련번호 (3자리, 0 padding). 글로벌 일련번호가 아님 — `/qa` 1회 실행당 1부터 시작.
- `slug` — 한 줄 요약을 케밥케이스로

Jira 티켓 ID는 사용자가 검토 후 `jira-ticket` skill로 등록할 때 부여됩니다 — 결함 md 단계에서는 Jira ID 없음.

## md 구조 (필수 섹션)

```markdown
# DEF-{NNN} {한 줄 제목}

## 메타
- layer: FE | BE | INFRA | AMBIGUOUS
- severity: Critical | Major | Minor
- auto-fix-eligible: true | false
- source-scenario: E2E-{NN}-{NN} | LOAD-{NN}
- detected-at: {ISO 8601}
- environment: docker-compose.qa.yml (commit {sha})
- related-pr: #{number} | none
- related-ticket: <TICKET-ID> | none

## 분류 근거
{어떤 artifact의 어떤 라인을 보고 layer/severity를 결정했는지}

## 재현 단계
1. ...
2. ...
3. ...

## 기대 동작
{시나리오 md의 Then 그대로}

## 실제 동작
{관찰된 결과 — 스택트레이스·에러·HTTP 코드 그대로}

## 영향 범위
- 영향 사용자: {추정}
- 영향 화면/엔드포인트: {경로}
- 데이터 영향: 없음 | 있음 ({상세})

## 아티팩트
- [trace](../artifacts/{slug}/trace.zip)
- [screenshot](../artifacts/{slug}/screenshot-step3.png)
- [console](../artifacts/{slug}/console.log)
- [network HAR](../artifacts/{slug}/network.har)
- [load raw.log](../load-results/{slug}/raw.log)

## 의심 코드 경로
- {repo}/{path}:{line} — {왜 의심하는지}

## 자동 수정 지시 (auto-fix-eligible=true인 경우만)
대상 에이전트: be-implementer | fe-implementer
작업 범위:
- {결함에 한정 — 인접 코드 리팩토링 금지}
- TDD 사이클: 결함 재현 테스트 RED → fix → GREEN
- 테스트 위치 제안: {경로}
- 예상 변경 파일 수: {개수} (3개 초과 시 사람 검토 권장)
```

## auto-fix-eligible 판정 규칙

`true`인 경우만 `/qa` Step 4에서 be-implementer/fe-implementer가 워크트리에서 자동 호출됩니다.

| 조건 | auto-fix-eligible |
|---|---|
| `layer: FE` + `severity: Critical|Major` | true |
| `layer: BE` + `severity: Critical|Major` | true |
| `layer: INFRA` | false (사람 처리 — docker-compose/k8s/config 영역) |
| `layer: AMBIGUOUS` | false (분류 자체가 불확실) |
| `severity: Minor` | false (백로그) |
| 같은 결함이 직전 `/qa` 회귀에서도 발견됨 + 자동 수정 PR이 머지됐는데 재발 | false (반복 실패 — 사람이 근본 원인 파악) |

## Severity 분류 가이드

| severity | 기준 | 대응 SLA (제안) |
|---|---|---|
| Critical | 결제·로그인·핵심 비즈니스 플로우 실패. 데이터 손실 가능 | 당일 핫픽스 |
| Major | 주요 기능 실패. 회피 가능한 워크어라운드 존재 | 다음 스프린트 |
| Minor | UI 깨짐·문구 오류·비핵심 케이스 | 백로그 |

부하 결함은:
- p95이 목표의 200% 초과 → Critical
- p95이 목표의 110~200% → Major
- p95이 목표의 100~110% → Minor

## 재현 단계 작성 규칙

- **사용자 액션 기준**으로 작성. 코드 호출이 아닌 클릭/입력으로:
  - ✅ "내 시설 카드 → 편집 버튼 클릭"
  - ❌ "FacilityController.update() 호출"
- 사전 조건은 fixture 이름으로 명시 (`user-fixture-A` 같은). 임의 데이터값 나열 금지.
- 단계는 5단계 이내. 그 이상이면 시나리오 자체가 너무 큼 → 결함 재현용 시나리오를 좁힐 것.

## 의심 코드 경로 추정 규칙

qa-defect-router는 다음을 단서로 의심 코드를 추정:
- network HAR의 실패한 API path → BE Controller 매핑
- console 스택트레이스의 파일 경로
- PR diff의 변경 파일 중 결함과 매칭되는 것
- `git grep`으로 에러 메시지 문자열 검색

**추측이 어렵거나 단서가 없으면 비워두기**. 잘못된 의심 경로는 be-implementer/fe-implementer를 엉뚱한 곳으로 보내므로 빈 칸이 차라리 낫음.

## 자동 수정 지시 작성 규칙

be-implementer/fe-implementer는 [`be-code-convention.md`](./be-code-convention.md)·CLAUDE.md를 따라 작업합니다. 결함 md의 `자동 수정 지시`는 다음만 포함:

- **작업 범위 제한** — "결함 한정. 인접 리팩토링 금지" 명시 (CLAUDE.md §3 정밀한 수정)
- **TDD 강제** — "재현 테스트를 먼저 RED로 작성"
- **테스트 위치 제안** — 결함이 발생한 도메인의 기존 테스트 디렉토리
- **예상 변경 파일 수** — 추정값. 초과 시 사람 검토 권장 신호

수정 방법(어떤 코드를 어떻게 고치라)은 **명시하지 않음**. 에이전트가 직접 판단 — 우리는 결함이 무엇인지만 알려줌.

## 인라인 임베드 발췌 규칙

`/qa` Step 4에서 결함 md 내용을 be-implementer/fe-implementer prompt에 인라인 임베드합니다. prompt가 비대해지지 않도록 다음 규칙을 따릅니다:

- **스택트레이스**: 첫 20줄 + 마지막 5줄만 인용. 중간은 "...트레이스 중략..." 표기
- **HAR**: 실패한 요청 1건의 method/url/status/response-body 발췌만
- **console 로그**: 에러 라인 ±5줄 컨텍스트만
- 원본 전문은 아티팩트 링크로 참조 — 에이전트는 메인 워크트리에 접근할 수 없으므로 PR 본문에 링크만 명시

## Jira 등록 시 변환 (사람이 검토 후 수동)

결함 md → Jira 변환 시 `jira-ticket` skill 사용:

| md 필드 | Jira 필드 |
|---|---|
| `# DEF-001 제목` | Summary: `[QA] {제목}` |
| `layer` | Component (FE/BE/INFRA) |
| `severity` | Priority (Critical→Highest, Major→High, Minor→Medium) |
| `재현 단계` + `기대` + `실제` | Description |
| `아티팩트` | Attachment 링크 |
| `의심 코드 경로` | Description의 "추정 영역" 섹션 |
| `auto-fix-eligible: true`로 자동 PR이 생성됨 | Linked PR |

## 안티 패턴

- 분류 근거 없이 layer/severity만 단언 → router의 판단 검증 불가
- 재현 단계가 "테스트가 실패한다"로 끝남 → 사용자 액션 기준 단계 부재
- auto-fix-eligible을 임의로 부여 → 룰 표를 무시하면 워크트리만 늘어남
- 의심 코드 경로에 결함과 무관한 파일 다수 나열 → be-implementer가 엉뚱한 곳을 만짐
- 한 결함 md에 여러 독립 결함을 묶음 → 1 결함 = 1 md = 1 PR 원칙 위반

## 참고

- [ticket-guide](./ticket-guide.md) — 정식 신기능 티켓 형식
- [qa-scenario-guide](./qa-scenario-guide.md) — source-scenario의 출처
- [be-code-convention](./be-code-convention.md) — be-implementer가 따르는 규칙
- [pr-guide](./pr-guide.md) — 자동 수정 PR 브랜치·제목
- [COMPLETION-RULE](./COMPLETION-RULE.md) — 결함 md는 산출물로 인정되는 강제 deliverable
