# mock-servers

외부 Open API 중 **유료이거나 키 발급이 필요한** 서비스의 로컬 개발용 Mock 입니다. 실 API와 **동일한 요청/응답 스키마**를 모사합니다.

| 디렉토리 | 모사 대상 | 실 API 전환 (env) |
|---|---|---|
| `kakao-local/` | Kakao Local REST (주소→좌표) | `EXTERNAL_GEOCODING_BASE_URL=https://dapi.kakao.com` + `KAKAO_REST_API_KEY` |
| `data-go-kr/` | 공공체육시설 + 기상청 단기예보 | `EXTERNAL_WEATHER_BASE_URL=https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0` + `DATA_GO_KR_SERVICE_KEY` |
| `solapi/` | SOLAPI SMS 발송 | `EXTERNAL_SMS_BASE_URL=https://api.solapi.com` + 발신번호(사업자) |

전환 판정 기준: 무료 API 일일 요청 한도 **≥ 1,000건**(ADR-001). kakao-local·data-go-kr 은 임계를 크게 상회해 전환 대상, solapi 는 한도 개념이 아닌 건당 과금 + 발신번호 사전등록이 필요해 mock 유지 — 상세 조사표는 `프로젝트/스포츠앱/외부 연동 정비/TDD.md` "발급 주체 무료 API 전환 조사표".

MailHog(이메일)은 공식 이미지를 docker-compose 에서 직접 사용합니다 (`8025` UI 로 수신 확인).

## 실행

루트의 `docker-compose up` 으로 함께 기동됩니다. 각 mock 은 `node:20-alpine` 이미지에서 런타임에 `npm install` 후 실행됩니다 (별도 이미지 빌드 불필요).

호스트 포트: kakao-local `9101`, data-go-kr `9102`, solapi `9103`, MailHog SMTP `1025` / UI `8025`.

## 전환 메커니즘

백엔드 Gateway 는 `base-url` + `api-key` 를 env 로 받습니다. 키가 없으면 mock host(localhost:910x)를, 키가 있으면 실 API host 를 바라봅니다. **코드 변경 없이 env 만 교체**하면 mock → 실연동 전환됩니다.
