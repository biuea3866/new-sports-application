package com.sportsapp.domain.notification.entity
import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "push_tokens")
class PushToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "token", nullable = false, length = 512)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    var platform: PushPlatform,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    // 같은 토큰이 다른 사용자/플랫폼으로 재등록되면 소유자를 갱신한다.
    fun reassign(userId: Long, platform: PushPlatform) {
        this.userId = userId
        this.platform = platform
    }

    companion object {
        fun create(userId: Long, token: String, platform: PushPlatform): PushToken {
            require(token.isNotBlank()) { "push token must not be blank" }
            return PushToken(userId = userId, token = token, platform = platform)
        }
    }
}

enum class PushPlatform {
    IOS,
    ANDROID,
    WEB,
}
