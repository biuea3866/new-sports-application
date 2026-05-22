package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.CreateSlotCommand
import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotCommand
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.UpdateSlotCommand
import com.sportsapp.application.booking.UpdateSlotUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.confirm.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
@Profile("!test-jpa")
class McpSlotWriteTools(
    private val createSlotUseCase: CreateSlotUseCase,
    private val updateSlotUseCase: UpdateSlotUseCase,
    private val deleteSlotUseCase: DeleteSlotUseCase,
    private val confirmationTokenGateway: ConfirmationTokenGateway,
) {

    @PreAuthorize("@authz.hasMcpScope('write:slot')")
    @Tool(
        name = "createSlot",
        description = "새로운 예약 슬롯을 생성합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 슬롯이 생성됩니다.",
    )
    fun createSlot(facilityId: String, date: String, timeRange: String, capacity: Int, confirmationToken: String?): McpResponse<*> {
        val callerUserId = resolveCallerUserId()
        val paramsHash = McpParamsHasher.hash("createSlot", callerUserId, facilityId, date, timeRange, capacity)
        if (confirmationToken == null) {
            return issueConfirmation(
                toolName = "createSlot",
                userId = callerUserId,
                paramsHash = paramsHash,
                metadata = mapOf("facilityId" to facilityId, "date" to date,
                    "timeRange" to timeRange, "capacity" to capacity,
                    "message" to "시설 $facilityId 에 슬롯을 생성하시겠습니까?"),
            )
        }
        validateHashAndConsume(confirmationToken, paramsHash)
        return McpResponse.ok(data = createSlotUseCase.execute(
            CreateSlotCommand(ownerId = callerUserId, facilityId = facilityId,
                date = ZonedDateTime.parse(date), timeRange = timeRange, capacity = capacity)
        ))
    }

    @PreAuthorize("@authz.hasMcpScope('write:slot')")
    @Tool(
        name = "updateSlot",
        description = "기존 슬롯의 timeRange 또는 capacity 를 수정합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 수정됩니다.",
    )
    fun updateSlot(slotId: Long, timeRange: String?, capacity: Int?, confirmationToken: String?): McpResponse<*> {
        val callerUserId = resolveCallerUserId()
        val paramsHash = McpParamsHasher.hash("updateSlot", callerUserId, slotId, timeRange, capacity)
        if (confirmationToken == null) {
            return issueConfirmation(
                toolName = "updateSlot",
                userId = callerUserId,
                paramsHash = paramsHash,
                metadata = mapOf("slotId" to slotId, "timeRange" to (timeRange ?: ""),
                    "capacity" to (capacity ?: 0), "message" to "슬롯 $slotId 를 수정하시겠습니까?"),
            )
        }
        validateHashAndConsume(confirmationToken, paramsHash)
        return McpResponse.ok(data = updateSlotUseCase.execute(
            UpdateSlotCommand(requesterId = callerUserId, slotId = slotId, timeRange = timeRange, capacity = capacity)
        ))
    }

    @PreAuthorize("@authz.hasMcpScope('write:slot')")
    @Tool(
        name = "deleteSlot",
        description = "슬롯을 삭제합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 삭제됩니다.",
    )
    fun deleteSlot(slotId: Long, confirmationToken: String?): McpResponse<*> {
        val callerUserId = resolveCallerUserId()
        val paramsHash = McpParamsHasher.hash("deleteSlot", callerUserId, slotId)
        if (confirmationToken == null) {
            return issueConfirmation(
                toolName = "deleteSlot",
                userId = callerUserId,
                paramsHash = paramsHash,
                metadata = mapOf("slotId" to slotId, "message" to "슬롯 $slotId 를 삭제하시겠습니까?"),
            )
        }
        validateHashAndConsume(confirmationToken, paramsHash)
        deleteSlotUseCase.execute(DeleteSlotCommand(requesterId = callerUserId, slotId = slotId))
        return McpResponse.ok(data = mapOf("deletedSlotId" to slotId, "message" to "슬롯 $slotId 가 삭제되었습니다."))
    }

    private fun issueConfirmation(
        toolName: String,
        userId: Long,
        paramsHash: String,
        metadata: Map<String, Any>,
    ): McpResponse<Map<String, Any>> {
        val token = confirmationTokenGateway.issue(
            ConfirmationTokenContext(toolName = toolName, userId = userId, paramsHash = paramsHash)
        )
        return McpResponse.confirmRequired(data = metadata + ("confirmationToken" to token))
    }

    private fun validateHashAndConsume(confirmationToken: String, expectedHash: String) {
        val context = confirmationTokenGateway.consume(confirmationToken)
        if (context.paramsHash != expectedHash) throw ConfirmationParamsMismatchException(confirmationToken)
    }

    private fun resolveCallerUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        return principal.userId
    }
}
