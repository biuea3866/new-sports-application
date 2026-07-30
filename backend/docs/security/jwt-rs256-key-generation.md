# JWT RS256 키 생성 절차 (W1-05)

`JwtTokenProvider`가 참조하는 `APP_JWT_PUBLIC_KEY` / `APP_JWT_PRIVATE_KEY` 환경변수 생성·주입 방법이다.
`.env.example`은 W1-02 소유라 이 문서에 절차를 남긴다 — compose env 배선은 W1-02를 따른다.

## 1. 키페어 생성 (RSA 2048, PKCS8)

```bash
# 1) RSA 사설키 생성 (PKCS1 raw)
openssl genrsa -out jwt_private_raw.pem 2048

# 2) PKCS8로 변환 (Java KeyFactory가 PKCS8 형식을 요구한다)
openssl pkcs8 -topk8 -nocrypt -inform PEM -in jwt_private_raw.pem -out jwt_private.pem

# 3) 공개키 추출 (X.509 SubjectPublicKeyInfo)
openssl rsa -in jwt_private_raw.pem -pubout -out jwt_public.pem

# 4) raw 키 파일 삭제 (PKCS8 변환본만 보관)
rm jwt_private_raw.pem
```

- `jwt_private.pem`은 **platform 서비스에만** 주입한다. 나머지 5개 서비스는 `APP_JWT_PRIVATE_KEY`를 비워둔다
  (발급 비활성·검증만 수행하는 정상 상태).
- `jwt_public.pem`은 **6개 서비스 전부**에 주입한다.

## 2. 환경변수 주입 형식 (개행 처리)

PEM은 여러 줄이다. `.env` 파일이나 compose `environment:`에 넣을 때는 **실제 개행을 유지**하거나
**리터럴 `\n` 이스케이프**로 한 줄로 압축한다 — `JwtTokenProvider`가 두 형식을 모두 처리한다
(`decodePemBody`가 리터럴 `"\n"` 문자열과 실제 개행 문자를 모두 허용).

### 방법 A — `.env` 파일에서 실제 개행 유지 (권장, docker compose `env_file`)

```env
APP_JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...(생략)...
-----END PUBLIC KEY-----"
```

### 방법 B — 한 줄 리터럴 `\n` (compose `environment:` 인라인 값에 적합)

```bash
APP_JWT_PUBLIC_KEY=$(awk 'BEGIN{ORS="\\n"} {print}' jwt_public.pem)
```

## 3. 애플리케이션 설정 매핑

`bootstrap/src/main/resources/application.yml`:

```yaml
app:
  jwt:
    algorithm: ${APP_JWT_ALGORITHM:HS256}   # 전환 단계별 값 — 표 참고. 값 변경은 컨테이너 재기동 필요
    public-key: ${APP_JWT_PUBLIC_KEY:}      # 기본값 빈 문자열 — 미주입 시 RS256 검증 비활성(HS256만 검증, 부팅 정상)
    private-key: ${APP_JWT_PRIVATE_KEY:}    # 기본값 빈 문자열 — platform 외 서비스는 비움
```

- `APP_JWT_ALGORITHM=RS256`으로 설정했는데 `APP_JWT_PRIVATE_KEY`가 없으면 부팅이 즉시 실패한다
  (조용히 HS256으로 계속 발급하는 사고 방지).

## 4. 전환 단계별 `APP_JWT_ALGORITHM` 값

| 단계 | 값 | 비고 |
|---|---|---|
| 1 | `HS256` | 기본값. 공개키 미주입 상태에서도 부팅 정상(RS256 검증 비활성, HS256만 검증) |
| 2 | `RS256` | 1단계 무사고 확인 후 platform에서만 전환 |
| 3 | `RS256` (검증도 RS256 단독) | 기존 HS256 토큰 최대 유효기간(액세스 토큰 30분) 경과 후, `JwtTokenProvider.parseClaims`의 HS256 fallback 코드를 제거 |

**전환에는 컨테이너 재기동이 필요하다.** 이 배포 형태(docker compose)는 config server·
`@RefreshScope`를 도입하지 않아 `APP_JWT_ALGORITHM` 같은 OS 환경변수는 프로세스 시작 시점에
고정된다 — 값을 바꾸려면 해당 서비스 컨테이너를 재기동해야 하고, 롤백도 동일하게 이전 값으로
재기동한다("재기동으로 즉시 롤백"이지 "재기동 없는 전환"이 아니다).

## 5. 로컬 개발 기본 키 금지

로컬 개발용 기본 RSA 키를 저장소에 커밋하지 않는다. 로컬에서 필요하면 위 1번 절차로 개인 키를 생성해
셸 환경변수로만 주입한다 (`.env`는 `.gitignore` 대상).
