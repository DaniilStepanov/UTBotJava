package org.utbot.engine.greyboxfuzzer.util

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.ClassId
import org.utbot.framework.plugin.api.UtNullModel
import ru.vyarus.java.generics.resolver.GenericsResolver
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import ru.vyarus.java.generics.resolver.context.GenericsContext


fun UtModelConstructor.constructModelFromValue(value: Any?, classId: ClassId) =
    if (value == null) {
        UtNullModel(classId)
    } else {
        try {
            ZestUtils.setUnserializableFieldsToNull(value)
            construct(value, classId)
        } catch (e: Throwable) {
            UtNullModel(classId)
        }
    }

fun UtModelConstructor.constructModelFromValues(list: List<Pair<FParameter?, ClassId>>) =
    list.map { (value, classId) ->
        if (value?.value == null) {
            UtNullModel(classId)
        } else {
            try {
                ZestUtils.setUnserializableFieldsToNull(value.value)
                construct(value.value, classId)
            } catch (e: Throwable) {
                UtNullModel(classId)
            }
        }
    }
fun Parameter.resolveParameterTypeAndBuildParameterContext(
    parameterIndex: Int,
    method: Method
): ParameterTypeContext {
    val parameterTypeContext = this.createParameterTypeContext(0)
    val resolvedOriginalType = parameterTypeContext.getGenericContext().resolveType(parameterTypeContext.type())
    val genericContext = createGenericsContext(resolvedOriginalType, this.type.toClass()!!)
    val resolvedParameterType = genericContext.method(method).resolveParameterType(parameterIndex)
    val newGenericContext = createGenericsContext(resolvedParameterType, resolvedParameterType.toClass()!!)
    return createParameterContextForParameter(this, parameterIndex, newGenericContext, resolvedParameterType)
}