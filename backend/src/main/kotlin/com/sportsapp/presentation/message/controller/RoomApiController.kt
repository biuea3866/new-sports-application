package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.usecase.CreateRoomUseCase
import com.sportsapp.application.message.usecase.DeleteRoomUseCase
import com.sportsapp.application.message.usecase.GetMyRoomParticipationUseCase
import com.sportsapp.application.message.usecase.GetRoomUseCase
import com.sportsapp.application.message.usecase.ListMyRoomsUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.message.dto.request.CreateRoomRequest
import com.sportsapp.presentation.message.dto.response.MyRoomParticipationResponse
import com.sportsapp.presentation.message.dto.response.RoomResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val getMyRoomParticipationUseCase: GetMyRoomParticipationUseCase,
) {

    @PostMapping
    fun createRoom(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateRoomRequest,
    ): ResponseEntity<RoomResponse> {
        val room = createRoomUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.of(room))
    }

    @GetMapping("/{id}")
    fun getRoom(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<RoomResponse> =
        ResponseEntity.ok(RoomResponse.of(getRoomUseCase.execute(id, principal.id)))

    @GetMapping("/me")
    fun listMyRooms(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<List<RoomResponse>> =
        ResponseEntity.ok(listMyRoomsUseCase.execute(principal.id, keyword).map { RoomResponse.of(it) })

    @GetMapping("/{roomId}/participation")
    fun getMyParticipation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): ResponseEntity<MyRoomParticipationResponse> = ResponseEntity.ok(
        MyRoomParticipationResponse.of(getMyRoomParticipationUseCase.execute(roomId, principal.id)),
    )

    @DeleteMapping("/{id}")
    fun deleteRoom(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteRoomUseCase.execute(id, principal.id)
        return ResponseEntity.noContent().build()
    }
}
