package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.DeleteSlotCommand
import com.sportsapp.application.booking.usecase.CreateSlotUseCase
import com.sportsapp.application.booking.usecase.DeleteSlotUseCase
import com.sportsapp.application.booking.usecase.ListSlotsUseCase
import com.sportsapp.application.booking.usecase.UpdateSlotUseCase
import com.sportsapp.presentation.booking.dto.request.CreateSlotRequest
import com.sportsapp.presentation.booking.dto.request.UpdateSlotRequest
import com.sportsapp.presentation.booking.dto.response.SlotResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/facilities/{facilityId}/slots")
class SlotApiController(
    private val createSlotUseCase: CreateSlotUseCase,
    private val updateSlotUseCase: UpdateSlotUseCase,
    private val listSlotsUseCase: ListSlotsUseCase,
    private val deleteSlotUseCase: DeleteSlotUseCase,
) {
    @PostMapping
    fun createSlot(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable facilityId: String,
        @RequestBody request: CreateSlotRequest,
    ): ResponseEntity<SlotResponse> {
        if (userId == 0L) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val slot = createSlotUseCase.execute(request.toCommand(userId, facilityId))
        return ResponseEntity.status(HttpStatus.CREATED).body(SlotResponse.of(slot))
    }

    @GetMapping
    fun listSlots(
        @PathVariable facilityId: String,
    ): ResponseEntity<List<SlotResponse>> =
        ResponseEntity.ok(listSlotsUseCase.execute(facilityId).map { SlotResponse.of(it) })

    @PatchMapping("/{slotId}")
    fun updateSlot(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable slotId: Long,
        @RequestBody request: UpdateSlotRequest,
    ): ResponseEntity<SlotResponse> {
        if (userId == 0L) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val slot = updateSlotUseCase.execute(request.toCommand(userId, slotId))
        return ResponseEntity.ok(SlotResponse.of(slot))
    }

    @DeleteMapping("/{slotId}")
    fun deleteSlot(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable slotId: Long,
    ): ResponseEntity<Void> {
        if (userId == 0L) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        deleteSlotUseCase.execute(DeleteSlotCommand(requesterId = userId, slotId = slotId))
        return ResponseEntity.noContent().build()
    }
}
