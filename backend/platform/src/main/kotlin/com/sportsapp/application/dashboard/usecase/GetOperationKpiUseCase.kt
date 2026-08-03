package com.sportsapp.application.dashboard.usecase
import com.sportsapp.application.dashboard.dto.GetOperationKpiResponse
import com.sportsapp.application.dashboard.dto.GetOperationKpiCommand

import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.facility.service.FacilityDomainService
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 이름을 찾지 못한 시설의 표시 문구 — 내부 식별자를 화면에 노출하지 않기 위한 대체값. */
private const val UNKNOWN_FACILITY_NAME = "알 수 없는 시설"

@Service
class GetOperationKpiUseCase(
    private val bookingDomainService: BookingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val ticketingDomainService: TicketingDomainService,
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetOperationKpiCommand): GetOperationKpiResponse {
        val facilityKpi = bookingDomainService.aggregateFacilityKpi(command.ownerUserId, command.from, command.to)
        val goodsKpi = goodsDomainService.aggregateGoodsKpi(command.ownerUserId, command.from, command.to)
        val ticketKpi = ticketingDomainService.aggregateTicketKpi(command.ownerUserId, command.from, command.to)
        val topFacilities = resolveTopFacilities(facilityKpi.topFacilityIds)
        return GetOperationKpiResponse.of(command.ownerUserId, facilityKpi, goodsKpi, ticketKpi, topFacilities)
    }

    /**
     * booking 집계가 준 시설 id를 facility 컨텍스트의 이름과 조합한다.
     *
     * 두 컨텍스트를 아는 유일한 레이어가 application이므로 여기서 조합한다 — booking이 facility를
     * 직접 참조하거나 facility 소유 컬렉션을 로우 쿼리로 읽지 않는다. 대상은 TOP5(최대 5건)라
     * 건별 조회로 충분하다. 삭제된 시설이라도 순위에서 빼지 않는다 — 개수가 달라지면 "인기 시설 수"
     * 지표와 어긋나므로, 이름만 대체 문구로 채운다.
     */
    private fun resolveTopFacilities(facilityIds: List<String>): List<GetOperationKpiResponse.TopFacility> =
        facilityIds.map { facilityId ->
            GetOperationKpiResponse.TopFacility(
                id = facilityId,
                name = facilityDomainService.findBy(facilityId)?.name ?: UNKNOWN_FACILITY_NAME,
            )
        }
}
