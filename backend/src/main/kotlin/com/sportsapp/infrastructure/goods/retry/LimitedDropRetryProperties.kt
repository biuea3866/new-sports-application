package com.sportsapp.infrastructure.goods.retry

/**
 * 한정판 구매 재시도 예산(FIX-02 §③) 값 홀더 — `app.limited-drop.retry.max-attempts`.
 *
 * 기본값을 코드에 고정하고 env(`APP_LIMITED_DROP_RETRY_MAX_ATTEMPTS`)로만 재정의한다 —
 * `application.yml`에 키를 추가하지 않는다(같은 wave의 FIX-03이 yml 소유권을 갖는다, 파일 충돌 방지).
 *
 * 서버측 요청 수명이 커넥션 풀 대기(5s, FIX-03)·LB `proxy_read_timeout`(30s)보다 확실히 짧아야
 * 하므로 기존 200회(최악 총 대기 최대 20초 수준)를 20회(약 1.5초 이내, backoff delay=5ms~100ms
 * 기준)로 낮춘다.
 */
class LimitedDropRetryProperties {
    var maxAttempts: Int = 20
}
