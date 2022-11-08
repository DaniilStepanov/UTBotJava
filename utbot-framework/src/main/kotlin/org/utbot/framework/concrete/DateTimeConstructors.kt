package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.intClassId
import org.utbot.framework.plugin.api.util.longClassId
import org.utbot.framework.plugin.api.util.methodId
import org.utbot.framework.plugin.api.util.stringClassId

internal class InstantConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.Instant
        val seconds = valueToConstructFrom.epochSecond
        val nanos = valueToConstructFrom.nano.toLong()

        val secondsModel = internalConstructor.construct(seconds, longClassId)
        val nanosModel = internalConstructor.construct(nanos, longClassId)

        val classId = valueToConstructFrom::class.java.id
        instantiationChain += UtExecutableCallModel(
            instance = null,
            methodId(classId, "ofEpochSecond", classId, longClassId, longClassId),
            listOf(secondsModel, nanosModel),
            this
        )
    }
}

internal class DurationConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.Duration
        val seconds = valueToConstructFrom.seconds
        val nanos = valueToConstructFrom.nano.toLong()

        val secondsModel = internalConstructor.construct(seconds, longClassId)
        val nanosModel = internalConstructor.construct(nanos, longClassId)

        val classId = valueToConstructFrom::class.java.id
        instantiationChain += UtExecutableCallModel(
            instance = null,
            methodId(classId, "ofSeconds", classId, longClassId, longClassId),
            listOf(secondsModel, nanosModel),
            this
        )
    }
}

internal class LocalDateConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.LocalDate
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId, intClassId, intClassId),
                listOf(
                    construct(valueToConstructFrom.year, intClassId),
                    construct(valueToConstructFrom.monthValue, intClassId),
                    construct(valueToConstructFrom.dayOfMonth, intClassId)
                ),
                this@modifyChains
            )
        }
    }
}

internal class LocalTimeConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.LocalTime
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId, intClassId, intClassId, intClassId),
                listOf(
                    construct(valueToConstructFrom.hour, intClassId),
                    construct(valueToConstructFrom.minute, intClassId),
                    construct(valueToConstructFrom.second, intClassId),
                    construct(valueToConstructFrom.nano, intClassId)
                ),
                this@modifyChains
            )
        }
    }
}

internal class LocalDateTimeConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.LocalDateTime
        val timeClassId = java.time.LocalTime::class.java.id
        val dateClassId = java.time.LocalTime::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, dateClassId, timeClassId),
                listOf(
                    construct(valueToConstructFrom.toLocalDate(), dateClassId),
                    construct(valueToConstructFrom.toLocalTime(), timeClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class ZoneIdConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.ZoneId
        val id = valueToConstructFrom.id

        val idModel = internalConstructor.construct(id, stringClassId)

        val classId = valueToConstructFrom::class.java.id
        instantiationChain += UtExecutableCallModel(
            instance = null,
            methodId(classId, "of", classId, stringClassId),
            listOf(idModel),
            this
        )
    }
}

internal class MonthDayConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.MonthDay
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId, intClassId),
                listOf(
                    construct(valueToConstructFrom.monthValue, intClassId),
                    construct(valueToConstructFrom.dayOfMonth, intClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class YearConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.Year
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId),
                listOf(
                    construct(valueToConstructFrom.value, intClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class YearMonthConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.YearMonth
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId, intClassId),
                listOf(
                    construct(valueToConstructFrom.year, intClassId),
                    construct(valueToConstructFrom.monthValue, intClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class PeriodConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.Period
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, intClassId, intClassId, intClassId),
                listOf(
                    construct(valueToConstructFrom.years, intClassId),
                    construct(valueToConstructFrom.months, intClassId),
                    construct(valueToConstructFrom.days, intClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class ZoneOffsetConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.ZoneOffset
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "ofTotalSeconds", classId, intClassId),
                listOf(
                    construct(valueToConstructFrom.totalSeconds, intClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class OffsetTimeConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.OffsetTime
        val timeClassId = java.time.LocalTime::class.java.id
        val offsetClassId = java.time.ZoneOffset::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, timeClassId, offsetClassId),
                listOf(
                    construct(valueToConstructFrom.toLocalTime(), timeClassId),
                    construct(valueToConstructFrom.offset, offsetClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class OffsetDateTimeConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.OffsetDateTime
        val dateTimeClassId = java.time.LocalDateTime::class.java.id
        val offsetClassId = java.time.ZoneOffset::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "of", classId, dateTimeClassId, offsetClassId),
                listOf(
                    construct(valueToConstructFrom.toLocalDateTime(), dateTimeClassId),
                    construct(valueToConstructFrom.offset, offsetClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class ZonedDateTimeConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.time.ZonedDateTime
        val dateTimeClassId = java.time.LocalDateTime::class.java.id
        val offsetClassId = java.time.ZoneOffset::class.java.id
        val zoneClassId = java.time.ZoneId::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "ofLenient", classId, dateTimeClassId, offsetClassId, zoneClassId),
                listOf(
                    construct(valueToConstructFrom.toLocalDateTime(), dateTimeClassId),
                    construct(valueToConstructFrom.offset, offsetClassId),
                    construct(valueToConstructFrom.zone, zoneClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class DateConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.util.Date
        val instantClassId = java.time.Instant::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "from", classId, instantClassId),
                listOf(
                    construct(valueToConstructFrom.toInstant(), instantClassId),
                ),
                this@modifyChains
            )
        }
    }
}

internal class TimeZoneConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.util.TimeZone
        val zoneClassId = java.time.ZoneId::class.java.id
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "getTimeZone", classId, zoneClassId),
                listOf(
                    construct(valueToConstructFrom.toZoneId(), zoneClassId),
                ),
                this@modifyChains
            )
        }
    }
}
