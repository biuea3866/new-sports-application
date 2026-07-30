package com.sportsapp.infrastructure.notification.gateway

import com.sportsapp.domain.notification.gateway.RecipientContactGateway
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Component

/**
 * [RecipientContactGateway] 구현체.
 *
 * user 도메인의 `UserRepository`(=`users` 테이블)를 직접 읽지 않고, 공급자의 공개 행위 계약인
 * [UserDomainService.findEmailBy] 를 경유한다. 결합이 스키마가 아니라 행위 계약에 걸리도록 한다.
 *
 * 수신처 조회 결과를 이벤트 payload 에 동봉하지 않고 발송 직전에 조회하는 이유는, 이메일을
 * payload 에 실으면 Kafka 보존 기간 내내 개인정보가 브로커에 잔존해 노출면이 넓어지기 때문이다.
 */
@Component
class RecipientContactGatewayImpl(
    private val userDomainService: UserDomainService,
) : RecipientContactGateway {

    override fun emailOf(userId: Long): String? =
        userDomainService.findEmailBy(userId)
}
