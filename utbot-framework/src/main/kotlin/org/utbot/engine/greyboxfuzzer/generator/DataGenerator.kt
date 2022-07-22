package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.ZilchGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.utbot.engine.greyboxfuzzer.util.getAllDeclaredFields
import org.utbot.engine.greyboxfuzzer.util.getFieldValue
import org.utbot.engine.greyboxfuzzer.util.hasModifiers
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import kotlin.system.exitProcess

object DataGenerator {

    private val generatorRepository = DataGeneratorSettings.generatorRepository

    fun generate(parameter: Parameter, random: SourceOfRandomness, status: GenerationStatus): FParameter? {
        //TODO INPUT RANDOM TYPES INSTEAD OF TYPE PARAMETERS
        val generator = generatorRepository.getOrProduceGenerator(parameter)
        var generatedValue: Any? = null
        repeat(3) {
            generatedValue = generator?.generate(random, status) ?: return@repeat
            println("GENERATED VALUE OF TYPE ${parameter.parameterizedType} = $generatedValue")
            if (generatedValue != null) {
                return FParameter(
                    parameter,
                    generatedValue!!,
                    generator,
                    //emptyList()
                    parameter.type.getFFieldsForClass(generatedValue!!, 0)
                )
            }
            //generator.generate(random, status)?.let { generatedValue = it; return@repeat }
        }
        throw IllegalStateException("Cant generate value of type ${parameter.type}")
    }


    //TODO Make it work with type parameters
    private fun Class<*>.getFFieldsForClass(value: Any, depth: Int): List<FField> {
        if (depth >= DataGeneratorSettings.maxDepthOfGeneration) {
            return emptyList()
        }
        val subFields = mutableListOf<FField>()
        for (field in this.getAllDeclaredFields()) {
            if (field.hasModifiers(Modifier.FINAL)) {
                //subFields.add(FField(field, value))
                continue
            }
            if (field.type.isArray) {
                val arrayOfObjects = field.getFieldValue(value)
                val fieldClass = field.type.componentType
                val typedArray =
                    when (arrayOfObjects) {
                        is BooleanArray -> {
                            arrayOfObjects.toList()
                        }
                        is ByteArray -> {
                            arrayOfObjects.toList()
                        }
                        is CharArray -> {
                            arrayOfObjects.toList()
                        }
                        is ShortArray -> {
                            arrayOfObjects.toList()
                        }
                        is IntArray -> {
                            arrayOfObjects.toList()
                        }
                        is LongArray -> {
                            arrayOfObjects.toList()
                        }
                        is FloatArray -> {
                            arrayOfObjects.toList()
                        }
                        is DoubleArray -> {
                            arrayOfObjects.toList()
                        }
                        else -> {
                            if (arrayOfObjects == null) {
                                subFields.add(FField(field, null, null, listOf()))
                                continue
                            } else {
                                (arrayOfObjects as Array<*>).toList()
                            }
                        }
                    }
                val generatorOfNeededType =
                    generatorRepository.getOrProduceGenerator(ParameterTypeContext.forClass(fieldClass))
                        .let { gen ->
                            if (gen is ComponentizedGenerator && gen.getComponents().any { it is ZilchGenerator }) null
                            else gen
                        }
                subFields.add(FField(field, typedArray, generatorOfNeededType))
//                for (i in typedArray.indices) {
//                    typedArray[i]?.let { fieldValue ->
//                        val subFFields = fieldClass?.getFFieldsForClass(fieldValue, depth + 1) ?: listOf()
//                        subFields.add(FField(field, fieldValue, generatorOfNeededType, subFFields))
//                        //subFields.add(FField(field, fieldValue, generatorOfNeededType))
//                    } ?: subFields.add(FField(field, null, generatorOfNeededType))
//                }
            } else {
                field.getFieldValue(value)?.let { fieldValue ->
                    try {
                        val generatorForField = generatorRepository.getOrProduceGenerator(field)
                        if (field.type.isPrimitive) {
                            subFields.add(FField(field, fieldValue, generatorForField))
                        } else {
                            //println("GETTING SUBFIELDS FOR ${field.type} value = ${fieldValue} DEPTH = $depth")
                            val subFFields = field.type.getFFieldsForClass(fieldValue, depth + 1)
                            subFields.add(FField(field, fieldValue, generatorForField, subFFields))
                        }
                    } catch (e: java.lang.IllegalStateException) {
                        subFields.add(FField(field, fieldValue, null, listOf()))
                    }
                } ?: subFields.add(FField(field, null, null, listOf()))
            }
        }
        return subFields
    }

}