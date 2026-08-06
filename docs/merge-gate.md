# 머지 게이트 (Merge Gate)

`main` 이 red 가 되는 것을 push 시점에 막는 저비용 게이트. 근거는 실측 사고다 — 2026-08-01~03 사이
머지된 PR 7건(#383·#385·#386·#390·#391·#393·#394)이 각각 `main` 에 red 를 남겼고, 공통 원인은
**게이트가 정적분석·아키텍처 규칙·컨텍스트 로드를 강제 실행하지 않은 것**이었다
(`후속-리스크-등록부.md` R-21·R-24).

## 무엇을 검사하는가

| tier | 검사 | 명령 | 이 tier 만 잡는 실패 |
|---|---|---|---|
| 1 | 정적분석 8모듈 전수 | `detekt harnessCheck --rerun-tasks` | Gradle `UP-TO-DATE` 캐시가 건너뛰던 detekt 위반 (R-21 — 4개 PR 연속 통과) |
| 2 | 아키텍처 규칙 | `:bootstrap:test --tests com.sportsapp.architecture.*` | 레이어·모듈 그래프·컨텍스트 맵 위반 (모듈 단위 테스트는 통과) |
| 3 | 컨텍스트 로드 풀부팅 | `:bootstrap:test --tests …ApplicationContextLoadGateTest …HealthEndpointIntegrationTest` | 프로파일 게이트 빈 주입으로 `test-jpa` 컨텍스트가 붕괴 (R-24 — 73클래스 red) |

**전체 테스트 스위트의 대체물이 아니다.** 변경 모듈 테스트(글로벌 push 훅이 요구) 위에 얹는
바닥선이다 — 위 3종은 어떤 변경으로도 깨질 수 있고, 깨지면 곧 `main` red 다.

## 왜 토큰이 아니라 아티팩트인가

기존 게이트는 `git push … # tests-passed` / `gh pr merge … # p3-reflected` **토큰 명예제**였다.
실행 여부를 검증하지 않으므로 "돌렸다"는 단언만으로 통과한다. 4개 PR 연속 통과가 그 구조의 결과다.

이 게이트는 단언 대신 리포트를 본다:

```
scripts/ops/merge-gate.sh          →  backend/build/merge-gate/report.json
                                        { fingerprint, verdict, tiers, steps[] }
                                             ↑
.claude/hooks/merge-gate-verify.sh  ──── ① 존재 ② verdict=PASS ③ tiers=1,2,3 ④ 지문 일치
```

**지문(fingerprint)** = `HEAD 커밋 + 추적 파일 미커밋 변경 + 미추적 파일 내용`의 해시
(`scripts/ops/lib/gate-fingerprint.sh`). 게이트 실행 후 코드를 한 줄 고치면 지문이 달라져 리포트가
무효화된다 — 검사받지 않은 코드가 나가지 않는다. `build/` 는 `.gitignore` 대상이라 리포트를 쓰는
행위 자체는 지문을 바꾸지 않는다.

**우회 토큰은 없다.** 게이트를 돌리지 않으면 통과할 방법이 없다.

## 사용

**순서는 커밋 → 게이트 → push** 다. 지문에 `HEAD` 가 들어가므로 게이트를 먼저 돌리고 커밋하면
`HEAD` 가 바뀌어 리포트가 무효화된다.

```bash
git commit -m "…"                    # ① 먼저 커밋
./scripts/ops/merge-gate.sh          # ② 전 tier — push·머지 전에 이것만 돌리면 된다
git push origin <branch>             # ③ 훅이 리포트를 검증

./scripts/ops/merge-gate.sh --tier=1 # 정적분석만 (자기검증·디버깅용, push 통과 불가)
./scripts/ops/merge-gate.sh --print-fingerprint
```

실패 시 단계별 로그는 `backend/build/merge-gate/logs/{static,arch,fullboot}.log`.

### 게이트가 요구되지 않는 변경

`backend/**/*.kt(s)`·detekt 설정·`gradle.properties`·`settings.gradle.kts` 가 하나도 바뀌지 않은
push(문서·FE·compose 전용)는 훅이 게이트를 요구하지 않는다. 판정은 훅이 직접 diff 로 하며
작업자가 주장할 수 없다.

## 자기검증 — 게이트를 신뢰하는 근거

게이트 코드를 읽은 것이 아니라 **고의 위반이 차단되는 것을 관측한 기록**이 신뢰의 근거다.

```bash
./scripts/ops/merge-gate-selftest.sh --fast   # 훅 판정 11케이스 (수 초)
./scripts/ops/merge-gate-selftest.sh --full   # + 게이트 실행 4케이스 (수십 분, 컨테이너)
```

`--full` 은 위반을 실제로 주입해 tier 별 차단을 확인한다 — `!!`(harnessCheck)·`ThrowsCount`
3개(detekt, R-21 유입 유형)·domain→infrastructure import(아키텍처)·프로파일 게이트 빈 주입
(R-24 재현). 각 케이스는 주입 파일을 반드시 원복하고, 추적 파일이 더러워진 채 끝나면 비-0 으로
알린다.

## 알려진 한계

| 한계 | 내용 |
|---|---|
| 로컬 훅 범위 | Claude Code 세션 밖(사람이 직접 터미널에서 push)에서는 훅이 돌지 않는다. 구조적 강제가 필요하면 PR 트리거 CI 가 다음 단계다 |
| tier 3 대표성 | 컨텍스트 로드 2개(`test-jpa`·기본 프로파일)만 본다. 프로파일이 늘면 `FULLBOOT_TESTS` 에 추가해야 한다 |
| 자기검증 C9 의 대상 빈 | R-24 의 원래 빈(`FacilityDomainService`)은 그 수정으로 test-jpa 스텁이 생겨 **더 이상 이 실패를 재현하지 않는다**. C9 는 스텁되지 않은 프로파일 게이트 빈을 쓴다 — 스텁 목록이 늘어 C9 가 통과하기 시작하면 대상 빈을 다시 골라야 한다(게이트가 약해진 것이 아니라 케이스가 무력해진 것) |
| detekt baseline | `backend/detekt-baseline.xml` 에 기존 이슈 38건이 등록돼 있다. 신규 위반은 baseline 을 통과하지 못하지만, "detekt 0건"은 baseline 등록분을 제외한 값이다 |

## 관련 문서

- `프로젝트/스포츠앱/MSA 물리분리/후속-리스크-등록부.md` — R-21·R-24 원본
- `~/.claude/rules/COMPLETION-RULE.md` §2 — 검증 아티팩트 원칙
