package com.sportsapp

import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

/**
 * test-jpa 프로파일 전용 게이트웨이 스텁.
 *
 * test-jpa 프로파일은 MongoDB 기반 facility 레이어를 비활성화하므로 실제
 * FacilityOwnershipGatewayImpl(@Profile("!test-jpa")) 이 존재하지 않는다.
 * 하지만 booking 의 SlotDomainService(프로파일 무관 빈)가 이 게이트웨이에 의존하므로,
 * 순수 JPA 통합 테스트 컨텍스트가 부팅되도록 no-op 스텁을 제공한다.
 * (이 테스트들은 슬롯 생성 경로를 실행하지 않는다.)
 */
@TestConfiguration
class TestJpaGatewayStubConfig {

    @Bean
    @Profile("test-jpa")
    fun facilityOwnershipGateway(): FacilityOwnershipGateway = object : FacilityOwnershipGateway {
        override fun requireOwner(facilityId: String, userId: Long) = Unit
    }
}
