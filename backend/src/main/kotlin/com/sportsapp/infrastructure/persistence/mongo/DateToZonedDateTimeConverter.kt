package com.sportsapp.infrastructure.persistence.mongo

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date

@ReadingConverter
class DateToZonedDateTimeConverter : Converter<Date, ZonedDateTime> {

    override fun convert(source: Date): ZonedDateTime =
        ZonedDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC)
}
