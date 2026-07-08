# qa/load/reseed/Dockerfile — reseed 배치 런타임 이미지 (⑦ 상시 트래픽 시뮬레이터, INFRA-09)
#
# 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-09-compose-sim-제어-대시보드-통합.md
# 근거 TDD: ../TDD.md "Testing Plan"(정적 레벨 — hadolint 검증 대상 이미지).
#
# 이 이미지는 reseed.sh(INFRA-08)를 실행할 수 있는 런타임(bash·mysql client·redis-cli)만 제공한다.
# reseed.sh/reseed.sql 자체는 이미지에 굽지 않고 docker-compose.sim.yml이 ./qa/load 를
# 볼륨으로 마운트해 공급한다(observability.yml의 config 마운트 관례와 동일 — 코드 변경 시 재빌드 불요).
FROM debian:bookworm-slim

# Debian slim 이미지는 패키지 버전을 고정하면 mirror 갱신마다 archive에서 밀려나 빌드가 깨지기
# 쉽다 — 베이스 이미지 태그(debian:bookworm-slim) 고정으로 재현성을 확보하고 패키지 버전은
# 고정하지 않는다(이 이미지는 부하 도구용 런타임일 뿐 배포 대상 앱 이미지가 아니다).
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        default-mysql-client \
        redis-tools \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# 기본 커맨드는 docker-compose.sim.yml의 command가 지정(qa/load/reseed/reseed.sh 마운트 경로).
ENTRYPOINT ["bash"]
