package com.sportsapp.infrastructure.config

import jakarta.annotation.PostConstruct
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component

/**
 * Spring Batch 메타데이터 테이블(BATCH_JOB_INSTANCE 등)을 최초 1회만 생성한다 (BE-11).
 *
 * Boot 기본값(`spring.batch.jdbc.initialize-schema=always`)은 존재 여부를 확인하지 않고 매 컨텍스트
 * 기동마다 스키마 스크립트를 재실행한다(BatchDataSourceScriptDatabaseInitializer 실측 확인). 이
 * 레포의 통합 테스트는 [com.sportsapp.SharedTestContainers]로 MySQL 컨테이너 하나를 여러 Spring
 * 컨텍스트(BaseIntegrationTest/BaseMongoIntegrationTest/BaseJpaIntegrationTest)가 공유하므로,
 * `always`를 켜면 두 번째 컨텍스트부터 "Table 'BATCH_JOB_INSTANCE' already exists"로 실패한다.
 * 이 컴포넌트는 `information_schema.tables`로 존재를 먼저 확인해 idempotent하게 생성하고,
 * `spring.batch.jdbc.initialize-schema=never`(application.yml)와 짝을 이룬다. Flyway 마이그레이션에
 * 담지 않는 이유는 이 스키마가 spring-batch-core가 배포하는 표준 스크립트를 그대로 쓰는 프레임워크
 * 메타데이터이지, 이 앱의 DDL 변경 이력이 아니기 때문이다.
 */
@Component
class BatchMetadataSchemaInitializer(
    private val dataSource: DataSource,
) {
    private val log = LoggerFactory.getLogger(BatchMetadataSchemaInitializer::class.java)

    @PostConstruct
    fun initializeIfMissing() {
        val jdbcTemplate = JdbcTemplate(dataSource)
        val existingTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Long::class.java,
            BATCH_METADATA_MARKER_TABLE,
        )
        if (existingTableCount != null && existingTableCount > 0) {
            log.info("event=batch-schema-already-initialized table={}", BATCH_METADATA_MARKER_TABLE)
            return
        }
        ResourceDatabasePopulator(ClassPathResource(BATCH_SCHEMA_SCRIPT_PATH)).execute(dataSource)
        log.info("event=batch-schema-initialized script={}", BATCH_SCHEMA_SCRIPT_PATH)
    }

    companion object {
        private const val BATCH_METADATA_MARKER_TABLE = "BATCH_JOB_INSTANCE"
        private const val BATCH_SCHEMA_SCRIPT_PATH = "org/springframework/batch/core/schema-mysql.sql"
    }
}
