package com.sportsapp.application.user

import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.domain.user.UserStatus
import com.sportsapp.domain.user.UserWithRoles
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ListUsersUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val listUsersUseCase = ListUsersUseCase(userDomainService)

    Given("[U-01] 필터 없이 전체 사용자 목록을 조회하면") {
        val pageable = PageRequest.of(0, 10)
        val command = ListUsersCommand(emailKeyword = null, roleName = null, pageable = pageable)
        val fakeUserWithRoles = UserWithRoles(
            userId = 1L,
            email = "user@example.com",
            status = UserStatus.ACTIVE,
            roleNames = listOf("USER"),
            joinedAt = ZonedDateTime.now(),
        )
        every {
            userDomainService.listUsers(
                emailKeyword = null,
                roleName = null,
                pageable = pageable,
            )
        } returns PageImpl(listOf(fakeUserWithRoles), pageable, 1L)

        When("execute 를 호출하면") {
            val result = listUsersUseCase.execute(command)

            Then("[U-01] UserDomainService.listUsers 가 null 필터로 1회 호출되고 결과가 반환된다") {
                verify(exactly = 1) {
                    userDomainService.listUsers(
                        emailKeyword = null,
                        roleName = null,
                        pageable = pageable,
                    )
                }
                result.totalElements shouldBe 1L
                result.content[0].email shouldBe "user@example.com"
            }
        }
    }

    Given("[U-02] emailKeyword=test, roleName=ADMIN 필터로 목록을 조회하면") {
        val pageable = PageRequest.of(0, 10)
        val command = ListUsersCommand(emailKeyword = "test", roleName = "ADMIN", pageable = pageable)
        every {
            userDomainService.listUsers(
                emailKeyword = "test",
                roleName = "ADMIN",
                pageable = pageable,
            )
        } returns PageImpl(emptyList(), pageable, 0L)

        When("execute 를 호출하면") {
            val result = listUsersUseCase.execute(command)

            Then("[U-02] 필터 조건이 그대로 DomainService 에 전달되고 빈 Page 가 반환된다") {
                verify(exactly = 1) {
                    userDomainService.listUsers(
                        emailKeyword = "test",
                        roleName = "ADMIN",
                        pageable = pageable,
                    )
                }
                result.totalElements shouldBe 0L
            }
        }
    }
})
