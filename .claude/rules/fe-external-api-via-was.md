# 외부 API는 backend WAS 경유 (프론트 직접 호출 금지)

## 원칙

**모든 외부 3rd-party Open API 호출은 우리 backend WAS(Spring)를 경유합니다. 프론트(mobile/web)는 외부 API를 직접 호출하지 않습니다.**

- 외부 API 호출 로직·인증키는 backend 의 `~Gateway` 구현체(infrastructure)에만 존재합니다.
- 프론트는 우리 backend WAS 의 엔드포인트만 호출합니다.
- 웹의 Next.js BFF(route.ts) 도 "프론트"입니다 — 외부 API를 직접 부르지 말고 backend WAS 를 호출합니다.

## 이유

| 관점 | 이유 |
|---|---|
| 보안 | API 키가 클라이언트 번들/네트워크에 노출되면 탈취됨. 키는 서버에만 둔다. |
| 일관성 | 응답 스키마 변환·캐싱·레이트리밋·장애 처리(타임아웃/서킷)를 backend 한 곳에서 관리. |
| 전환 | mock ↔ 실 API 전환을 backend env 로만 처리. 프론트 재배포 불필요. |
| 감사 | 외부 호출 로그·메트릭을 backend 에서 일괄 수집. |

## 적용 예시

| 기능 | ❌ 프론트 직접 | ✅ backend 경유 |
|---|---|---|
| 주소→좌표 | 프론트가 `dapi.kakao.com` 호출 | 프론트 → 우리 `GET /facilities/near` → backend `GeocodingGateway` → Kakao |
| 날씨 | 프론트가 `apis.data.go.kr` 호출 | 프론트 → 우리 `GET /weather` → backend `WeatherGateway` → 기상청 |
| 푸시 발송 | 프론트가 `exp.host` 발송 호출 | 기기는 expo SDK 로 **토큰만** 획득 → 우리 `POST /notifications/push-tokens` 등록 → backend `PushChannelGateway` 가 Expo 로 발송 |
| SMS | 프론트가 `api.solapi.com` 호출 | backend `SmsChannelGateway` 만 호출 |

> 예외: 기기 자체 능력(예: expo-notifications 로 자기 푸시 토큰 획득, 지도 렌더링 SDK)은 "외부 데이터 API 직접 호출"이 아니므로 허용. 단 **데이터 조회/발송은 반드시 backend 경유**.

## 강제

`harness-rules.json` 의 `fe-no-direct-external-api` 룰이 프론트(.ts/.tsx)에서 외부 API 호스트
(`dapi.kakao.com`, `apis.data.go.kr`, `data.go.kr`, `exp.host`, `api.solapi.com`, `apihub.kma.go.kr` 등)
직접 호출을 차단합니다.
