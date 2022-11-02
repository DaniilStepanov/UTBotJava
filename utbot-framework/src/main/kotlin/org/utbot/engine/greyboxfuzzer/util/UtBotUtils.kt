package org.utbot.engine.greyboxfuzzer.util

import org.utbot.quickcheck.internal.ParameterTypeContext
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.executableId
import ru.vyarus.java.generics.resolver.GenericsResolver
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import ru.vyarus.java.generics.resolver.context.GenericsContext


fun UtAssembleModel.addModification(modification: UtStatementModel) =
    UtAssembleModel(
        this.id,
        this.classId,
        "${this.classId.name}#" + this.id?.toString(16),
        this.instantiationChain,
        this.modificationsChain + modification,
        this.origin,
        this.initialInstance
    )


fun UtModelConstructor.constructAssembleModelUsingMethodInvocation(
    clazz: Class<*>,
    methodExecutableId: ExecutableId,
    parameterValues: List<UtModel>
): UtAssembleModel {
    val genId = UtModelGenerator.utModelConstructor.computeUnusedIdAndUpdate()
    val instantiationChain = mutableListOf<UtStatementModel>()
    val resModel = UtAssembleModel(
        genId,
        classIdForType(clazz),
        "${clazz.name}#" + genId.toString(16),
        instantiationChain
    )
    val callModel = UtExecutableCallModel(
        null,
        methodExecutableId,
        parameterValues,
        resModel
    )
    instantiationChain.add(callModel)
    return resModel
}

//fun UtModelConstructor.constructModelFromValue(value: Any?, classId: ClassId) =
//    if (value == null) {
//        UtNullModel(classId)
//    } else {
//        try {
//            ZestUtils.setUnserializableFieldsToNull(value)
//            construct(value, classId)
//        } catch (e: Throwable) {
//            UtNullModel(classId)
//        }
//    }
//
//fun UtModelConstructor.constructModelFromValues(list: List<Pair<FParameter?, ClassId>>) =
//    list.map { (value, classId) ->
//        if (value?.value == null) {
//            UtNullModel(classId)
//        } else {
//            try {
//                ZestUtils.setUnserializableFieldsToNull(value.value)
//                construct(value.value, classId)
//            } catch (e: Throwable) {
//                UtNullModel(classId)
//            }
//        }
//    }

fun Parameter.resolveParameterTypeAndBuildParameterContext(
    parameterIndex: Int,
    method: Method
): ParameterTypeContext {
    val parameterTypeContext = this.createParameterTypeContext(0)
    val resolvedOriginalType = parameterTypeContext.generics.resolveType(parameterTypeContext.type())
    val genericContext = resolvedOriginalType.createGenericsContext(this.type.toClass()!!)
    val resolvedParameterType = genericContext.method(method).resolveParameterType(parameterIndex)
    val newGenericContext = resolvedParameterType.createGenericsContext(resolvedParameterType.toClass()!!)
    return createParameterContextForParameter(this, parameterIndex, newGenericContext, resolvedParameterType)
}