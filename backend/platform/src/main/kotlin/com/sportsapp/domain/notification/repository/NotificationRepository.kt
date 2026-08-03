package com.sportsapp.domain.notification.repository
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.entity.NotificationStatus
interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findById(id: Long): Notification?
    fun findByEventId(eventId: String): Notification?
    fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification>
    fun saveAll(notifications: List<Notification>): List<Notification>
    /**
     * 미읽음 집계. 목록과 **같은 채널 기준**으로 세야 알림함 배지와 목록 건수가 어긋나지 않는다
     * (채널을 안 거르면 IN_APP+PUSH 합산이라 목록 8건에 배지 16이 뜬다).
     */
    fun countUnreadByUserId(userId: Long, channel: NotificationChannel?): Long
}
