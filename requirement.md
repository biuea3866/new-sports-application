# 문서 요구사항
- /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱 하위에 모든 문서를 기재한다.
- 과제별로 카테고리를 나누고, ADR, Tickets, PRD, TDD 문서를 작성한다. (같은 계층의 주식앱 참고)

# 프로젝트 요구사항
- 전체 리팩토링
- 명확한 도메인 분리 및 도메인 재설정

# 테스트 요구사항
## 외부 연동 서버
- toss-payment, naver-pay, kakao-pay를 mock 서버로 구성
- 가급적이면, 무료로 외부 api를 연동하되 안된다면 mock 서버를 만들 것
-- 체육관, 공원, 날씨, 맵 등 외에 과제를 진행하며 필요한 외부 데이터들

## 실제 유저들의 트래픽
- b2c 사이드 3000TPS, b2b 사이드 100TPS 수준의 트래픽을 실제로 발생시킬 것, 여러 유즈케이스 및 시나리오를 e2e로 발생

## 마케팅 이벤트 
- 티케팅, 한정판 상품 등 마케팅 이벤트 시 20000TPS 수준의 트래픽을 실제로 발생

## B2B 사이드 외부 협력사
- 상품 등록, 관람 티켓 등에 대한 유즈케이스를 지속적으로 계속 발생시킴 하루에 1,000건 이상

# 배포 요구사항
- dev, prod 환경을 분리한다.
- dev: main 브랜치에 머지되면 배포된다.
- prod: private-qa 에이전트(신규 추가 예정)의 QA를 통과해야 배포할 수 있다.

## Docker 운영 위생
- 이미지는 최신 이미지를 사용하고, 구버전 이미지는 사용하지 않는다. (롤백용 직전 N개 태그는 보존)
- 불필요한 이미지는 제거한다. (dangling·오래된 태그 prune)
- 불필요한 볼륨은 제거한다. (미사용 볼륨 prune)
- 불필요한 컨테이너는 내린다. (중단/고아 컨테이너 정리)

# 모니터링 요구사항
- 그라파나, 프로메테우스, 로키, 템포 스택 전부 활용
- p95 이상의 API 레이턴시가 급증할 경우 https://discord.com/api/webhooks/1522253523205750996/1y0aQ7HiE5k7xK4bk5y7Aaq7MY3neLHci4gVK8CUW6qLdXgzjcElPa6Ny7iUWXhM1_Iw로 알림 발생
-- 어떤 에러읹, 원인, 해결방법 등을 함께 조사하여 알림 발생
- mysql
-- cpu, memory, slow raw query, connection
- redis 
-- cpu, memory 
- kafka
-- cpu, memory, lag, partition
- spring app 
-- cpu, memory, gc, heap, tomcat thread pool / active count, async thread pool / active count
- kafka의 경우 kafka ui 추가
- otel로 모든 요청에 대한 분산 추적이 가능하도록 trace를 잇도록 함