package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.BaseJpaIntegrationTest
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired

/**
 * [B2B-01] V17 마이그레이션 — Role/Permission/RolePermission 시드 검증
 */
class B2bSeedRepositoryTest(
    @Autowired private val roleJpaRepository: RoleJpaRepository,
    @Autowired private val permissionJpaRepository: PermissionJpaRepository,
    @Autowired private val rolePermissionJpaRepository: RolePermissionJpaRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("V17 마이그레이션이 적용된 상태") {

            When("roles 테이블을 조회하면") {
                Then("[R-01] EVENT_HOST 롤이 존재한다") {
                    roleJpaRepository.findByNameAndDeletedAtIsNull("EVENT_HOST").shouldNotBeNull()
                }

                Then("[R-02] GOODS_SELLER 롤이 존재한다") {
                    roleJpaRepository.findByNameAndDeletedAtIsNull("GOODS_SELLER").shouldNotBeNull()
                }
            }

            When("permissions 테이블을 조회하면") {
                Then("[R-03] event:write 퍼미션이 존재한다") {
                    permissionJpaRepository.findByNameAndDeletedAtIsNull("event:write").shouldNotBeNull()
                }
            }

            When("EVENT_HOST 롤의 role_permissions 를 조회하면") {
                Then("[R-04] event:write, event:read, b2b:dashboard:read 퍼미션이 모두 포함된다") {
                    val eventHostRole = roleJpaRepository.findByNameAndDeletedAtIsNull("EVENT_HOST")
                    eventHostRole.shouldNotBeNull()

                    val rolePermissions = rolePermissionJpaRepository.findByRoleIdAndDeletedAtIsNull(eventHostRole.id)

                    val permissionNames = rolePermissions.mapNotNull { rolePermission ->
                        permissionJpaRepository.findByIdAndDeletedAtIsNull(rolePermission.permissionId)?.name
                    }

                    permissionNames shouldContainAll listOf("event:write", "event:read", "b2b:dashboard:read")
                }
            }

            When("FACILITY_OWNER 롤의 role_permissions 를 조회하면") {
                Then("[R-05] facility:write, facility:read, b2b:dashboard:read 퍼미션이 모두 포함된다") {
                    val facilityOwnerRole = roleJpaRepository.findByNameAndDeletedAtIsNull("FACILITY_OWNER")
                    facilityOwnerRole.shouldNotBeNull()

                    val rolePermissions = rolePermissionJpaRepository.findByRoleIdAndDeletedAtIsNull(facilityOwnerRole.id)

                    val permissionNames = rolePermissions.mapNotNull { rolePermission ->
                        permissionJpaRepository.findByIdAndDeletedAtIsNull(rolePermission.permissionId)?.name
                    }

                    permissionNames shouldContainAll listOf("facility:write", "facility:read", "b2b:dashboard:read")
                }
            }

            When("GOODS_SELLER 롤의 role_permissions 를 조회하면") {
                Then("[R-06] product:write, product:read, b2b:dashboard:read 퍼미션이 모두 포함된다") {
                    val goodsSellerRole = roleJpaRepository.findByNameAndDeletedAtIsNull("GOODS_SELLER")
                    goodsSellerRole.shouldNotBeNull()

                    val rolePermissions = rolePermissionJpaRepository.findByRoleIdAndDeletedAtIsNull(goodsSellerRole.id)

                    val permissionNames = rolePermissions.mapNotNull { rolePermission ->
                        permissionJpaRepository.findByIdAndDeletedAtIsNull(rolePermission.permissionId)?.name
                    }

                    permissionNames shouldContainAll listOf("product:write", "product:read", "b2b:dashboard:read")
                }
            }
        }
    }
}
