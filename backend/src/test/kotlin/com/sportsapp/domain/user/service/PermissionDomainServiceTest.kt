package com.sportsapp.domain.user.service

import com.sportsapp.domain.user.entity.Permission
import com.sportsapp.domain.user.repository.PermissionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class PermissionDomainServiceTest : BehaviorSpec({

    fun makePermission(name: String, id: Long): Permission {
        val permission = Permission(name = name)
        val idField = Permission::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(permission, id)
        val superclass = permission.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(permission, ZonedDateTime.now())
        }
        return permission
    }

    val permissionRepository = mockk<PermissionRepository>()
    val permissionDomainService = PermissionDomainService(permissionRepository)

    Given("[U-01] 존재하는 권한명으로 findIdByName을 호출하면") {
        every { permissionRepository.findByName("mcp.facility.read.own") } returns makePermission("mcp.facility.read.own", 10L)

        When("findIdByName을 호출하면") {
            val result = permissionDomainService.findIdByName("mcp.facility.read.own")

            Then("[U-01] 해당 권한 id가 반환된다") {
                result shouldBe 10L
            }
        }
    }

    Given("[U-02] 존재하지 않는 권한명으로 findIdByName을 호출하면") {
        every { permissionRepository.findByName("mcp.unknown.read.own") } returns null

        When("findIdByName을 호출하면") {
            val result = permissionDomainService.findIdByName("mcp.unknown.read.own")

            Then("[U-02] null이 반환된다") {
                result.shouldBeNull()
            }
        }
    }

    Given("[U-03] 여러 권한 id로 findNamesByIds를 호출하면") {
        every { permissionRepository.findAllByIds(listOf(10L, 20L)) } returns listOf(
            makePermission("mcp.facility.read.own", 10L),
            makePermission("mcp.booking.write.own", 20L),
        )

        When("findNamesByIds를 호출하면") {
            val result = permissionDomainService.findNamesByIds(listOf(10L, 20L))

            Then("[U-03] id-이름 매핑이 반환된다") {
                result.shouldContainExactly(mapOf(10L to "mcp.facility.read.own", 20L to "mcp.booking.write.own"))
            }
        }
    }

    Given("[U-04] 존재하지 않는 id가 섞인 목록으로 findNamesByIds를 호출하면") {
        every { permissionRepository.findAllByIds(listOf(10L, 999L)) } returns listOf(
            makePermission("mcp.facility.read.own", 10L),
        )

        When("findNamesByIds를 호출하면") {
            val result = permissionDomainService.findNamesByIds(listOf(10L, 999L))

            Then("[U-04] 존재하는 id만 담긴 매핑이 반환된다 (엣지)") {
                result.shouldContainExactly(mapOf(10L to "mcp.facility.read.own"))
            }
        }
    }
})
