# 부하 테스트 시나리오 작성 가이드

`/qa` 파이프라인이 생성·실행하는 **k6 부하 시나리오 md**의 형식과 규칙. qa-scenario-author·qa-load-tester·qa-defect-router가 공통 참조합니다.

## 시나리오 파일 위치

| 위치 | 용도 | 보존 |
|---|---|---|
| `qa/load/scenarios/{flow-slug}.md` | 영구 회귀 시나리오 | 레포 영구 |
| `qa/load/k6/{flow-slug}.js` | k6 스크립트 | 레포 영구 |
| `qa/load/k6/lib/` | 공통 헬퍼 (인증·시드·메트릭) | 레포 영구 |
| `qa/load/seeds/{flow-slug}.sql` | 부하 사전 시드 SQL | 레포 영구 |
| `.analysis/outputs/qa/{YYYYMMDD}_{topic}/scenarios/load/{slug}.md` | 1회성 시나리오 | 산출물 |
| `.analysis/outputs/qa/{YYYYMMDD}_{topic}/load-results/{slug}/` | 실행 결과 | 산출물 |

## md 구조

```markdown
# LOAD-{NN} {엔드포인트 또는 플로우}

## 메타
- target: GET /api/v1/... | E2E flow {flow-slug}
- objective: latency | throughput | spike | soak
- duration: 5m | 30m | 2h
- related-files: [...]
- related-ticket: <TICKET-ID> | none

## 목표 임계
| 지표 | 목표 |
|---|---|
| RPS | {목표} |
| p95 | < {ms} |
| p99 | < {ms} |
| error rate | < {%} |
| 자원 (선택) | CPU < {%}, Mem < {gb} |

## 가상 사용자 패턴
- ramp-up: 0 → {N} VU over {duration}
- steady: {N} VU for {duration}
- ramp-down: {N} → 0 VU over {duration}

## 사전 시드
- DB 시드: qa/load/seeds/{slug}.sql
- 토큰 발급: {endpoint 또는 fixture}
- 캐시 워밍업: {선택}

## 검증
- 응답 body 검증: {필요 시 — sampling rate 명시}
- 사이드이펙트 검증: {DB row count, 큐 메시지 수 등}

## 측정 노이즈 경고
- 본 시나리오는 로컬 docker-compose 환경에서 실행되므로 **절대치 비교용이 아닌 회귀 추세 추적용**입니다.
- 임계 위반은 결함 후보일 뿐, 실제 결함은 운영 환경 부하로 재검증 필요.
```

## objective별 패턴 가이드

| objective | k6 stages | 측정 포인트 |
|---|---|---|
| latency | 일정 VU 유지 (ramp-up → steady → ramp-down) | p95·p99 |
| throughput | VU 점진 증가 → saturation point 탐색 | 최대 RPS, RPS-latency curve |
| spike | 짧은 시간에 VU 급증 (10s에 100배) → 회복 시간 측정 | 회복까지 걸린 시간, error spike 폭 |
| soak | 낮은 VU로 30m~2h | 메모리 누수, error rate 시간 변화 |

## 임계 설정 가이드

| 엔드포인트 유형 | p95 기본값 | error rate 기본값 |
|---|---|---|
| GET 단순 조회 (캐시 있음) | < 100ms | < 0.1% |
| GET 복합 조회 (조인 다수) | < 300ms | < 0.5% |
| POST 신규 생성 | < 500ms | < 0.5% |
| POST 복합 트랜잭션 (외부 호출 포함) | < 1000ms | < 1% |
| E2E 사용자 플로우 (5~7 step) | < 3000ms | < 1% |

위는 출발점이며 실제 SLO에 맞춰 조정. SLO 문서가 있으면 그 값을 우선.

## k6 스크립트 컨벤션

- 시나리오 ID를 `group()` 이름의 접두사로:
  ```js
  group("[LOAD-01] /api/v1/bookings GET", () => { ... });
  ```
- 인증 토큰·기본 헤더는 `qa/load/k6/lib/auth.js`에 분리. 매 스크립트에서 중복 작성 금지.
- 시드는 k6 실행 전 별도 step에서 적용 (스크립트 안에서 시드하지 않음 — 측정 노이즈)
- 응답 body 검증은 sampling. 매 응답 JSON 파싱은 비용이 큼:
  ```js
  if (__ITER % 100 === 0) check(res, { 'has id': (r) => r.json('id') !== undefined });
  ```
- 1개 스크립트 = 1개 시나리오. 여러 시나리오를 한 스크립트에 묶지 않음.

## 안전 규칙

- 운영 환경(`*.production`·실제 도메인) 대상 부하 실행 금지. qa-load-tester가 `QA_API_URL` 패턴 검사 후 차단.
- staging 대상 부하는 별도 승인 필요 — `/qa --target=staging` 같은 명시적 플래그가 있을 때만.
- 부하 결과 raw 데이터(`raw.json`)는 사이즈가 크므로 90일 후 자동 삭제 (산출물 보존 정책).

## 안티 패턴

- 모든 엔드포인트를 한 스크립트에 묶음 → 시나리오별 임계 추적 불가
- 인증 토큰을 매 VU마다 새로 발급 → 인증 서버에도 부하가 가서 측정 대상 분리 안 됨
- 측정값을 절대치로 비교 ("p95이 200ms 이하여야 한다" 단언) → 환경 차이 무시. **회귀 추세** 관점으로 비교
- 임계를 시나리오 md에 두지 않고 k6 스크립트에만 하드코딩 → qa-defect-router가 임계 위반을 분류할 수 없음

## 참고

- [qa-scenario-guide](./qa-scenario-guide.md) — E2E 시나리오와의 차이
- [defect-ticket-guide](./defect-ticket-guide.md) — 부하 결함 md 작성
- k6 공식 문서: https://grafana.com/docs/k6/
