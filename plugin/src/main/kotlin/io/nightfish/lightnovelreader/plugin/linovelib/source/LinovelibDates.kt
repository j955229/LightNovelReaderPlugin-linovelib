package io.nightfish.lightnovelreader.plugin.linovelib.source

import java.time.LocalDate
import java.time.LocalDateTime

internal object LinovelibDates {
    val unknownLocalDate: LocalDate = LocalDate.of(1, 1, 1)

    fun isUnknown(date: LocalDate): Boolean = date == unknownLocalDate

    fun unknownDateTime(): LocalDateTime = LocalDateTime.MIN
}
