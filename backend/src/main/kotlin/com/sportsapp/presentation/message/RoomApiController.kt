package com.sportsapp.presentation.message

import com.sportsapp.application.message.CreateRoomUseCase
import com.sportsapp.application.message.DeleteRoomUseCase
import com.sportsapp.application.message.GetRoomUseCase
import com.sportsapp.application.message.ListMyRoomsUseCase
import com.sportsapp.application.message.RoomResponse
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
        val response = createRoomUseCase.execute(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun getRoom(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable id: Long,
    ): ResponseEntity<RoomResponse> {
        return ResponseEntity.ok(getRoomUseCase.execute(id, userId))
    }

    @GetMapping("/me")
    fun listMyRooms(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<List<RoomResponse>> {
        return ResponseEntity.ok(listMyRoomsUseCase.execute(userId, keyword))
    }

    @DeleteMapping("/{id}")
    fun deleteRoom(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteRoomUseCase.execute(id, userId)
        return ResponseEntity.noContent().build()
    }
}
