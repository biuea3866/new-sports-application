package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.Message
import org.springframework.data.jpa.repository.JpaRepository

interface MessageJpaRepository : JpaRepository<Message, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Message?
}
