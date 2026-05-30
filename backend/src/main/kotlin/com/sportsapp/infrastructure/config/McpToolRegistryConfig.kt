package com.sportsapp.infrastructure.config

import com.sportsapp.presentation.mcp.toolregistry.McpBookingTools
import com.sportsapp.presentation.mcp.toolregistry.McpBookingWriteTools
import com.sportsapp.presentation.mcp.toolregistry.McpComplimentaryTicketTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityStatsTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityTools
import com.sportsapp.presentation.mcp.toolregistry.McpGoodsSalesTools
import com.sportsapp.presentation.mcp.toolregistry.McpInventoryTools
import com.sportsapp.presentation.mcp.toolregistry.McpNotificationTools
import com.sportsapp.presentation.mcp.toolregistry.McpOperatorProfileTools
import com.sportsapp.presentation.mcp.toolregistry.McpRefundTools
import com.sportsapp.presentation.mcp.toolregistry.McpSlotWriteTools
import com.sportsapp.presentation.mcp.toolregistry.McpTicketSalesTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * MCP tool registry 설정.
 *
 * Spring AI MCP Server 에 read/write tool 을 등록한다.
 * - getFacilities: read:facility scope
 * - getBookings: read:booking scope
 * - getFacilityStats: read:facility:stats scope
 * - getOperatorProfile: read:operator:profile scope
 * - getNotifications: read:notification scope
 * - getGoodsSales: read:goods:sales scope
 * - getInventory: read:goods:inventory scope
 * - getTicketSales: read:ticket:sales scope
 * - cancelBooking: write:booking scope (2-step confirm flow)
 * - createSlot: write:slot scope (2-step confirm flow)
 * - updateSlot: write:slot scope (2-step confirm flow)
 * - deleteSlot: write:slot scope (2-step confirm flow)
 * - refundBooking: write:booking:refund scope (2-step confirm flow, Phase 2)
 * - issueComplimentaryTicket: write:ticket:complimentary scope (2-step confirm flow, Phase 2)
 */
@Configuration
@Profile("!test-jpa")
class McpToolRegistryConfig {

    @Bean
    fun mcpReadToolCallbackProvider(
        mcpFacilityTools: McpFacilityTools,
        mcpBookingTools: McpBookingTools,
        mcpFacilityStatsTools: McpFacilityStatsTools,
        mcpOperatorProfileTools: McpOperatorProfileTools,
        mcpNotificationTools: McpNotificationTools,
        mcpGoodsSalesTools: McpGoodsSalesTools,
        mcpInventoryTools: McpInventoryTools,
        mcpTicketSalesTools: McpTicketSalesTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(
                mcpFacilityTools,
                mcpBookingTools,
                mcpFacilityStatsTools,
                mcpOperatorProfileTools,
                mcpNotificationTools,
                mcpGoodsSalesTools,
                mcpInventoryTools,
                mcpTicketSalesTools,
            )
            .build()

    @Bean
    fun mcpWriteToolCallbackProvider(
        mcpBookingWriteTools: McpBookingWriteTools,
        mcpSlotWriteTools: McpSlotWriteTools,
        mcpRefundTools: McpRefundTools,
        mcpComplimentaryTicketTools: McpComplimentaryTicketTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(mcpBookingWriteTools, mcpSlotWriteTools, mcpRefundTools, mcpComplimentaryTicketTools)
            .build()
}
