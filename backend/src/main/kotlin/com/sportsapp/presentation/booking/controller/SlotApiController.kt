package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.CloseSlotCommand
import com.sportsapp.application.booking.dto.DeleteSlotCommand
import com.sportsapp.application.booking.dto.OpenSlotCommand
import com.sportsapp.application.booking.usecase.CloseSlotUseCase
import com.sportsapp.application.booking.usecase.CreateSlotUseCase
import com.sportsapp.application.booking.usecase.DeleteSlotUseCase
import com.sportsapp.application.booking.usecase.ListSlotsUseCase
import com.sportsapp.application.booking.usecase.OpenSlotUseCase
import com.sportsapp.application.booking.usecase.UpdateSlotUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.booking.dto.request.CreateSlotRequest
import com.sportsapp.presentation.booking.dto.request.UpdateSlotRequest
import com.sportsapp.presentation.booking.dto.response.SlotResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/facilities/{facilityId}/slots")
class SlotApiController(
    private val createSlotUseCase: CreateSlotUseCase,
    private val updateSlotUseCase: UpdateSlotUseCase,
    private val listSlotsUseCase: ListSlotsUseCase,
    private val deleteSlotUseCase: DeleteSlotUseCase,
    private val closeSlotUseCase: CloseSlotUseCase,
    private val openSlotUseCase: OpenSlotUseCase,
) {
    @PostMapping
    fun createSlot(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable facilityId: String,
        @RequestBody request: CreateSlotRequest,
    ): ResponseEntity<SlotResponse> {
        val slot = createSlotUseCase.execute(request.toCommand(principal.id, facilityId))
        return ResponseEntity.status(HttpStatus.CREATED).body(SlotResponse.of(slot))
    }

    @GetMapping
    fun listSlots(
        @PathVariable facilityId: String,
        @RequestParam(required = false) programId: Long?,
    ): ResponseEntity<List<SlotResponse>> =
        ResponseEntity.ok(listSlotsUseCase.execute(facilityId, programId).map { SlotResponse.of(it) })

    @PatchMapping("/{slotId}")
    fun updateSlot(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable slotId: Long,
        @RequestBody request: UpdateSlotRequest,
    ): ResponseEntity<SlotResponse> {
        val slot = updateSlotUseCase.execute(request.toCommand(principal.id, slotId))
        return ResponseEntity.ok(SlotResponse.of(slot))
    }

    @PatchMapping("/{slotId}/close")
    fun closeSlot(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable slotId: Long,
    ): ResponseEntity<SlotResponse> {
        val slot = closeSlotUseCase.execute(CloseSlotCommand(requesterId = principal.id, slotId = slotId))
        return ResponseEntity.ok(SlotResponse.of(slot))
    }

    @PatchMapping("/{slotId}/open")
    fun openSlot(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable slotId: Long,
    ): ResponseEntity<SlotResponse> {
        val slot = openSlotUseCase.execute(OpenSlotCommand(requesterId = principal.id, slotId = slotId))
        return ResponseEntity.ok(SlotResponse.of(slot))
    }

    @DeleteMapping("/{slotId}")
    fun deleteSlot(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable slotId: Long,
    ): ResponseEntity<Void> {
        deleteSlotUseCase.execute(DeleteSlotCommand(requesterId = principal.id, slotId = slotId))
        return ResponseEntity.noContent().build()
    }
}
