package org.utbot.engine.zestfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import org.utbot.engine.zestfuzzer.util.getAllDeclaredFields
import org.utbot.engine.zestfuzzer.util.isFinal
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.Field
import java.lang.reflect.Parameter
import java.lang.reflect.Type
import kotlin.system.exitProcess

val Generator<*>.isUserGenerator: Boolean
    get() = this is UserClassesGenerator

fun GeneratorRepository.addGenerator(forClass: Class<*>, parameterType: Type, depth: Int) {
    val generatorsField = this.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
    generatorsField.isAccessible = true
    val map = generatorsField.get(this) as java.util.HashMap<Class<*>, Set<Generator<*>>>
    map[forClass] = setOf(UserClassesGenerator().also { it.clazz = forClass; it.parameterType = parameterType; it.depth = depth })
}


fun GeneratorRepository.getOrProduceGenerator(field: Field, depth: Int = 0): Generator<*> =
    getOrProduceGenerator(ParameterTypeContext.forField(field), depth)

fun GeneratorRepository.getOrProduceGenerator(param: Parameter, depth: Int = 0): Generator<*> =
    getOrProduceGenerator(ParameterTypeContext.forParameter(param), depth)

fun GeneratorRepository.getOrProduceGenerator(parameterTypeContext: ParameterTypeContext, depth: Int = 0): Generator<*> {
    val allTypeParameters = parameterTypeContext.getAllTypeParameters()
    var generator: Generator<*>
    while (true) {
        try {
            generator = this.produceGenerator(parameterTypeContext)
            if (generator is UserClassesGenerator) {
                generator.depth = depth
            }
            break
        } catch (e: java.lang.IllegalArgumentException) {
            //ADD GENERATOR
            val className = e.localizedMessage.substringAfterLast("of type ")
            val classWithMissingGenerator = Class.forName(className.substringBefore('<'))
            val typeWithActualTypeParams = allTypeParameters.find { it.typeName == className } ?: parameterTypeContext.type()
            this.addGenerator(classWithMissingGenerator, typeWithActualTypeParams, depth)
        }
    }
    return generator
}

private fun ParameterTypeContext.getAllTypeParameters(): List<Type> {
    val res = mutableListOf<Type>()
    (this.type() as? ParameterizedTypeImpl)?.let { res.add(it) }
    val queue = ArrayDeque<Type>()
    (this.type() as? ParameterizedTypeImpl)?.actualTypeArguments?.forEach { queue.add(it) }
    while (queue.isNotEmpty()) {
        val el = queue.removeFirst()
        (el as? ParameterizedTypeImpl)?.actualTypeArguments?.forEach { queue.add(it) }
        res.add(el)
    }
    return res
}


//fun GeneratorRepository.getOrProduceGenerator(parameter: Parameter): Generator<out Any> =
//    try {
//        produceGenerator(ParameterTypeContext.forParameter(parameter))
//    } catch (e: java.lang.IllegalArgumentException) {
//        UserClassesGenerator()
//    }
//
//fun GeneratorRepository. getOrProduceGenerator(field: Field): Generator<out Any> =
//    try {
//        produceGenerator(ParameterTypeContext.forField(field))
//    } catch (e: java.lang.IllegalArgumentException) {
//        UserClassesGenerator()
//    }
//
//fun GeneratorRepository.getOrProduceGenerator(clazz: Class<*>): Generator<out Any> =
//    try {
//        produceGenerator(ParameterTypeContext.forClass(clazz))
//    } catch (e: java.lang.IllegalArgumentException) {
//        UserClassesGenerator()
//    }