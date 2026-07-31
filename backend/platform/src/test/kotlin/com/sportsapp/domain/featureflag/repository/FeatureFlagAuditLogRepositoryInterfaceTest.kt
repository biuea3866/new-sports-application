package com.sportsapp.domain.featureflag.repository

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.lang.reflect.Modifier

/**
 * `FeatureFlagAuditLogRepository` 인터페이스 시그니처 검증 — 감사 조회는
 * `findByFlagKey(key, pageable): Page<FeatureFlagAuditLog>` (McpAuditLogRepository 선례 미러).
 */
class FeatureFlagAuditLogRepositoryInterfaceTest : BehaviorSpec({

    Given("FeatureFlagAuditLogRepository 인터페이스") {
        val cls = FeatureFlagAuditLogRepository::class.java

        Then("save 메서드가 (FeatureFlagAuditLog): FeatureFlagAuditLog 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "save" })
            method.returnType shouldBe FeatureFlagAuditLog::class.java
            method.parameterTypes.toList() shouldBe listOf(FeatureFlagAuditLog::class.java)
        }

        Then("findByFlagKey 메서드가 (String, Pageable): Page 시그니처로 선언돼 있다") {
            val method = requireNotNull(cls.declaredMethods.find { it.name == "findByFlagKey" })
            method.returnType shouldBe Page::class.java
            method.parameterTypes.toList() shouldBe listOf(String::class.java, Pageable::class.java)
        }

        Then("FeatureFlagAuditLogRepository 는 인터페이스(추상)이다") {
            Modifier.isInterface(cls.modifiers) shouldBe true
        }
    }
})
