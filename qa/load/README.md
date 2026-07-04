# qa/load/ — k6 부하 회귀 + 상시 트래픽 시뮬레이터(⑦)

## 디렉토리

```
qa/load/
├── scenarios/          # 부하 시나리오 md (qa-load-guide 형식)
├── k6/
│   ├── lib/            # 공통 헬퍼(auth·metrics·diurnal·pool·gapreport·readmix·writemix·b2bRegistration)
│   └── *.js            # 시나리오별 k6 스크립트(회귀 4종 + 상시 시뮬 4종: b2c-diurnal-read/write,
│                        #   b2b-diurnal, marketing-spike)
├── seeds/               # 부하 사전 시드 SQL(회귀용 + simulator-baseline.sql)
├── provision/            # synthetic 최초 프로비저닝(INFRA-03, 파트너 키·drop 개설 → .env.sim)
├── reseed/               # 10분 주기 synthetic 복원 배치(INFRA-08) + Dockerfile(INFRA-09)
├── sim/                  # k6-runner 컨테이너 진입점(entrypoint.sh, INFRA-09)
├── dashboards/           # Grafana 대시보드 JSON 산출물(simulator.json, INFRA-09 — FR-6)
└── results/              # 실행 산출물(gap report·로그, git 추적 제외)
```

아래 "상시 트래픽 시뮬레이터" 절은 24시간 상시 구동용(⑦, INFRA-01~09)이고, 그 위 실행법은
일회성 회귀 검증용이다 — 서로 다른 용도이니 혼동하지 않는다.

## 설치

```bash
# macOS
brew install k6

# Linux
sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

## 실행

```bash
# 단일 시나리오
cd qa/load
QA_API_URL=http://localhost:8080 \
  k6 run \
  --summary-export=results/bookings-get-summary.json \
  k6/example.bookings-get.js

