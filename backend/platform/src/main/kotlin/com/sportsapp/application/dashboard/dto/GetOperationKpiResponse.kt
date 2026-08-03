package com.sportsapp.application.dashboard.dto

import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.ticketing.dto.TicketKpiSummary
import java.math.BigDecimal

data class GetOperationKpiResponse(
    val ownerUserId: Long,
    val facility: FacilityKpiSection,
    val goods: GoodsKpiSection,
    val ticket: TicketKpiSection,
) {
    data class FacilityKpiSection(
        val utilizationRate: BigDecimal,
        val noShowRate: BigDecimal,
        val topFacilities: List<TopFacility>,
    )

    /**
     * 인기 시설 순위 항목 — 화면이 사람이 읽는 이름을 렌더할 수 있도록 id와 함께 이름을 싣는다.
     * 이름만 주면 상세로 이동할 수 없고, id만 주면 화면에 내부 식별자가 노출된다.
     */
    data class TopFacility(
        val id: String,
        val name: String,
    )

    data class GoodsKpiSection(
        val dailyRevenueTotal: BigDecimal,
        val inventoryTurnoverRate: BigDecimal,
        val outOfStockSkuCount: Long,
    )

    data class TicketKpiSection(
        val totalSoldCount: Long,
        val refundRate: BigDecimal,
        val complimentaryCount: Long,
    )

    companion object {
        fun of(
            ownerUserId: Long,
            facilityKpi: FacilityKpiSummary,
            goodsKpi: GoodsKpiSummary,
            ticketKpi: TicketKpiSummary,
            topFacilities: List<TopFacility>,
        ) =
            GetOperationKpiResponse(
                ownerUserId = ownerUserId,
                facility = FacilityKpiSection(
                    utilizationRate = facilityKpi.utilizationRate,
                    noShowRate = facilityKpi.noShowRate,
                    topFacilities = topFacilities,
                ),
                goods = GoodsKpiSection(
                    dailyRevenueTotal = goodsKpi.dailyRevenueTotal,
                    inventoryTurnoverRate = goodsKpi.inventoryTurnoverRate,
                    outOfStockSkuCount = goodsKpi.outOfStockSkuCount,
                ),
                ticket = TicketKpiSection(
                    totalSoldCount = ticketKpi.totalSoldCount,
                    refundRate = ticketKpi.refundRate,
                    complimentaryCount = ticketKpi.complimentaryCount,
                ),
            )
    }
}
