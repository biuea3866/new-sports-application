package com.sportsapp.infrastructure.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.sql.Timestamp
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Converter(autoApply = true)
class ZonedDateTimeAttributeConverter : AttributeConverter<ZonedDateTime, Timestamp> {

    override fun convertToDatabaseColumn(attribute: ZonedDateTime?): Timestamp? =
        attribute?.let { Timestamp.from(it.toInstant()) }

    override fun convertToEntityAttribute(dbData: Timestamp?): ZonedDateTime? =
        dbData?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneOffset.UTC) }
}
