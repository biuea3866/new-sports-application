# qa/ — QA 회귀 스위트

`/qa` 슬래시 커맨드가 실행하는 **영구 회귀 스위트**의 위치입니다. 1회성 시나리오·아티팩트는 `.analysis/outputs/qa/`에 따로 저장됩니다.

## 디렉토리 구조

```
qa/
├── e2e/                         # Playwright E2E 회귀
│   ├── docker-compose.qa.yml    # 인프라만 (MySQL/MongoDB/Redis/Kafka)
│   ├── playwright.config.ts     # 회귀 전용 설정 (web/playwright.config.ts와 분리)
│   ├── wait-for-healthy.sh      # 컨테이너 healthy 대기
│   ├── package.json             # Playwright 의존성
│   ├── scenarios/               # 시나리오 md (영구 회귀)
│   ├── specs/                   # Playwright spec.ts (영구 회귀)
│   ├── page-objects/            # Page Object Model
│   ├── fixtures/                # 시드 fixture
│   └── .gitignore
└── load/                        # k6 부하 회귀
    ├── README.md
    ├── scenarios/               # 시나리오 md (영구 회귀)
    ├── k6/
    │   ├── lib/                 # 공통 헬퍼 (auth, metrics)
    │   └── *.js                 # k6 스크립트 (영구 회귀)
    └── seeds/                   # 부하 사전 시드 SQL
```

## 사용

### `/qa` 슬래시 커맨드로 실행 (권장)
```
/qa #123                # PR 번호
/qa <TICKET-ID>         # 티켓 번호
/qa --full-regression   # 전체 회귀
```

자세한 흐름은 [`.claude/commands/qa.md`](../.claude/commands/qa.md) 참조.

### 수동 실행 (디버깅)

**1) 인프라 기동**
```bash
cd qa/e2e
docker-compose -f docker-compose.qa.yml up -d
./wait-for-healthy.sh
```

**2) BE/FE 호스트에서 실행** (별도 터미널)
```bash
# BE
cd backend && ./gradlew bootRun

# FE
cd web && npm run dev
```

> Dockerfile은 의도적으로 두지 않습니다. docker-compose에서 BE/FE를 띄우려면 빌드 시간이 길어져 회귀 속도를 해칩니다. 호스트 실행으로 빠른 hot-reload를 유지합니다.

**3) E2E 실행**
```bash
cd qa/e2e
npx playwright install   # 최초 1회
npx playwright test
```

**4) 부하 실행**
```bash
cd qa/load
k6 run k6/example.bookings-get.js
```

**5) 정리**
```bash
cd qa/e2e
docker-compose -f docker-compose.qa.yml down -v
```

## 시나리오 승격 정책

`.analysis/outputs/qa/.../scenarios/`의 1회성 시나리오 중 다음 조건을 만족하면 사람 검토 후 `qa/e2e/scenarios/` 또는 `qa/load/scenarios/`로 승격합니다.

- 실제로 결함이 발견된 시나리오 (회귀 방지 목적)
- Critical/Major severity면서 사용자가 빈번하게 거치는 플로우
- 사람이 검토하고 명시적으로 승격 결정

## 참고 문서

- [.claude/commands/qa.md](../.claude/commands/qa.md) — 파이프라인 정의
- [.claude/rules/qa-scenario-guide.md](../.claude/rules/qa-scenario-guide.md) — E2E 시나리오 형식
- [.claude/rules/qa-load-guide.md](../.claude/rules/qa-load-guide.md) — k6 시나리오 형식
- [.claude/rules/defect-ticket-guide.md](../.claude/rules/defect-ticket-guide.md) — 결함 md 형식
