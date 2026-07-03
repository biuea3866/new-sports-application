# 배포 운영 런북

배포 파이프라인(`docker-compose.yml` + override, `.github/workflows/deploy-dev.yml`/`deploy-prod.yml`,
`scripts/deploy.sh`/`rollback.sh`)이 코드로 강제하지 못하고 **운영자가 수동으로 준비해야 하는 항목**을
정리한다. 코드/워크플로 변경 없이도 배포 전 반드시 확인해야 하는 체크리스트다.

## 1. GitHub environment `production` 보호 설정 (필수)

`deploy-prod.yml`의 `deploy` job은 `environment: production`을 선언하지만, 이 키 자체는
승인을 강제하지 않는다 — GitHub 레포 설정에서 **별도로 Required reviewers를 등록해야** 승인 게이트가 완성된다.

- 경로: 레포 **Settings > Environments > production > Required reviewers**
- 이 설정이 없으면 ADR-003이 의도한 "자동(qa-gate) + 수동(environment 승인) 2중 관문" 중
  수동 축이 비어 있는 상태로 배포가 진행된다 — `qa-gate` job만 통과하면 승인 절차 없이 `deploy` job이 실행된다.
- 신규 self-hosted 러너/레포 생성 시 가장 먼저 누락되는 설정이므로, 배포 전 반드시 확인한다.

## 2. 이미지 태그 보존 정책 (prod 롤백 전제조건)

`scripts/rollback.sh`(ADR-004)는 새 이미지를 빌드하지 않고 **호스트에 이미 존재하는**
`sports-backend:<sha>` 이미지를 재배포하는 방식으로 동작한다. 따라서 이미지가 호스트에서
삭제되면 롤백이 불가능하다.

- **최소 직전 3개** 배포 SHA의 `sports-backend:<sha>` 이미지를 호스트에 보존한다.
- `docker image prune` / `docker system prune` 등 정리 작업 시 최근 배포 태그가 삭제되지
  않도록 태그를 확인하고 실행한다 (`docker images sports-backend` 로 사전 확인).
- 현재 파이프라인에는 자동 이미지 정리 스텝이 없다 — 무제한 누적 방지가 필요해지면 보존 정책을
  코드화(예: N개 초과분만 정리하는 스크립트)하는 것을 후속 개선 항목으로 검토한다.

## 3. 환경별 `.env` 파일 프로비저닝

`.env.dev`/`.env.prod`는 시크릿이므로 `.gitignore` 대상이며 git 저장소에 존재하지 않는다.
운영자가 배포 호스트에서 직접 생성해야 한다.

- **생성 방법**: `.env.example`을 템플릿으로 복사해 `.env.dev`/`.env.prod`를 만들고 실제 값으로 채운다.
- **`DOCKER_TAG`**: 운영자가 직접 관리할 필요 없음 — `scripts/deploy.sh`가 배포 시점의 대상 SHA로
  해당 키만 치환한다(다른 키는 보존). 최초 생성 시 임의값(`local` 등)으로 두어도 무방하다.
- **`MYSQL_ROOT_PASSWORD` / `DB_PASSWORD`**: base `docker-compose.yml`은 별도 app DB 계정을
  만들지 않으므로 backend는 root 계정으로 접속한다 — **두 값은 반드시 동일해야 한다**
  (`.env.example` 주석 참조). prod에서는 반드시 강한 값으로 직접 설정한다.
  - 미설정 시 `docker-compose.yml`의 기본값(`root`)으로 기동되는 것은 **dev 환경 편의를 위한
    fallback**이며, prod에서 이 fallback에 의존하는 것은 보안 리스크다.
  - 현재 파이프라인은 prod에서 기본값 사용을 막는 fail-fast 검증이 없다 — 후속 개선 항목으로 기록한다
    (예: `deploy.sh`에 prod 배포 시 `MYSQL_ROOT_PASSWORD`가 기본값이면 배포를 거부하는 가드 추가).

## 4. self-hosted 러너 등록 (필수)

`deploy-dev.yml`/`deploy-prod.yml`은 `runs-on: [self-hosted]`로 지정되어 있다 — dev/prod
compose 스택이 실제로 동작하는 호스트에 GitHub Actions self-hosted 러너가 등록되어 있어야
워크플로가 실행된다.

- dev/prod 스택과 `qa/verdict.json`(QA 게이트 검증 대상)이 **같은 호스트**에 있어야 한다 —
  `deploy-prod.yml`의 qa-gate job이 워크스페이스의 `qa/verdict.json`을 직접 읽기 때문이다.
- 러너 미등록 상태에서 워크플로를 `workflow_dispatch`로 실행하면 job이 대기(queued) 상태로
  멈춘다 — 배포 전 러너 온라인 상태를 GitHub 레포 **Settings > Actions > Runners**에서 확인한다.

## 5. Docker 운영 위생 (자동화됨 — `scripts/docker-cleanup.sh`)

`scripts/deploy.sh`를 **직접 실행**해 배포가 성공하면(health 폴링 통과 이후), 곧바로
`scripts/docker-cleanup.sh <env>`가 자동 호출되어 아래 4가지를 정리한다. `rollback.sh`는
`deploy.sh`의 `deploy()` 함수만 재사용하고 이 자동 호출 블록(BASH_SOURCE 가드)을 거치지
않으므로 **롤백 시에는 정리가 실행되지 않는다** — 롤백 대상이 될 수 있는 이전 이미지를
보존하기 위함이다.