# 전체 회귀
for script in k6/*.js; do
  k6 run "$script" || echo "FAIL: $script"
done
```

## 안전 규칙

- `QA_API_URL`이 `localhost` 또는 `*.local`이 아니면 실행 중단 (`lib/auth.js`가 검사).
- 운영 환경 부하 금지. staging 부하는 별도 승인 필요.
- 측정값은 회귀 추세 추적용 — 절대치 비교 금지.

자세한 시나리오 작성 규칙은 [`.claude/rules/qa-load-guide.md`](../../.claude/rules/qa-load-guide.md) 참조.

---

## 상시 트래픽 시뮬레이터 (⑦, INFRA-01~09)

근거: `../../.analysis`가 아니라 `프로젝트/스포츠앱/상시 트래픽 시뮬레이터/TDD.md`(설계) ·
`Tickets/INFRA-09-compose-sim-제어-대시보드-통합.md`(본 절이 갱신하는 통합 티켓).
**prod 환경 대상** — 24시간 일주기 곡선(B2C 조회 peak 2100·쓰기 peak 900, B2B peak 100) +
10분 주기 reseed + 예약된 마케팅 스파이크(20000 TPS 도전)를 backend 3 replica + nginx LB
위에서 상시 구동한다. 애플리케이션 도메인 코드·DB 스키마는 무변경.

### 사전 준비 (최초 1회)

```bash
# 1) backend 다중 인스턴스 + nginx LB + 관측 스택까지 먼저 기동
make sim-up-prod   # 또는 로컬 검증은 make sim-up

# 2) synthetic 프로비저닝 (파트너 키·마케팅 drop 개설 → qa/load/.env.sim에 기록)
QA_API_URL=http://localhost:8088 ./qa/load/provision/provision.sh
```

`.env.sim`은 시크릿(파트너 API 키 등)을 담을 수 있어 리포에 커밋하지 않는다
(`.env.sim.example`이 키 목록 참고용). `docker-compose.sim.yml`이 이 파일을 k6-runner·reseed에
`env_file`(없어도 무시, `required: false`)로 그대로 주입한다.

### 제어 (FR-7 — Makefile 타겟)

| 명령 | 동작 |
|---|---|
| `make sim-up` | 로컬(base+관측+LB+sim) 병합 기동 — 곡선 시작. 상태: 정지 → 구동중 |
| `make sim-up-prod` | prod 네임스페이스(`-p sports-prod`) 기동 |
| `make sim-pause` | k6-runner만 graceful stop(도착률 0 수렴). reseed·LB·backend·관측은 유지. 상태: 구동중 → 일시정지 |
| `make sim-down` / `make sim-down-prod` | k6-runner·reseed graceful stop. LB·backend·관측은 무영향(즉시 재개 가능). 상태: → 정지 |
| `make sim-ps` | k6-runner·reseed 컨테이너 상태 |
| `make sim-logs` | k6-runner·reseed 로그 팔로우(진행률·gap report stdout·reseed 사이클 이력) |
| `make sim-config` / `-dev` / `-prod` | 병합 config 정적 검증(exit code) |

graceful 보장: `docker-compose.sim.yml`의 k6-runner `stop_grace_period: 45s`가
`stop`/`down`에 자동 적용되어, k6 시나리오 기본 `gracefulStop`(30s) 동안 도착률이 0으로
수렴할 시간을 준 뒤 정지한다(SIGKILL로 끊기지 않음). `qa/load/sim/entrypoint.sh`가 SIGTERM을
받으면 4개 k6 하위 프로세스 전체에 SIGTERM을 전달하고 종료를 기다린다.

### 상태 조회 (FR-6 — 최소 보장: 진행률·도달 TPS·409율)

1. **⑤ Grafana(1차 수단)** — k6가 `--out experimental-prometheus-rw`로 각 요청마다
   `testid` 태그(b2c-read/b2c-write/b2b/marketing-spike)를 붙여 ⑤ Prometheus에
   remote-write한다(Prometheus는 `docker-compose.observability.yml`에서 이미
   `--web.enable-remote-write-receiver`로 활성화돼 있어 별도 조율 불요). 대시보드 원본은
   `qa/load/dashboards/simulator.json`(도달 TPS vs 목표 참조선·활성 시나리오(VU>0)·409 거부율·
   운영 안내 패널 4종).
   **Open Item**: 이 JSON을 Grafana가 자동 인식하려면 `observability/grafana/dashboards/`
   (⑤ 소유, provider.yml이 재귀 스캔)에 복사되어야 한다. TDD "⑦는 ⑤ 파일 무수정" 원칙에
   따라 `docker-compose.sim.yml`은 `grafana` 서비스를 확장하지 않으므로(그렇게 하면
   observability.yml 없이 sim.yml만 병합할 때 config가 깨진다), 이 복사는 ⑤ 쪽에서
   한 줄로 처리하거나(`cp qa/load/dashboards/simulator.json observability/grafana/dashboards/`)
   조율이 필요하다.
2. **k6 로그·gap report(보조 수단)** — `qa/load/results/<testid>.log`(k6 stdout)와
   `qa/load/results/<scenarioId>-gap.json`(달성률·병목 client/server 추정, FR-8)이
   컨테이너 볼륨을 통해 호스트에 그대로 남는다. Grafana 없이도 `make sim-logs` 또는
   `cat qa/load/results/*-gap.json`으로 진행률을 확인할 수 있다.
3. **reseed 이력** — `docker compose ... logs -f reseed`로 10분 주기
   `[reseed] === reseed 사이클 시작/종료 ===` 로그를 확인한다(Loki로의 자동 수집은
   아직 배선되지 않음 — docker 컨테이너 stdout을 스크레이프하는 Promtail 등 에이전트가
   이 리포에 없다. ⑤ 후속 필요, 대시보드 "운영 안내" 패널에도 명시).

### 환경 변수 (전부 기본값 있음, `docker-compose.sim.yml` 참고)

| 변수 | 기본값 | 의미 |
|---|---|---|
| `QA_SIM_TARGET_URL` | `http://nginx-lb` | k6-runner가 부하를 보낼 대상(LB 유일 진입점) |
| `QA_SIM_TIME_SCALE` | `1`(실시간 24h) | 압축 검증 시 예: `0.02` |
| `QA_SIM_ENABLE_MARKETING_SPIKE` | `true` | `false`면 배경 곡선 3종만 실행 |
| `QA_SIM_SPIKE_START_TIME` | `0s` | marketing-spike.js 시작 오프셋(예: `3h`) |
| `QA_SIM_K6_CPU_LIMIT` / `QA_SIM_K6_MEMORY_LIMIT` | `4.0` / `4G` | k6-runner 리소스 상한(NFR 측정 왜곡 방지) |
| `QA_SIM_RESEED_INTERVAL_SECONDS` | `600` | reseed 주기(초) |

### 20000 TPS 미달 시

마케팅 스파이크는 "도전 목표"다(TDD Open Questions). `qa/load/results/INFRA-07-gap.json`의
달성률·병목(`client(k6)` vs `server`)을 확인한다 — `limited-drop`의 JVM 세마포어(기본 200)가
실질 병목으로 예상되며, 이는 스크립트 실패가 아니라 관측 결과다.
