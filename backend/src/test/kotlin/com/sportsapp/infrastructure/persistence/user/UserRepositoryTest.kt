package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.BaseIntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.ZonedDateTime

class UserRepositoryTest(
    @Autowired private val userJpaRepository: UserJpaRepository,
    @Autowired private val roleJpaRepository: RoleJpaRepository,
) : BaseIntegrationTest() {

    private val fixedTime = ZonedDateTime.parse("2026-01-01T00:00:00Z")

    init {
        Given("동일한 email 로 두 번 insert 하면") {
            When("UserJpaRepository.save 를 두 번 호출하면") {
                Then("[R-01] DataIntegrityViolationException 이 발생한다") {
                    val first = UserJpaEntity(
                        id = 0,
                        email = "dup@example.com",
                        passwordHash = "hash",
                        status = "ACTIVE",
                        createdAt = fixedTime,
                    )
                    userJpaRepository.save(first)
                    shouldThrow<DataIntegrityViolationException> {
                        val second = UserJpaEntity(
                            id = 0,
                            email = "dup@example.com",
                            passwordHash = "hash2",
                            status = "ACTIVE",
                            createdAt = fixedTime,
                        )
                        userJpaRepository.saveAndFlush(second)
                    }
                }
            }
        }

        Given("존재하는 email 과 존재하지 않는 email") {
            userJpaRepository.save(
                UserJpaEntity(
                    id = 0,
                    email = "find@example.com",
                    passwordHash = "hash",
                    status = "ACTIVE",
                    createdAt = fixedTime,
                )
            )

            When("findByEmail 로 조회하면") {
                Then("[R-02] 매칭되는 사용자 1건 또는 0건을 Optional 로 반환한다") {
                    userJpaRepository.findByEmail("find@example.com").isPresent shouldBe true
                    userJpaRepository.findByEmail("notfound@example.com").isPresent shouldBe false
                }
            }
        }

        Given("user_roles 복합 PK 검증") {
            When("동일 (user_id, role_id) 로 두 번 insert 하면") {
                Then("[R-03] DB 레벨에서 중복을 차단한다") {
                    val userEntity = userJpaRepository.save(
                        UserJpaEntity(
                            id = 0,
                            email = "roledup@example.com",
                            passwordHash = "hash",
                            status = "ACTIVE",
                            createdAt = fixedTime,
                        )
                    )
                    val roleEntity = roleJpaRepository.findByName("USER").orElse(null)
                    roleEntity.shouldNotBeNull()

                    userEntity.roles.add(roleEntity)
                    userJpaRepository.save(userEntity)

                    shouldThrow<Exception> {
                        userEntity.roles.add(roleEntity)
                        userJpaRepository.saveAndFlush(userEntity)
                    }
                }
            }
        }

        Given("Flyway V2 마이그레이션이 완료된 상태") {
            When("roles 테이블을 전체 조회하면") {
                Then("[R-04] USER / ADMIN / FACILITY_OWNER 3개 Role 이 존재한다") {
                    val roleNames = roleJpaRepository.findAll().map { it.name }.toSet()
                    roleNames.containsAll(setOf("USER", "ADMIN", "FACILITY_OWNER")) shouldBe true
                }
            }
        }
    }
}