### 태그 보존 정책

- `sports-backend:<sha>` 이미지 중 **최신 3개(`KEEP_TAGS=3`)** SHA 태그만 보존한다.
  방금 배포된 최신 태그도 이 안에 포함되어 항상 보존된다.
- `latest`/`local`/`<none>` 태그는 보존 정책 계산에서 제외한다(삭제 후보로도 잡지 않음).
- 3개를 초과하는 오래된 태그는 `docker rmi` 대상이 된다. 사용 중(실행 중 컨테이너 참조 등)이면
  `docker rmi`가 자연스럽게 실패하며, 스크립트는 이 실패를 무시하고 계속 진행한다(`|| true`).
- 위 "2. 이미지 태그 보존 정책" 섹션의 수동 확인 절차는 이 자동화로 대체된다 — 다만 운영자가
  직접 `docker images sports-backend`로 확인하는 습관은 여전히 유효하다.

### 각 prune의 대상 · 비대상

| 정리 대상 | 명령 | 대상 | 비대상 |
|---|---|---|---|
| dangling 이미지 | `docker image prune -f` | untagged 이미지 | 태그가 붙은 모든 이미지 |
| dangling 볼륨 | `docker volume prune -f` | 어떤 컨테이너도 참조하지 않는 anonymous 볼륨 | named 볼륨 전체(데이터 볼륨 포함) — `--all` 옵션은 절대 사용하지 않는다 |
| 중단 컨테이너 | `docker container prune -f --filter label=com.docker.compose.project=sports-<env>` | 해당 env의 compose 프로젝트 소속 중단 컨테이너 | 다른 프로젝트(예: 다른 레포의 compose 스택) 컨테이너 |

### 데이터 볼륨 보호 방식 (p0 — 절대 삭제 금지)

`mysql-data` / `mongodb-data` / `redis-data` / `kafka-data` / `minio-data` 패턴을 이름에
포함하는 볼륨(compose가 붙이는 프로젝트 접두사 포함, 예: `sports-dev_mysql-data`)은
**어떤 상황에서도 삭제 명령의 실행 조건에 들어가지 않도록 이중으로 방어한다**.

1. **명령 자체가 named 볼륨을 건드리지 않는다** — `docker volume prune -f`(인자 없음)만
   사용하고 `--all` 플래그는 코드에 존재하지 않는다. 기본 prune은 anonymous 미사용 볼륨만
   대상으로 하므로, 정상 상황에서는 애초에 named 데이터 볼륨이 후보에 오르지 않는다.
2. **prune 실행 전 방어 가드** — dangling 볼륨 목록을 먼저 조회해 보호 패턴과 매칭되는
   볼륨이 하나라도 있으면, prune을 실행하지 않고 즉시 에러로 중단(exit 1)한다. 이는
   "데이터 볼륨이 비정상적으로 dangling 상태가 되는" 상황을 조기에 차단하기 위한 것이다.

로컬 docker 환경에서 데이터스토어 named 볼륨 이름 샘플(`sports-dev_mysql-data`,
`sports-prod_mongodb-data`, `sports-dev_kafka-data`, `sports-dev_redis-data`, `minio-data`,
`sports-prod_minio-data`)과 익명 볼륨 해시명 샘플로 매칭 함수(`is_protected_volume`)를 단위
검증했으며, 데이터 볼륨 6종은 전부 보호로, 해시명 2종은 전부 삭제 후보로 정확히 분류됨을
확인했다. 또한 테스트용 볼륨(`test-hygiene-check_mysql-data`)을 실제로 dangling 상태로
만들어 real-run을 실행한 결과 prune이 중단(exit 1)되고 해당 볼륨이 삭제되지 않고 생존함을
실제 docker 환경에서 확인했다.

### 수동 정리 명령 예시

```bash
# 1. 먼저 dry-run으로 삭제 후보만 확인한다 (실제 삭제 없음)
./scripts/docker-cleanup.sh dev --dry-run

# 2. 후보를 확인한 뒤에만 실삭제를 실행한다
./scripts/docker-cleanup.sh dev
```

- `env` 인자는 `dev`/`prod` 외에는 거부된다(exit 1).
- 롤백 시에는 정리가 자동 실행되지 않는다 — 위 "각 prune의 대상·비대상" 이전에 이미
  이전 이미지가 보존되어 있어야 롤백이 성립하기 때문이다. 필요하면 배포자가 롤백 이후
  수동으로 `docker-cleanup.sh <env> --dry-run`을 먼저 확인하고 판단한다.

## 참고

- ADR-003 (prod 배포 QA 게이트), ADR-004 (롤백 이미지 태그 핀) — `.github/workflows/deploy-prod.yml` 주석 참조
- `scripts/deploy.sh`, `scripts/rollback.sh` — 배포/롤백 스크립트 계약
- `scripts/docker-cleanup.sh` — 배포 성공 후 자동 실행되는 Docker 운영 위생 정리 스크립트
