package com.sportsapp.domain.notification.repository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel

interface NotificationCustomRepository {
    /**
     * 사용자 알림 목록. 같은 사건이 IN_APP·PUSH 두 행으로 적재되므로([NotificationEventWorker]
     * enqueueBoth) 채널을 지정해 조회한다 — 지정하지 않으면 알림함에 같은 알림이 두 번 보인다.
     */
    fun findByUserIdPaged(
        userId: Long,
        channel: NotificationChannel,
        onlyUnread: Boolean,
        pageable: Pageable,
    ): Page<Notification>
}
