package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.CreateRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomDomainService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CreateRoomUseCase(
    private val roomDomainService: RoomDomainService,
) {
    @Transactional
    fun execute(command: CreateRoomCommand): Room {
        return when {
            command.participantIds.size == 2 && command.name == null -> {
                require(command.requestUserId in command.participantIds) {
                    "본인이 참여자에 포함되어야 합니다"
                }
                roomDomainService.createOrFindOneToOne(
                    command.participantIds[0],
                    command.participantIds[1],
                )
            }
            command.name != null -> {
                val allParticipantIds = if (command.requestUserId in command.participantIds) {
                    command.participantIds
                } else {
                    listOf(command.requestUserId) + command.participantIds
                }
                roomDomainService.createGroupRoom(command.name, allParticipantIds, hostUserId = command.requestUserId)
            }
            else ->
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Group room requires a name")
        }
    }
}
