package com.sportsapp

import com.sportsapp.domain.booking.dto.FacilitySchedule
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.booking.gateway.FacilityScheduleGateway
import com.sportsapp.domain.facility.service.FacilityDomainService
import com.sportsapp.domain.facility.service.ProgramDomainService
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page

/**
 * test-jpa 프로파일 전용 게이트웨이 스텁.
 *
 * test-jpa 프로파일은 MongoDB 기반 facility 레이어를 비활성화하므로 실제
 * FacilityOwnershipGatewayImpl / FacilityScheduleGatewayImpl(@Profile("!test-jpa")) 이 존재하지 않는다.
 * 하지만 booking 의 SlotDomainService·SlotGenerationDomainService(프로파일 무관 빈)가 이 게이트웨이들에
 * 의존하므로, 순수 JPA 통합 테스트 컨텍스트가 부팅되도록 no-op 스텁을 제공한다.
 * (이 테스트들은 슬롯 생성 경로를 실행하지 않는다.)
 */
@TestConfiguration
class TestJpaGatewayStubConfig {

    @Bean
    @Profile("test-jpa")
    fun facilityOwnershipGateway(): FacilityOwnershipGateway = object : FacilityOwnershipGateway {
        override fun requireOwner(facilityId: String, userId: Long) = Unit
    }

    @Bean
    @Profile("test-jpa")
    fun facilityScheduleGateway(): FacilityScheduleGateway = object : FacilityScheduleGateway {
        override fun findSchedulableFacilities(): List<FacilitySchedule> = emptyList()
    }

    // BE-07 catalog 파사드(CatalogCompositionService)가 프로파일 무관 빈으로
    // ProgramDomainService(@Profile("!test-jpa"))를 주입받으므로, test-jpa 풀부팅을 위해
    // no-op 스텁을 제공한다 (facility catalog는 JPA-only 테스트에서 빈 결과).
    @Bean
    @Profile("test-jpa")
    fun programDomainService(): ProgramDomainService = mockk(relaxed = true) {
        every { searchForCatalog(any(), any()) } returns Page.empty()
    }

    /**
     * `GetOperationKpiUseCase`(dashboard, 프로파일 무관 빈)가 인기 시설 이름을 채우려고
     * `FacilityDomainService`(`@Profile("!test-jpa")`)를 주입받는다 (PR #386) — 스텁이 없으면
     * **`BaseJpaIntegrationTest` 를 쓰는 모든 테스트의 컨텍스트 로드가 실패한다**
     * (`UnsatisfiedDependencyException`). 위 두 게이트웨이·`ProgramDomainService` 와 같은 사유의 스텁이다.
     *
     * `findBy` 가 null 을 반환해도 UseCase 가 대체 문구("알 수 없는 시설")로 방어하므로 relaxed 스텁으로 충분하다.
     */
    @Bean
    @Profile("test-jpa")
    fun facilityDomainService(): FacilityDomainService = mockk(relaxed = true) {
        every { findBy(any<String>()) } returns null
    }
}
