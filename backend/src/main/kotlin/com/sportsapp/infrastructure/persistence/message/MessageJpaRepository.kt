package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.Message
import org.springframework.data.jpa.repository.JpaRepository

interface MessageJpaRepository : JpaRepository<Message, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Message?
}
