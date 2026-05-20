package com.sportsapp.infrastructure.persistence.audit

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.common.BaseDocument
import com.sportsapp.domain.user.UserPrincipal
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl

@Document(collection = "test_audit_documents")
class TestAuditDocument(
    @Id val id: String? = null,
    val name: String,
) : BaseDocument()

/**
 * R-01, R-02: MongoAuditorAware 레포지토리 통합 테스트
 * - Testcontainers MongoDB 사용
 * - 인증 컨텍스트 설정 후 save → createdBy 검증
 */
class MongoAuditorAwareRepositoryTest(
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        beforeEach {
            mongoTemplate.dropCollection(TestAuditDocument::class.java)
            SecurityContextHolder.clearContext()
        }

        afterEach {
            SecurityContextHolder.clearContext()
        }

        Given("인증 컨텍스트에 userId=99 사용자가 설정된 상태") {
            When("BaseDocument 상속 도큐먼트를 save하면") {
                val userId = 99L
                val principal = UserPrincipal(id = userId, email = "auditor@test.com", roles = listOf("ROLE_USER"))
                val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                SecurityContextHolder.setContext(SecurityContextImpl(authentication))

                val document = TestAuditDocument(name = "테스트 도큐먼트")
                val saved = mongoTemplate.save(document)
                val savedId = requireNotNull(saved.id) { "saved document must have id" }
                val found = mongoTemplate.findById<TestAuditDocument>(savedId)

                Then("[R-01] 도큐먼트가 저장된다") {
                    found.shouldNotBeNull()
                }

                Then("[R-01] createdBy가 authUserId(99)로 기록된다") {
                    found?.createdBy shouldBe userId
                }

                Then("[R-01] updatedBy가 authUserId(99)로 기록된다") {
                    found?.updatedBy shouldBe userId
                }

                Then("[R-01] createdAt이 자동으로 채워진다") {
                    found?.createdAt.shouldNotBeNull()
                }
            }
        }

        Given("인증 컨텍스트가 없는 상태") {
            When("BaseDocument 상속 도큐먼트를 save하면") {
                SecurityContextHolder.clearContext()

                val document = TestAuditDocument(name = "익명 도큐먼트")
                val saved = mongoTemplate.save(document)
                val savedId = requireNotNull(saved.id) { "saved document must have id" }
                val found = mongoTemplate.findById<TestAuditDocument>(savedId)

                Then("[R-02] 도큐먼트가 저장된다") {
                    found.shouldNotBeNull()
                }

                Then("[R-02] createdBy가 null이다") {
                    found?.createdBy.shouldBeNull()
                }

                Then("[R-02] createdAt은 자동으로 채워진다") {
                    found?.createdAt.shouldNotBeNull()
                }
            }
        }
    }
}
