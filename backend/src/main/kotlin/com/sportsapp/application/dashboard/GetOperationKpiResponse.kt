package com.sportsapp.application.dashboard

import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.ticketing.TicketKpiSummary
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
        val topFacilityIds: List<String>,
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
        fun of(ownerUserId: Long, facilityKpi: FacilityKpiSummary, goodsKpi: GoodsKpiSummary, ticketKpi: TicketKpiSummary) =
            GetOperationKpiResponse(
                ownerUserId = ownerUserId,
                facility = FacilityKpiSection(
                    utilizationRate = facilityKpi.utilizationRate,
                    noShowRate = facilityKpi.noShowRate,
                    topFacilityIds = facilityKpi.topFacilityIds,
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
