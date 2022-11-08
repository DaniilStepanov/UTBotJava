package org.utbot.engine.greyboxfuzzer.generator.userclasses.generator

import org.utbot.engine.greyboxfuzzer.util.SootStaticsCollector
import org.utbot.engine.greyboxfuzzer.util.UtModelGenerator
import org.utbot.engine.greyboxfuzzer.util.hasModifiers
import org.utbot.engine.greyboxfuzzer.util.toClass
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.*
import org.utbot.quickcheck.generator.GenerationStatus
import org.utbot.quickcheck.random.SourceOfRandomness
import ru.vyarus.java.generics.resolver.context.GenericsContext
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

internal class StaticsFieldBasedInstanceGenerator(
    private val clazz: Class<*>,
    private val gctx: GenericsContext
) : InstanceGenerator {
    override fun generate(): UtModel? =
        getRandomStaticToProduceInstanceUsingSoot()?.let { fieldToProvideInstance ->
            createUtModelForStaticFieldInvocation(UtModelGenerator.utModelConstructor, fieldToProvideInstance)
        }

    //In case of no Soot
    private fun getStaticFieldToProduceInstance(): Field? {
        val resolvedStaticFields =
            try {
                clazz.declaredFields.filter { it.hasModifiers(Modifier.STATIC) }
                    .map { it to gctx.resolveFieldType(it) }
                    .filter { it.first.type.toClass() == clazz }
            } catch (e: Error) {
                listOf()
            }
        return resolvedStaticFields.randomOrNull()?.first
    }

    private fun getRandomStaticToProduceInstanceUsingSoot(): Field? =
        SootStaticsCollector.getStaticFieldsInitializersOf(clazz).randomOrNull()

    private fun createUtModelForStaticFieldInvocation(
        utModelConstructor: UtModelConstructor,
        field: Field
    ): UtAssembleModel {
        with(utModelConstructor) {
            val generatedModelId = computeUnusedIdAndUpdate()
            val instantiationChain = mutableListOf<UtStatementModel>()
            val generatedModel = UtAssembleModel(
                id = generatedModelId,
                classId = classIdForType(field.type),
                modelName = "xxx_$generatedModelId",
                instantiationChain = instantiationChain
            )
            val fieldModelId = computeUnusedIdAndUpdate()
            val fieldModel = UtCompositeModel(fieldModelId, Field::class.java.id, isMock = false)
            val classModelId = computeUnusedIdAndUpdate()
            val classModel = UtCompositeModel(classModelId, Class::class.java.id, isMock = false)
            val classInstanceModel = construct(clazz, classClassId) as UtReferenceModel
            instantiationChain += UtExecutableCallModel(
                instance = null,
                executable = methodId(Objects::class.java.id, "requireNonNull", objectClassId, objectClassId),
                params = listOf(classInstanceModel),
                returnValue = classModel
            )
            instantiationChain += UtExecutableCallModel(
                classModel,
                Class<*>::getField.executableId,
                listOf(construct(field.name, stringClassId)),
                returnValue = fieldModel
            )
            instantiationChain += UtExecutableCallModel(
                fieldModel,
                Field::get.executableId,
                listOf(UtNullModel(clazz.id)),
                returnValue = generatedModel
            )
            return generatedModel
        }
    }

}