package org.utbot.engine.zestfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.ArrayGenerator
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.ZilchGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.utbot.engine.zestfuzzer.util.getAllDeclaredFields
import org.utbot.engine.zestfuzzer.util.getArrayValues
import org.utbot.engine.zestfuzzer.util.getFieldValue
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.Field
import java.lang.reflect.Parameter
import kotlin.reflect.KFunction
import kotlin.system.exitProcess
import kotlin.reflect.KFunction2
import java.lang.reflect.Array as JArray

class DataGenerator(private val generatorRepository: GeneratorRepository) {


    fun generate(parameter: Parameter, random: SourceOfRandomness, status: GenerationStatus): FParameter {
        //TODO
        val generatorsField = generatorRepository.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
        generatorsField.isAccessible = true
        val map = generatorsField.get(generatorRepository) as java.util.HashMap<Class<*>, Set<Generator<*>>>
        map.remove(java.util.Locale::class.java)



        val generator = generatorRepository.getOrProduceGenerator(parameter)
        val generatedValue = generator.generate(random, status)
        generatedValue.toString()
        //println("GENERATED VALUE = $generatedValue")
        exitProcess(0)
        return FParameter(parameter, generatedValue, generator, parameter.type.getFFieldsForClass(generatedValue))
    }


    private fun Class<*>.getFFieldsForClass(value: Any): List<FField> {
        val subFields = mutableListOf<FField>()
        for (field in this.getAllDeclaredFields()) {
            if (field.type.isArray) {
                val arrayOfObjects = (field.getFieldValue(value) as Array<*>).filterNotNull()
                val typeOfArrayObjects =
                    if (arrayOfObjects.isEmpty()) Object::class.java else arrayOfObjects.first().javaClass
                val generatorOfNeededType =
                    generatorRepository.getOrProduceGenerator(ParameterTypeContext.forClass(typeOfArrayObjects))
                        .let { gen ->
                            if (gen is ComponentizedGenerator && gen.getComponents().any { it is ZilchGenerator }) null
                            else gen
                        }
                //generatorOfNeededType.generate(DataGeneratorSettings.sourceOfRandomness, NonTrackingGenerationStatus(DataGeneratorSettings.sourceOfRandomness))
                val funcToGetValueFromArray: KFunction2<Any, Int, Any>
                val fieldClass: Class<*>
                val typedArray =
                    when (typeOfArrayObjects) {
                        Boolean::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getBoolean
                            fieldClass = Boolean::class.java
                            arrayOfObjects.map { it as Boolean }.toBooleanArray()
                        }
                        Byte::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getByte
                            fieldClass = Byte::class.java
                            arrayOfObjects.map { it as Byte }.toByteArray()
                        }
                        Char::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getChar
                            fieldClass = Char::class.java
                            arrayOfObjects.map { it as Char }.toCharArray()
                        }
                        Short::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getShort
                            fieldClass = Short::class.java
                            arrayOfObjects.map { it as Short }.toShortArray()
                        }
                        Int::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getInt
                            fieldClass = Int::class.java
                            arrayOfObjects.map { it as Int }.toIntArray()
                        }
                        Long::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getLong
                            fieldClass = Long::class.java
                            arrayOfObjects.map { it as Long }.toLongArray()
                        }
                        Float::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getFloat
                            fieldClass = Float::class.java
                            arrayOfObjects.map { it as Float }.toFloatArray()
                        }
                        Double::class.javaObjectType -> {
                            funcToGetValueFromArray = JArray::getDouble
                            fieldClass = Double::class.java
                            arrayOfObjects.map { it as Double }.toDoubleArray()
                        }
                        else -> {
                            funcToGetValueFromArray = JArray::get
                            fieldClass = typeOfArrayObjects
                            arrayOfObjects.toTypedArray()
                        }
                    }
                for (i in arrayOfObjects.indices) {
                    val fieldValue = funcToGetValueFromArray.invoke(typedArray, i)
                    val subFFields = fieldClass.getFFieldsForClass(fieldValue)
                    subFields.add(FField(field, fieldValue, generatorOfNeededType, subFFields))
                }
            } else {
                val fieldValue = field.getFieldValue(value)
                val generatorForField = generatorRepository.getOrProduceGenerator(field)
                if (field.type.isPrimitive) {
                    subFields.add(FField(field, fieldValue, generatorForField))
                } else {
                    val subFFields = field.type.getFFieldsForClass(fieldValue)
                    subFields.add(FField(field, fieldValue, generatorForField, subFFields))
                }
            }
        }
        return subFields
    }


    private fun ComponentizedGenerator<*>.getComponents(): List<Generator<*>> {
        val components = this.javaClass.getAllDeclaredFields().find { it.name == "components" } ?: return listOf()
        return components.let {
            it.isAccessible = true
            it.get(this) as List<Generator<*>>
        }.also { components.isAccessible = false }
    }

}