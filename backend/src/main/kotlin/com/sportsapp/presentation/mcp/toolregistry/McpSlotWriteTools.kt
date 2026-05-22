package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.CreateSlotCommand
import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotCommand
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.UpdateSlotCommand
import com.sportsapp.application.booking.UpdateSlotUseCase
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.ZonedDateTime

/**
 * MCP Write tool — 슬롯(Slot) CRUD.
 * scope: write:slot
 *
 * 2-step confirm flow:
 *   1차 호출 (confirmationToken = null) → 토큰 발급 + CONFIRM_REQUIRED 반환
 *   2차 호출 (confirmationToken = 발급 토큰) → 토큰 소진 + UseCase 실행 + OK 반환
 */
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
    fun createSlot(
        ownerId: Long,
        facilityId: String,
        date: String,
        timeRange: String,
        capacity: Int,
        confirmationToken: String?,
    ): McpResponse<*> {
        if (confirmationToken == null) {
            return issueConfirmation("createSlot", ownerId, hashParams("createSlot", ownerId, facilityId, date, timeRange, capacity),
                mapOf("ownerId" to ownerId, "facilityId" to facilityId, "date" to date,
                    "timeRange" to timeRange, "capacity" to capacity,
                    "message" to "시설 $facilityId 에 슬롯을 생성하시겠습니까?"),
            )
        }
        confirmationTokenGateway.consume(confirmationToken)
        return McpResponse.ok(data = createSlotUseCase.execute(
            CreateSlotCommand(ownerId = ownerId, facilityId = facilityId,
                date = ZonedDateTime.parse(date), timeRange = timeRange, capacity = capacity)
        ))
    }

    @PreAuthorize("@authz.hasMcpScope('write:slot')")
    @Tool(
        name = "updateSlot",
        description = "기존 슬롯의 timeRange 또는 capacity 를 수정합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 수정됩니다.",
    )
    fun updateSlot(
        requesterId: Long,
        slotId: Long,
        timeRange: String?,
        capacity: Int?,
        confirmationToken: String?,
    ): McpResponse<*> {
        if (confirmationToken == null) {
            return issueConfirmation("updateSlot", requesterId, hashParams("updateSlot", requesterId, slotId, timeRange, capacity),
                mapOf("requesterId" to requesterId, "slotId" to slotId,
                    "timeRange" to (timeRange ?: ""), "capacity" to (capacity ?: 0),
                    "message" to "슬롯 $slotId 를 수정하시겠습니까?"),
            )
        }
        confirmationTokenGateway.consume(confirmationToken)
        return McpResponse.ok(data = updateSlotUseCase.execute(
            UpdateSlotCommand(requesterId = requesterId, slotId = slotId,
                timeRange = timeRange, capacity = capacity)
        ))
    }

    @PreAuthorize("@authz.hasMcpScope('write:slot')")
    @Tool(
        name = "deleteSlot",
        description = "슬롯을 삭제합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 삭제됩니다.",
    )
    fun deleteSlot(
        requesterId: Long,
        slotId: Long,
        confirmationToken: String?,
    ): McpResponse<*> {
        if (confirmationToken == null) {
            return issueConfirmation("deleteSlot", requesterId, hashParams("deleteSlot", requesterId, slotId),
                mapOf("requesterId" to requesterId, "slotId" to slotId,
                    "message" to "슬롯 $slotId 를 삭제하시겠습니까?"),
            )
        }
        confirmationTokenGateway.consume(confirmationToken)
        deleteSlotUseCase.execute(DeleteSlotCommand(requesterId = requesterId, slotId = slotId))
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

    private fun hashParams(vararg parts: Any?): String {
        val raw = parts.joinToString(":") { it?.toString() ?: "" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
