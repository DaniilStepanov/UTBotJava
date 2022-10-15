package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.ZilchGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.UtNullModel
import ru.vyarus.java.generics.resolver.context.container.GenericArrayTypeImpl
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.system.exitProcess

object DataGenerator {

    val generatorRepository = DataGeneratorSettings.generatorRepository

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
        parameter: Parameter,
        parameterIndex: Int,
        utModelConstructor: UtModelConstructor,
        random: SourceOfRandomness,
        status: GenerationStatus
    ): FParameter {
        //TODO INPUT RANDOM TYPES INSTEAD OF TYPE PARAMETERS
        val generator =
            generatorRepository.getOrProduceGenerator(parameter, parameterIndex)
                ?.also { GeneratorConfigurator.configureGenerator(it, 80) }
//        val t = parameter.type.toClass()!!
//        val pt = parameter.parameterizedType as ParameterizedTypeImpl
//        t.typeParameters
//        val ptx = ParameterTypeContext.forParameter(parameter)
//        val ptx1 = ptx.getAllParameterTypeContexts()[1]
//        ptx.getResolvedType()
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
        repeat(3) {
            println("TRY $it")
//            try {
            val genVal = generator?.generate(random, status)
            try {
                println("GENERATED VALUE OF TYPE ${parameter.parameterizedType} = $genVal")
            } catch (e: Exception) {
                println("VALUE GENERATED!")
            }
            if (genVal != null) {
                return FParameter(
                    parameter,
                    genVal,
                    utModelConstructor.construct(genVal, classIdForType(genVal.javaClass)),
                    generator,
                    //emptyList()
                    parameter.parameterizedType.getFFieldsForClass(genVal, 0, null)
                )
            }
//            } catch (e: Throwable) {
//                println("EXCEPTION WHILE GENERATION :( $e")
//                return@repeat
//            }
        }
        return FParameter(parameter, null, UtNullModel(classId), generator, classId, listOf())
    }

    //TODO Make it work with type parameters
    private fun Type.getFFieldsForClass(value: Any, depth: Int, originalField: Field?): List<FField> {
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