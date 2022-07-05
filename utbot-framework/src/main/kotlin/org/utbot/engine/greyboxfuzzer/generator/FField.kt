package org.utbot.engine.zestfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import org.utbot.external.api.classIdForType
import org.utbot.framework.plugin.api.ClassId
import java.lang.reflect.Field

data class FField(
    val field: Field,
    val value: Any?,
    val generator: Generator<*>?,
    val classId: ClassId,
    val subFields: List<FField>,
    var isBlocked: Boolean
) {
    constructor(
        field: Field,
        value: Any?,
        generator: Generator<*>?,
        subFields: List<FField>,
        isBlocked: Boolean
    ) : this(field, value, generator, classIdForType(field.type), subFields, isBlocked)

    constructor(
        field: Field,
        value: Any?,
        generator: Generator<*>?,
        subFields: List<FField>,
    ) : this(field, value, generator, classIdForType(field.type), subFields, false)

    constructor(
        field: Field,
        value: Any?,
        generator: Generator<*>?,
    ) : this(field, value, generator, classIdForType(field.type), listOf(), false)

    constructor(
        field: Field,
        value: Any?
    ) : this(field, value, null, classIdForType(field.type), listOf(), false)

}