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

## 참고

- ADR-003 (prod 배포 QA 게이트), ADR-004 (롤백 이미지 태그 핀) — `.github/workflows/deploy-prod.yml` 주석 참조
- `scripts/deploy.sh`, `scripts/rollback.sh` — 배포/롤백 스크립트 계약
