package com.sportsapp.presentation.booking

import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.ListSlotsUseCase
import com.sportsapp.application.booking.SlotResponse
import com.sportsapp.application.booking.UpdateSlotUseCase
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
        val response = createSlotUseCase.execute(request.toCommand(userId, facilityId))
        return ResponseEntity.status(201).body(response)
    }

    @GetMapping
    fun listSlots(
        @PathVariable facilityId: String,
    ): ResponseEntity<List<SlotResponse>> =
        ResponseEntity.ok(listSlotsUseCase.execute(facilityId))

    @PatchMapping("/{slotId}")
    fun updateSlot(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable slotId: Long,
        @RequestBody request: UpdateSlotRequest,
    ): ResponseEntity<SlotResponse> {
        val response = updateSlotUseCase.execute(request.toCommand(userId, slotId))
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{slotId}")
    fun deleteSlot(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable slotId: Long,
    ): ResponseEntity<Void> {
        deleteSlotUseCase.execute(userId, slotId)
        return ResponseEntity.noContent().build()
    }
}
