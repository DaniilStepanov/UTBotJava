package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import org.utbot.external.api.classIdForType
import org.utbot.framework.plugin.api.ClassId
import org.utbot.framework.plugin.api.UtModel
import java.lang.reflect.Parameter

data class FParameter(
    val parameter: Parameter,
    val value: Any?,
    val utModel: UtModel,
    val generator: Generator<*>?,
    val classId: ClassId,
    val fields: List<FField>
) {

    constructor(
        parameter: Parameter,
        value: Any?,
        utModel: UtModel,
        generator: Generator<*>?
    ) : this(parameter, value, utModel, generator, classIdForType(parameter.type), emptyList())

    constructor(
        parameter: Parameter,
        value: Any?,
        utModel: UtModel,
        generator: Generator<*>?,
        fields: List<FField>
    ) : this(parameter, value, utModel, generator, classIdForType(parameter.type), fields)

    fun getAllSubFields(): List<FField> {
        val res = mutableListOf<FField>()
        val queue = ArrayDeque<FField>()
        queue.addAll(fields)
        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()
            queue.addAll(element.subFields)
            res.add(element)
        }
        return res
    }

}