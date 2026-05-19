package com.sportsapp.infrastructure.persistence.booking

import org.springframework.data.jpa.repository.JpaRepository

interface SlotJpaRepository : JpaRepository<SlotJpaEntity, Long>
