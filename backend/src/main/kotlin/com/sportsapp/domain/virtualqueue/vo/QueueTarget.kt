package com.sportsapp.domain.virtualqueue.vo

/**
 * 가상 대기열 대상 VO — type + targetId를 캡슐화하고 Redis 키 조립을 전담한다.
 *
 * 다른 도메인(goods·ticketing)은 이 VO의 존재만 알고 키 문자열 구조는 몰라야 한다
 * (no-getter-chain-behavior — 키 조립은 항상 이 VO에 위임한다). 호출부가 `"queue:" + type + ...`
 * 형태로 직접 문자열을 이어 붙이지 않는다.
 *
 * 키·Lua 계약 SSOT: `backend/docs/redis/virtual-queue-keys.md`.
 */
data class QueueTarget(
    val type: QueueTargetType,
    val targetId: Long,
) {

    /** `queue:{slug}:{targetId}` — 대상별 키 접두. */
    fun keyPrefix(): String = "$KEY_NAMESPACE:${type.slug}:$targetId"

    /** `queue:{slug}:{targetId}:waiting` — 순번 Sorted Set(score=고정 seq). */
    fun waitingKey(): String = "${keyPrefix()}:waiting"

    /** `queue:{slug}:{targetId}:heartbeat` — 생존 신호 Sorted Set(score=마지막 폴링 ms). */
    fun heartbeatKey(): String = "${keyPrefix()}:heartbeat"

    /** `queue:{slug}:{targetId}:seq` — 고정 시퀀스 채번 + seenTotal 상한 겸용 카운터. */
    fun seqKey(): String = "${keyPrefix()}:seq"

    /** `queue:{slug}:{targetId}:admitted_count` — 클러스터 admission 고수위. */
    fun admittedCountKey(): String = "${keyPrefix()}:admitted_count"

    /** `queue:{slug}:{targetId}:token:{userId}` — 입장 토큰 멱등·재사용 마커. */
    fun tokenKey(userId: Long): String = "${keyPrefix()}:token:$userId"

    /** `queue:admission:{slug}:{targetId}` — 배치 admission 분산 락 키(대상별 독립). */
    fun admissionLockKey(): String = "$KEY_NAMESPACE:admission:${type.slug}:$targetId"

    /** `queue:active` Set의 member 표현(`{slug}:{targetId}`) — pump 순회 인덱스용. */
    fun activeMember(): String = "${type.slug}:$targetId"

    companion object {
        private const val KEY_NAMESPACE = "queue"

        /** pump가 활성 대상을 순회하는 인덱스 키(`SMEMBERS`, `KEYS`/`SCAN` 회피). */
        const val ACTIVE_TARGETS_KEY = "queue:active"
    }
}
