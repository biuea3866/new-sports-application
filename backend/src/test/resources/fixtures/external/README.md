# 외부 API 계약 fixture 규약

`ExternalContractSupport.loadFixture(path)` 가 로드하는 녹화 응답 JSON을 보관합니다 (ADR-002).

## 디렉토리 규약

```
fixtures/external/{issuer}/{service}.json
```

- `issuer`: API 발급 주체 (예: `kakao-local`, `kma`, `data-go-kr`)
- `service`: Gateway가 호출하는 서비스 단위 (예: `address.json`, `short-forecast.json`)

`loadFixture("external/{issuer}/{service}.json")` 형태로 호출합니다 — 인자에 `external/` 접두사를 포함합니다.

## 예시

| Gateway | fixture 경로 (예정, BE-02/03/04) |
|---|---|
| `KakaoGeocodingGatewayImpl` | `external/kakao-local/address.json` |
| `DataGoKrPublicFacilityGatewayImpl` | `external/data-go-kr/public-facility.json` |
| `KmaWeatherGatewayImpl` | `external/kma/short-forecast.json` |

## fixture 채우는 방법

1. 키 발급 후 실 API를 1회 호출해 응답 JSON을 그대로 캡처해 저장한다 (권장).
2. 키 발급 전이면 도메인 DTO와 스키마가 동일한 mock 응답으로 seed하고, `verifyExternalLive` 최초 실행 시 실 응답과 대조해 승격한다 (TDD Open Questions 참고).

`sample/health-check.json`은 `ExternalContractSupport` 자체 하네스 테스트(BE-01)가 사용하는 최소 샘플이며, 실 벤더 fixture가 아닙니다.
