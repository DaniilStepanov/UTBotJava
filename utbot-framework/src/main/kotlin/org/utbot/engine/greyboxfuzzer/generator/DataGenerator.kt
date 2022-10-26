package org.utbot.engine.greyboxfuzzer.generator

import org.utbot.quickcheck.generator.ComponentizedGenerator
import org.utbot.quickcheck.generator.GenerationStatus
import org.utbot.quickcheck.generator.Generator
import org.utbot.quickcheck.internal.ParameterTypeContext
import org.utbot.quickcheck.internal.generator.ZilchGenerator
import org.utbot.quickcheck.random.SourceOfRandomness
import kotlinx.coroutines.*
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.UtModel
import org.utbot.framework.plugin.api.UtNullModel
import ru.vyarus.java.generics.resolver.context.container.GenericArrayTypeImpl
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess
import kotlin.time.Duration

object DataGenerator {

    private val generatorRepository = DataGeneratorSettings.generatorRepository

    fun generate(
        parameterTypeContext: ParameterTypeContext,
        parameter: Parameter,
        parameterIndex: Int,
        utModelConstructor: UtModelConstructor,
        random: SourceOfRandomness,
        status: GenerationStatus
    ): FParameter {
        val generator = generatorRepository.getOrProduceGenerator(parameterTypeContext, parameterIndex)
        return generate(generator, parameter, utModelConstructor, random, status)
    }

    fun generate(
        clazz: Class<*>,
        random: SourceOfRandomness,
        status: GenerationStatus
    ) = generatorRepository.getOrProduceGenerator(clazz)?.generate(random, status)


    fun generate(
        parameter: Parameter,
        parameterIndex: Int,
        utModelConstructor: UtModelConstructor,
        random: SourceOfRandomness,
        status: GenerationStatus
    ): FParameter {
        val generator =
            generatorRepository.getOrProduceGenerator(parameter, parameterIndex)
                ?.also { GeneratorConfigurator.configureGenerator(it, 80) }
        return generate(generator, parameter, utModelConstructor, random, status)
    }

    private fun generate(
        generator: Generator<*>?,
        parameter: Parameter,
        utModelConstructor: UtModelConstructor,
        random: SourceOfRandomness,
        status: GenerationStatus,
    ): FParameter {
        generatorRepository.removeGenerator(Any::class.java)
        val classId = classIdForType(parameter.type)
        var generatedValue: Any?

        repeat(3) {
            println("TRY $it")
            try {
                generatedValue = generator?.generate(random, status)
                if (generatedValue != null) {
                    ZestUtils.setUnserializableFieldsToNull(generatedValue!!)
                    try {
                        println("GENERATED VALUE OF TYPE ${parameter.parameterizedType} = $generatedValue")
                    } catch (e: Exception) {
                        println("VALUE GENERATED!")
                    }
                    println("OK")

                    val constructedUtModel =
                        utModelConstructor.constructWithTimeoutOrNull(generatedValue, classId) ?: UtNullModel(classId)
                    println("UtModel = $constructedUtModel")
                    val fParam = FParameter(
                        parameter,
                        generatedValue,
                        constructedUtModel,
                        //utModelConstructor.construct(generatedValue, classIdForType(generatedValue!!.javaClass)),
                        generator,
                        emptyList()
                        //parameter.parameterizedType.getFFieldsForClass(generatedValue!!, 0, null)
                    )
                    println("fParam created")
                    return fParam
                }
            } catch (e: Throwable) {
                println("EXCEPTION WHILE VALUE GENERATION")
                return@repeat
            }
        }
        return FParameter(parameter, null, UtNullModel(classId), generator, classId, listOf())
    }

