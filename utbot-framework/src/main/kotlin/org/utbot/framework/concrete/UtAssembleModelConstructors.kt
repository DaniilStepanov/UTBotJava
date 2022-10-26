package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.ClassId
import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.jClass
import org.utbot.framework.plugin.api.util.primitiveWrappers
import org.utbot.framework.plugin.api.util.voidWrapperClassId
import org.utbot.framework.util.nextModelName
import java.util.concurrent.CopyOnWriteArrayList

private val predefinedConstructors = mutableMapOf<Class<*>, () -> UtAssembleModelConstructorBase>(
    /**
     * Optionals
     */
    java.util.OptionalInt::class.java to { OptionalIntConstructor() },
    java.util.OptionalLong::class.java to { OptionalLongConstructor() },
    java.util.OptionalDouble::class.java to { OptionalDoubleConstructor() },
    java.util.Optional::class.java to { OptionalConstructor() },

    /**
     * Lists
     */
    java.util.LinkedList::class.java to { CollectionConstructor() },
    java.util.ArrayList::class.java to { CollectionConstructor() },
    java.util.AbstractList::class.java to { CollectionConstructor() },
    java.util.List::class.java to { CollectionConstructor() },
    java.util.concurrent.CopyOnWriteArrayList::class.java to { CollectionConstructor() },


    /**
     * Queues, deques
     */
    java.util.PriorityQueue::class.java to { CollectionConstructor() },
    java.util.ArrayDeque::class.java to { CollectionConstructor() },
    java.util.concurrent.LinkedBlockingQueue::class.java to { CollectionConstructor() },
    java.util.concurrent.LinkedBlockingDeque::class.java to { CollectionConstructor() },
    java.util.concurrent.ConcurrentLinkedQueue::class.java to { CollectionConstructor() },
    java.util.concurrent.ConcurrentLinkedDeque::class.java to { CollectionConstructor() },
    java.util.Queue::class.java to { CollectionConstructor() },
    java.util.Deque::class.java to { CollectionConstructor() },


    /**
     * Sets
     */
    java.util.HashSet::class.java to { CollectionConstructor() },
    java.util.TreeSet::class.java to { CollectionConstructor() },
    java.util.LinkedHashSet::class.java to { CollectionConstructor() },
    java.util.AbstractSet::class.java to { CollectionConstructor() },
    java.util.Set::class.java to { CollectionConstructor() },



    /**
     * Maps
     */
    java.util.HashMap::class.java to { MapConstructor() },
    java.util.TreeMap::class.java to { MapConstructor() },
    java.util.LinkedHashMap::class.java to { MapConstructor() },
    java.util.AbstractMap::class.java to { MapConstructor() },
    java.util.concurrent.ConcurrentMap::class.java to { MapConstructor() },
    java.util.concurrent.ConcurrentHashMap::class.java to { MapConstructor() },
    java.util.IdentityHashMap::class.java to { MapConstructor() },
    java.util.WeakHashMap::class.java to { MapConstructor() },

    /**
     * Hashtables
     */
    java.util.Hashtable::class.java to { MapConstructor() },


    /**
     * String wrapper
     */
    java.lang.String::class.java.let { it to { PrimitiveWrapperConstructor() } },

    /**
     * TODO: JIRA:1405 -- Add assemble constructors for another standard classes as well.
     */
    java.math.BigInteger::class.java.let { it to { BigNumberConstructor() } },
    java.math.BigDecimal::class.java.let { it to { BigNumberConstructor() } },

    java.util.BitSet::class.java.let { it to { BitSetConstructor() } },
    java.util.UUID::class.java.let { it to { UUIDConstructor() } },
    java.util.Locale::class.java.let { it to { LocaleConstructor() } },
    java.util.Date::class.java.let { it to { DateConstructor() } },
    java.util.TimeZone::class.java.let { it to { TimeZoneConstructor() } },

    java.time.Instant::class.java.let { it to { InstantConstructor() } },
    java.time.Duration::class.java.let { it to { DurationConstructor() } },
    java.time.ZoneId::class.java.let { it to { ZoneIdConstructor() } },
    java.time.LocalDate::class.java.let { it to { LocalDateConstructor() } },
    java.time.LocalTime::class.java.let { it to { LocalTimeConstructor() } },
    java.time.LocalDateTime::class.java.let { it to { LocalDateTimeConstructor() } },
    java.time.MonthDay::class.java.let { it to { MonthDayConstructor() } },
    java.time.Year::class.java.let { it to { YearConstructor() } },
    java.time.YearMonth::class.java.let { it to { YearMonthConstructor() } },
    java.time.Period::class.java.let { it to { PeriodConstructor() } },
    java.time.ZoneOffset::class.java.let { it to { ZoneOffsetConstructor() } },
    java.time.OffsetTime::class.java.let { it to { OffsetTimeConstructor() } },
    java.time.OffsetDateTime::class.java.let { it to { OffsetDateTimeConstructor() } },
    java.time.ZonedDateTime::class.java.let { it to { ZonedDateTimeConstructor() } },


).apply {
    /**
     * Primitive wrappers
     */
    this += primitiveWrappers
        .filter { it != voidWrapperClassId }
        .associate { it.jClass to { PrimitiveWrapperConstructor() } }
}

internal fun findUtAssembleModelConstructor(classId: ClassId): UtAssembleModelConstructorBase? =
    predefinedConstructors[classId.jClass]?.invoke()

internal abstract class UtAssembleModelConstructorBase {
    fun constructAssembleModel(
        internalConstructor: UtModelConstructorInterface,
        valueToConstructFrom: Any,
        valueClassId: ClassId,
        id: Int?,
        init: (UtAssembleModel) -> Unit
    ): UtAssembleModel {
        val instantiationChain = mutableListOf<UtStatementModel>()
        val modificationChain = mutableListOf<UtStatementModel>()
        val baseName = valueClassId.simpleName.decapitalize()
        return UtAssembleModel(id, valueClassId, nextModelName(baseName), instantiationChain, modificationChain)
            .also(init)
            .apply { modifyChains(internalConstructor, instantiationChain, modificationChain, valueToConstructFrom) }
    }

    protected abstract fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    )
}

internal fun UtAssembleModelConstructorBase.checkClassCast(expected: Class<*>, actual: Class<*>) {
    require(expected.isAssignableFrom(actual)) {
        "Can't cast $actual to $expected in $this assemble constructor."
    }
}