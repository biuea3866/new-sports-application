package com.sportsapp.infrastructure.persistence.mongo

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import java.time.ZonedDateTime
import java.util.Date

@WritingConverter
class ZonedDateTimeToDateConverter : Converter<ZonedDateTime, Date> {

    override fun convert(source: ZonedDateTime): Date =
        Date.from(source.toInstant())
}
