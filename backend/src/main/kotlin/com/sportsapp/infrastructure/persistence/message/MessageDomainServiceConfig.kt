package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.MessageRepository
import com.sportsapp.domain.message.RoomParticipantRepository
import com.sportsapp.domain.message.RoomRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessageDomainServiceConfig {

    @Bean
    fun messageDomainService(
        roomRepository: RoomRepository,
        messageRepository: MessageRepository,
        roomParticipantRepository: RoomParticipantRepository,
    ): MessageDomainService = MessageDomainService(roomRepository, messageRepository, roomParticipantRepository)
}
