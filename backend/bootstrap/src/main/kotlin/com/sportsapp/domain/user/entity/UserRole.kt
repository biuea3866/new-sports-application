package com.sportsapp.domain.user.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user_roles")
class UserRole(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "role_id", nullable = false)
    val roleId: Long,
    @Column(name = "granted_by")
    val grantedBy: Long?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0
}
