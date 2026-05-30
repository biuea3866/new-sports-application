# qa/load/ — k6 부하 회귀

## 디렉토리

```
qa/load/
├── scenarios/          # 부하 시나리오 md (qa-load-guide 형식)
├── k6/
│   ├── lib/            # 공통 헬퍼
│   │   ├── auth.js     # 인증 토큰 발급
│   │   └── metrics.js  # 공통 임계·태깅
│   └── *.js            # 시나리오별 k6 스크립트
└── seeds/              # 부하 사전 시드 SQL
```

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
