package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.RoomType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CreateRoomUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional
    fun execute(command: CreateRoomCommand): RoomResponse {
        val room = when {
            command.participantIds.size == 2 && command.name == null ->
                messageDomainService.createOrFindOneToOne(
                    command.participantIds[0],
                    command.participantIds[1],
                )
            command.name != null ->
                messageDomainService.createGroupRoom(command.name)
            else ->
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Group room requires a name")
        }
        return RoomResponse.of(room)
    }
}