    //TODO Make it work with type parameters
    private fun Type.getFFieldsForClass(value: Any, depth: Int, originalField: Field?): List<FField> {
        println("GETTING FFIelds from $value")
        createFFieldFromPrimitivesOrBoxedPrimitives(this, value, originalField)?.let { return listOf(it) }
        val parameterizedType = this as? ParameterizedType
        val genericsContext =
            if (this is GenericArrayTypeImpl) {
                (this.genericComponentType as? ParameterizedType)?.buildGenericsContext()
            } else {
                parameterizedType?.buildGenericsContext()
            }
        if (depth >= DataGeneratorSettings.maxDepthOfGeneration) {
            return emptyList()
        }
        val subFields = mutableListOf<FField>()
        if (this.toClass()?.isArray == true) {
            val arrayContentType = this.toClass()?.componentType ?: return subFields
            getFFieldsFromArray(value, subFields, originalField, this, arrayContentType, depth)
            return subFields
        }
        val classFields =
            this.toClass()?.getAllDeclaredFields()?.filter { !it.hasModifiers(Modifier.FINAL) } ?: emptyList()
        for (field in classFields) {
            val resolvedFieldType =
                if (genericsContext != null) {
                    //TODO make it work for subclasses
                    parameterizedType.let { field.resolveFieldType(genericsContext) } ?: field.type
                } else {
                    field.type
                }
            assert(resolvedFieldType.toClass() != null)
//            if (field.hasModifiers(Modifier.FINAL)) {
//                //subFields.add(FField(field, value))
//                continue
//            }
            if (resolvedFieldType.toClass()!!.isArray) {
                val arrayOfObjects = field.getFieldValue(value)
                val arrayContentType =
                    (resolvedFieldType as? GenericArrayTypeImpl)?.genericComponentType ?: field.type.componentType
                getFFieldsFromArray(arrayOfObjects, subFields, field, resolvedFieldType, arrayContentType, depth)
                //TODO!!!!
            } else {
                field.getFieldValue(value)?.let { fieldValue ->
                    try {
                        val generatorForField = generatorRepository.getOrProduceGenerator(field)
                        if (field.type.isPrimitive) {
                            subFields.add(FField(field, fieldValue, resolvedFieldType, generatorForField))
                        } else {
                            //println("GETTING SUBFIELDS FOR ${field.type} value = ${fieldValue} DEPTH = $depth")
                            //TODO resolve type
                            val subFFields = resolvedFieldType.getFFieldsForClass(fieldValue, depth + 1, null)
                            subFields.add(FField(field, fieldValue, resolvedFieldType, generatorForField, subFFields))
                        }
                    } catch (e: java.lang.IllegalStateException) {
                        subFields.add(FField(field, fieldValue, resolvedFieldType, null, listOf()))
                    }
                } ?: subFields.add(FField(field, null, resolvedFieldType, null, listOf()))
            }
        }
        return subFields
    }

    private fun createFFieldFromPrimitivesOrBoxedPrimitives(originalType: Type, value: Any?, field: Field?): FField? {
        val clazz = originalType.toClass() ?: return null
        val listOfPrimitives = listOf(
            Byte::class,
            Short::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
            Boolean::class,
            Char::class,
            String::class
        )
        return if (clazz.kotlin in listOfPrimitives || clazz.isPrimitive) {
            FField(field, value, originalType, getGenerator(originalType))
        } else null
    }

    private fun getFFieldsFromArray(
        array: Any?,
        subFields: MutableList<FField>,
        field: Field?,
        arrayType: Type,
        arrayContentType: Type,
        depth: Int
    ) {
        val typedArray =
            when (array) {
                is BooleanArray -> {
                    array.toList()
                }
                is ByteArray -> {
                    array.toList()
                }
                is CharArray -> {
                    array.toList()
                }
                is ShortArray -> {
                    array.toList()
                }
                is IntArray -> {
                    array.toList()
                }
                is LongArray -> {
                    array.toList()
                }
                is FloatArray -> {
                    array.toList()
                }
                is DoubleArray -> {
                    array.toList()
                }
                else -> {
                    if (array == null) {
                        subFields.add(FField(null, null, arrayContentType, null, listOf()))
                        return
                    } else {
                        (array as Array<*>).toList()
                    }
                }
            }
        val generatorOfNeededType = field?.let { getGenerator(it, arrayType) } ?: getGenerator(arrayType)
        val localSubFields = mutableListOf<FField>()
        val indexOfLastNotNullElement = typedArray.indexOfLast { it != null }
        val arrayContentGenerator = getGenerator(arrayContentType)
        if (indexOfLastNotNullElement == -1) {
            localSubFields.add(FField(field, null, arrayContentType, arrayContentGenerator))
        } else {
            typedArray.filterNotNull().map { el ->
                val ssFFields = arrayContentType.getFFieldsForClass(el, depth + 1, null)
                localSubFields.add(FField(field, el, arrayContentType, arrayContentGenerator, ssFFields))
            }
        }
        subFields.add(FField(field, typedArray, arrayType, generatorOfNeededType, localSubFields))
    }

    private fun getGenerator(field: Field, fieldType: Type): Generator<*>? {
        return if (fieldType is ParameterizedType) {
            generatorRepository.getOrProduceGenerator(field.buildParameterContext(fieldType), 0)
        } else {
            generatorRepository.getOrProduceGenerator(field)
        }.let { gen ->
            if (gen is ComponentizedGenerator && gen.getComponents().any { it is ZilchGenerator }) null
            else gen
        }
    }

    private fun getGenerator(resolvedType: Type): Generator<*>? =
        generatorRepository.getOrProduceGenerator(resolvedType).let { gen ->
            if (gen is ComponentizedGenerator && gen.getComponents().any { it is ZilchGenerator }) null
            else gen
        }

}