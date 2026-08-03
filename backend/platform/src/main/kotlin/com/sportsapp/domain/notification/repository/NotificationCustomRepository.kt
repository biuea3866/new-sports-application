package com.sportsapp.domain.notification.repository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel

interface NotificationCustomRepository {
    /**
     * 사용자 알림 목록.
     *
     * 같은 사건이 IN_APP·PUSH 두 행으로 적재되므로(NotificationEventWorker#enqueueBoth) 소비처가
     * 채널을 지정한다 — 알림함은 IN_APP(중복 노출 방지), MCP 발송 진단 도구는 null(전 채널).
     * [channel] 이 null 이면 채널 조건 없이 조회한다.
     */
    fun findByUserIdPaged(
        userId: Long,
        channel: NotificationChannel?,
        onlyUnread: Boolean,
        pageable: Pageable,
    ): Page<Notification>
}
