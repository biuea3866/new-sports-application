package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.repository.UserCustomRepository
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.entity.UserStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate

class UserCustomRepositoryImplTest(
    @Autowired private val userCustomRepository: UserCustomRepository,
    @Autowired private val userJpaRepository: UserJpaRepository,
    @Autowired private val roleJpaRepository: RoleJpaRepository,
    @Autowired private val userRoleJpaRepository: UserRoleJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%custom-repo-test%')")
        jdbcTemplate.execute("DELETE FROM users WHERE email LIKE '%custom-repo-test%'")
    }

    private fun createUser(email: String, status: UserStatus = UserStatus.ACTIVE): User =
        userJpaRepository.save(User(email = email, passwordHash = "hash", status = status))

    private fun assignRole(userId: Long, roleName: String) {
        val role = roleJpaRepository.findByNameAndDeletedAtIsNull(roleName) ?: return
        userRoleJpaRepository.save(UserRole(userId = userId, roleId = role.id, grantedBy = null))
    }

    init {
        afterEach { resetData() }

        Given("[R-01] email 부분검색 필터가 있는 경우") {
            resetData()
            createUser("alpha-custom-repo-test@example.com")
            createUser("beta-custom-repo-test@example.com")
            createUser("gamma-custom-repo-test@example.com")

            When("emailKeyword=alpha 로 조회하면") {
                Then("[R-01] email 에 alpha 가 포함된 사용자 1건만 반환된다") {
                    val result = userCustomRepository.findAllWithRoles(
                        emailKeyword = "alpha",
                        roleName = null,
                        pageable = PageRequest.of(0, 10),
                    )
                    result.totalElements shouldBe 1L
                    result.content[0].email shouldBe "alpha-custom-repo-test@example.com"
                }
            }
        }

        Given("[R-02] role 필터가 있는 경우") {
            resetData()
            val user1 = createUser("role-admin-custom-repo-test@example.com")
            val user2 = createUser("role-user-custom-repo-test@example.com")
            assignRole(user1.id, "ADMIN")
            assignRole(user2.id, "USER")

            When("roleName=ADMIN 으로 조회하면") {
                Then("[R-02] ADMIN 역할을 가진 사용자만 반환된다") {
                    val result = userCustomRepository.findAllWithRoles(
                        emailKeyword = null,
                        roleName = "ADMIN",
                        pageable = PageRequest.of(0, 10),
                    )
                    result.content.all { userWithRoles -> userWithRoles.roleNames.contains("ADMIN") } shouldBe true
                    result.content.any { userWithRoles -> userWithRoles.email == "role-admin-custom-repo-test@example.com" } shouldBe true
                }
            }
        }

        Given("[R-03] 페이지네이션 검증") {
            resetData()
            repeat(5) { index ->
                createUser("paging-$index-custom-repo-test@example.com")
            }

            When("size=2, page=0 으로 조회하면") {
                Then("[R-03] totalElements=5, content 2건, totalPages=3 이 반환된다") {
                    val result = userCustomRepository.findAllWithRoles(
                        emailKeyword = "paging",
                        roleName = null,
                        pageable = PageRequest.of(0, 2),
                    )
                    result.totalElements shouldBe 5L
                    result.content.size shouldBe 2
                    result.totalPages shouldBe 3
                }
            }
        }

        Given("[R-04] 정렬 검증 — createdAt ASC") {
            resetData()
            val userA = createUser("sort-a-custom-repo-test@example.com")
            val userB = createUser("sort-b-custom-repo-test@example.com")

            When("sort=id ASC 로 조회하면") {
                Then("[R-04] id 오름차순으로 정렬된 결과가 반환된다") {
                    val result = userCustomRepository.findAllWithRoles(
                        emailKeyword = "sort",
                        roleName = null,
                        pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")),
                    )
                    result.totalElements shouldBe 2L
                    result.content[0].userId shouldBe userA.id
                    result.content[1].userId shouldBe userB.id
                }
            }
        }

        Given("[R-05] role 정보가 포함된 응답 검증") {
            resetData()
            val user = createUser("roles-custom-repo-test@example.com")
            assignRole(user.id, "USER")
            assignRole(user.id, "FACILITY_OWNER")

            When("해당 사용자를 목록 조회하면") {
                Then("[R-05] 사용자의 역할 목록에 USER 와 FACILITY_OWNER 가 모두 포함된다") {
                    val result = userCustomRepository.findAllWithRoles(
                        emailKeyword = "roles-custom-repo-test",
                        roleName = null,
                        pageable = PageRequest.of(0, 10),
                    )
                    result.totalElements shouldBe 1L
                    val roles = result.content[0].roleNames
                    roles.containsAll(listOf("USER", "FACILITY_OWNER")) shouldBe true
                }
            }
        }

        Given("역할이 여러 개 부여된 사용자가 존재하면") {
            resetData()
            val user = createUser("single-fetch-custom-repo-test@example.com")
            assignRole(user.id, "USER")
            assignRole(user.id, "FACILITY_OWNER")

            When("findByIdWithRoles로 단건 조회하면") {
                Then("User 정보와 역할 목록이 단일 쿼리 결과로 함께 반환된다") {
                    val result = userCustomRepository.findByIdWithRoles(user.id)

                    result.shouldNotBeNull()
                    result.email shouldBe "single-fetch-custom-repo-test@example.com"
                    result.roleNames.containsAll(listOf("USER", "FACILITY_OWNER")) shouldBe true
                }
            }
        }

        Given("존재하지 않는 사용자 ID가 주어지면") {
            When("findByIdWithRoles로 단건 조회하면") {
                Then("null이 반환된다") {
                    userCustomRepository.findByIdWithRoles(-1L).shouldBeNull()
                }
            }
        }
    }
}
