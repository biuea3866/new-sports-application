package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

class UserRepositoryTest(
    @Autowired private val userJpaRepository: UserJpaRepository,
    @Autowired private val roleJpaRepository: RoleJpaRepository,
    @Autowired private val userRoleJpaRepository: UserRoleJpaRepository,
    @Autowired private val userRepository: UserRepository,
) : BaseIntegrationTest() {

    init {
        Given("동일한 email 로 두 번 insert 하면") {
            When("UserJpaRepository.save 를 두 번 호출하면") {
                Then("[R-01] DataIntegrityViolationException 이 발생한다") {
                    val first = User(
                        email = "dup@example.com",
                        passwordHash = "hash",
                        status = UserStatus.ACTIVE,
                    )
                    userJpaRepository.save(first)
                    shouldThrow<DataIntegrityViolationException> {
                        val second = User(
                            email = "dup@example.com",
                            passwordHash = "hash2",
                            status = UserStatus.ACTIVE,
                        )
                        userJpaRepository.saveAndFlush(second)
                    }
                }
            }
        }

        Given("존재하는 email 과 존재하지 않는 email") {
            userJpaRepository.save(
                User(
                    email = "find@example.com",
                    passwordHash = "hash",
                    status = UserStatus.ACTIVE,
                )
            )

            When("findByEmail 로 조회하면") {
                Then("[R-02] 매칭되는 사용자 1건 또는 null 을 반환한다") {
                    userJpaRepository.findByEmailAndDeletedAtIsNull("find@example.com").shouldNotBeNull()
                    userJpaRepository.findByEmailAndDeletedAtIsNull("notfound@example.com").shouldBeNull()
                }
            }
        }

        Given("user_roles 중복 검증") {
            When("동일 (user_id, role_id) 로 활성 row 가 이미 존재하면") {
                Then("[R-03] existsByUserIdAndRoleIdAndDeletedAtIsNull 이 true 를 반환해 application 레벨에서 중복을 차단한다") {
                    val userEntity = userJpaRepository.save(
                        User(
                            email = "roledup@example.com",
                            passwordHash = "hash",
                            status = UserStatus.ACTIVE,
                        )
                    )
                    val roleEntity = roleJpaRepository.findByNameAndDeletedAtIsNull("USER")
                    roleEntity.shouldNotBeNull()

                    userRoleJpaRepository.save(UserRole(userId = userEntity.id, roleId = roleEntity.id, grantedBy = null))

                    userRoleJpaRepository.existsByUserIdAndRoleIdAndDeletedAtIsNull(
                        userEntity.id,
                        roleEntity.id,
                    ) shouldBe true
                }
            }
        }

        Given("닉네임이 있는 사용자와 없는 사용자") {
            val nicknamedUser = userJpaRepository.save(
                User.create("nickname-batch@example.com", "hash", "김철수"),
            )
            val unsetNicknameUser = userJpaRepository.save(
                User(
                    email = "unset-nickname-batch@example.com",
                    passwordHash = "hash",
                    status = UserStatus.ACTIVE,
                )
            )

            When("findAllBy 로 id 목록을 한 번에 조회하면") {
                val users = userRepository.findAllBy(
                    listOf(nicknamedUser.id, unsetNicknameUser.id, -1L),
                )

                Then("[R-05] nickname 컬럼이 왕복 저장·조회된다") {
                    users.map { it.id }.toSet() shouldBe setOf(nicknamedUser.id, unsetNicknameUser.id)
                    users.first { it.id == nicknamedUser.id }.nickname shouldBe "김철수"
                    users.first { it.id == unsetNicknameUser.id }.nickname.shouldBeNull()
                }
            }

            When("빈 목록으로 조회하면") {
                Then("[R-05] 빈 결과를 반환한다") {
                    userRepository.findAllBy(emptyList()).isEmpty() shouldBe true
                }
            }
        }

        Given("Flyway V2 마이그레이션이 완료된 상태") {
            When("roles 테이블을 전체 조회하면") {
                Then("[R-04] USER / ADMIN / FACILITY_OWNER 3개 Role 이 존재한다") {
                    val roleNames = roleJpaRepository.findAllByDeletedAtIsNull().map { it.name }.toSet()
                    roleNames.containsAll(setOf("USER", "ADMIN", "FACILITY_OWNER")) shouldBe true
                }
            }
        }
    }
}
