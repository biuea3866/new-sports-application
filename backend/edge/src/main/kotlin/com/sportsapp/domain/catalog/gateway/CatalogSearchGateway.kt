package com.sportsapp.domain.catalog.gateway

import com.sportsapp.domain.catalog.dto.CatalogItem
import com.sportsapp.domain.catalog.vo.SellerType
import org.springframework.data.domain.Pageable

/**
 * catalog 통합검색(BE-07)이 4개 판매 대상 도메인(goods/facility/recruitment/ticketing)의 읽기
 * 조회를 위임하는 계약 — edge가 소유하고, 구현은 단계마다 교체된다 (S2-01, W1-06b §9 Branch By
 * Abstraction과 동일 패턴).
 *
 * - 1단계: 조립자(`bootstrap`)의 로컬 어댑터가 각 코어 DomainService를 같은 프로세스에서 직접
 *   호출한다. 원격 홉 0, 동작·성능 이동 전과 동일.
 * - 2단계: edge 안의 RestClient 어댑터가 각 서비스의 원격 catalog 조회 엔드포인트를 호출한다.
 *
 * 반환 타입은 edge 소유 [CatalogItem]이다 — `ProductWithStock`·`EventWithMinSeatPrice` 등 타
 * 모듈 DTO를 계약에 노출하면 2단계에 그 타입이 그대로 HTTP 직렬화 계약이 되어버린다. 도메인
 * Entity → [CatalogItem] 매핑은 구현체(조립자 어댑터)의 책임이다.
 *
 * **edge가 commerce·facility-booking·social을 컴파일 의존하지 않는 것이 이 인터페이스의 존재
 * 이유다.** [CatalogCompositionService][com.sportsapp.application.catalog.CatalogCompositionService]가
 * 4개 코어 DomainService를 직접 주입하던 구조를 이 계약이 끊는다.
 */
interface CatalogSearchGateway {

    fun searchGoods(keyword: String?, sellerType: SellerType?, pageable: Pageable): List<CatalogItem>

    fun searchPrograms(keyword: String?, pageable: Pageable): List<CatalogItem>

    fun searchRecruitments(keyword: String?, pageable: Pageable): List<CatalogItem>

    fun searchTicketingEvents(keyword: String?, pageable: Pageable): List<CatalogItem>
}
