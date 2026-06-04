package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.usecase.CreateRoomUseCase
import com.sportsapp.application.message.usecase.DeleteRoomUseCase
import com.sportsapp.application.message.usecase.GetRoomUseCase
import com.sportsapp.application.message.usecase.ListMyRoomsUseCase
import com.sportsapp.presentation.message.dto.request.CreateRoomRequest
import com.sportsapp.presentation.message.dto.response.RoomResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rooms")
class RoomApiController(
    private val createRoomUseCase: CreateRoomUseCase,
    private val getRoomUseCase: GetRoomUseCase,
    private val listMyRoomsUseCase: ListMyRoomsUseCase,
    private val deleteRoomUseCase: DeleteRoomUseCase,
) {

    @PostMapping
    fun createRoom(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @RequestBody request: CreateRoomRequest,
    ): ResponseEntity<RoomResponse> {
        val room = createRoomUseCase.execute(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.of(room))
    }

    @GetMapping("/{id}")
    fun getRoom(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable id: Long,
    ): ResponseEntity<RoomResponse> =
        ResponseEntity.ok(RoomResponse.of(getRoomUseCase.execute(id, userId)))

    @GetMapping("/me")
    fun listMyRooms(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<List<RoomResponse>> =
        ResponseEntity.ok(listMyRoomsUseCase.execute(userId, keyword).map { RoomResponse.of(it) })

    @DeleteMapping("/{id}")
    fun deleteRoom(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteRoomUseCase.execute(id, userId)
        return ResponseEntity.noContent().build()
    }
}
