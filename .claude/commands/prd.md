---
description: 요구사항·컨텍스트를 받아 PRD(Product Requirements Document)를 작성하고 .analysis/outputs/에 저장합니다. 작성 후 Confluence 게시 또는 /feature·/implement 파이프라인 입력으로 바로 연결됩니다.
---

# /prd — PRD 작성

## 입력
`$ARGUMENTS` — 기능 아이디어, 변경 배경, 또는 간단한 요구사항 메모

---

## 파이프라인 개요

```
요구사항 메모 / 아이디어
    │
    ▼
[Step 1] 컨텍스트 수집
    │ 관련 코드·설계 파악, AS-IS 현황 분석
    ▼
[Step 2] PRD 초안 작성
    │ .analysis/outputs/{날짜}_{기능명}/prd.md 저장
    ▼
[Step 3] prd-reviewer 검수 (필수)
    │ prd-reviewer 에이전트로 누락·모순·실현가능성 점검 → 지적 반영
    ▼
[Step 4] 사용자 검토 게이트
    │ 검수 결과 + PRD 요약 출력 → 수정 요청 시 재작성
    ▼
[Step 5] 완료 출력
      파일 경로 + 다음 단계 안내 (/feature or /implement)
```

---

## Step 1 — 컨텍스트 수집

`$ARGUMENTS`를 분석해 다음을 파악한다:

- 이 기능이 영향을 주는 **서비스·레포** (`.architecture/` 스냅샷, `.claude/context/` 참조)
- **AS-IS 현황** — 현재 코드베이스에서 관련 부분 요약
- **변경 동기** — 왜 지금 이 기능이 필요한가
- **불명확한 부분** — 요구사항에서 모호하거나 빠진 항목

---

## Step 2 — PRD 작성

아래 구조로 PRD를 작성한다.

```markdown
# {기능명} PRD

## 배경 (Background)
{이 기능이 필요한 이유, 현재 문제점}

## 목표 (Goals)
- {측정 가능한 목표 1}
- {측정 가능한 목표 2}

## 비목표 (Non-Goals)
- {이번 범위에서 제외하는 것}

## 사용자 스토리 (User Stories)
| As a | I want to | So that |
|------|-----------|---------|
| ... | ... | ... |

## 기능 요구사항 (Functional Requirements)
### FR-01. {요구사항 제목}
{행위자, 트리거, 결과를 포함한 구체적 설명}

### FR-02. ...

## 비기능 요구사항 (Non-Functional Requirements)
- 성능: {응답시간, 처리량 등}
- 보안: {인증·인가 요구사항}
- 운영: {모니터링, 알림 요구사항}

## 제약 조건 (Constraints)
- {기술적·비즈니스 제약}

## 영향 범위 (Scope)
| 레포 | 변경 유형 | 설명 |
|------|---------|------|
| ... | 신규/수정/삭제 | ... |

## 오픈 이슈 (Open Issues)
| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | ... | PM | ... |

## 마일스톤
| 단계 | 내용 | 목표일 |
|------|------|--------|
| ... | ... | ... |
```

**저장 경로**: `.analysis/outputs/{YYYYMMDD}_{기능명}/prd.md`

---

## Step 3 — prd-reviewer 검수 (필수)

PRD 초안 작성 직후 **반드시** `prd-reviewer` 에이전트를 호출해 검수한다. 이 단계는 생략할 수 없다.

- 호출: `prd-reviewer` 서브에이전트에 작성한 `prd.md` 경로(들)를 전달
- 점검 항목: 요구사항 누락, FR 간 모순, AS-IS 근거의 코드 사실 일치 여부, 실현 가능성, 오픈 이슈 충분성, 영향 범위 정확성
- 결과 처리:
  - **Must Fix** 지적 → PRD를 수정한 뒤 재검수 (지적 0건까지 반복)
  - **Nice to Have** 지적 → 오픈 이슈로 남기거나 반영
- 검수 결과(지적 목록 + 반영 내역)를 Step 4 요약에 포함한다.

> 검수 없이 "PRD 완료"를 단언하지 않는다 (`rules/COMPLETION-RULE.md` §2 — "PRD 분석 완료"는 reviewer 산출물 첨부 시에만 인정).

---

## Step 4 — 사용자 검토 게이트

검수 반영 후 다음 요약을 출력하고 사용자 확인을 기다린다:

```
## PRD 요약 — {기능명}

목표: {핵심 목표 1줄}
영향 레포: {N}개 — {목록}
기능 요구사항: {N}건
오픈 이슈: {N}건 (PM 확인 필요)
prd-reviewer 검수: Must Fix {N}건 반영 / Nice to Have {N}건

전체 문서: {prd.md 경로}
```

**대기 액션**:
- "확인" / "OK" → Step 5 진행
- 수정 요청 → 해당 섹션 수정 후 재출력

---

## Step 5 — 완료 출력

```
PRD 작성 완료: {prd.md 경로}

다음 단계:
- 본격 신기능 (다중 레포): /feature {prd.md 경로}
- 단일 도메인 변경:        /implement {prd.md 경로}
- Confluence 게시:         Pipeline 탭 → 해당 단계 Confluence 작성 버튼
```

---

## 주의사항

- 코드·SQL을 직접 작성하지 않는다. PRD는 요구사항 문서이지 설계 문서가 아니다.
- 오픈 이슈가 있으면 임의로 결정하지 않고 목록으로 남긴다.
- 기능 요구사항은 "무엇을" 기술한다. "어떻게"는 TDD 단계에서 결정한다.
- **Step 3 prd-reviewer 검수는 필수**다. 검수를 건너뛰고 PRD를 완료 처리하지 않는다.
