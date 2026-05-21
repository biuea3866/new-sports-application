package com.sportsapp.infrastructure.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.sportsapp.infrastructure.persistence.mongo.DateToZonedDateTimeConverter
import com.sportsapp.infrastructure.persistence.mongo.ZonedDateTimeToDateConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

@Configuration
@Profile("!test-jpa")
@EnableMongoAuditing(
    auditorAwareRef = "mongoAuditorAware",
    dateTimeProviderRef = "mongoAuditingDateTimeProvider",
)
class MongoConfig : AbstractMongoClientConfiguration() {

    @Value("\${spring.data.mongodb.uri}")
    private lateinit var mongoUri: String

    override fun getDatabaseName(): String =
        ConnectionString(mongoUri).database ?: "sports"

    override fun configureClientSettings(builder: MongoClientSettings.Builder) {
        builder.applyConnectionString(ConnectionString(mongoUri))
    }

    override fun getMappingBasePackages(): Collection<String> = domainMappingPackages()

    fun domainMappingPackages(): Set<String> = setOf("com.sportsapp.domain")

    @Bean
    override fun customConversions(): MongoCustomConversions =
        MongoCustomConversions(
            listOf(
                ZonedDateTimeToDateConverter(),
                DateToZonedDateTimeConverter(),
            )
        )

    override fun autoIndexCreation(): Boolean = true
}
