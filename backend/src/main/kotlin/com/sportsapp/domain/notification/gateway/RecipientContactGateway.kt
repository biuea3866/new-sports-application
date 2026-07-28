package com.sportsapp.domain.notification.gateway

/**
 * 알림 수신처(이메일)를 조회하는 ACL 게이트웨이.
 *
 * notification 도메인은 user 도메인을 직접 import 하지 않으며, 이 interface 를 통해서만
 * 수신처를 확인한다. 구현체는 infrastructure 레이어에 위치하며 user 의 공개 행위 계약
 * (UserDomainService)을 경유한다 — user 의 저장소(테이블)를 직접 알지 못한다.
 *
 * 수신처를 찾지 못하면 예외가 아니라 null 을 반환한다. 알림 발송 경로에서 예외가 전파되면
 * 채널 게이트웨이의 발송 실패 처리 흐름이 아니라 상위 트랜잭션이 깨지기 때문이다.
 */
interface RecipientContactGateway {
    fun emailOf(userId: Long): String?
}
