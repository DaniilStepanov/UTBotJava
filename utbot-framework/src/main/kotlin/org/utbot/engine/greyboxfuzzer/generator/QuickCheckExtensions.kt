package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.LambdaGenerator
import org.utbot.engine.greyboxfuzzer.util.getAllDeclaredFields
import org.utbot.engine.greyboxfuzzer.util.hasAtLeastOneOfModifiers
import org.utbot.engine.greyboxfuzzer.util.hasModifiers
import org.utbot.engine.greyboxfuzzer.util.toClass
import ru.vyarus.java.generics.resolver.context.GenericsContext
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.reflect.Type

val Generator<*>.isUserGenerator: Boolean
    get() = this is UserClassesGenerator

fun GeneratorRepository.addGenerator(
    forClass: Class<*>,
    parameterType: Type,
    parameterTypeContext: ParameterTypeContext,
    depth: Int
) {
    val generatorsField = this.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
    generatorsField.isAccessible = true
    val map = generatorsField.get(this) as java.util.HashMap<Class<*>, Set<Generator<*>>>
    map[forClass] = setOf(UserClassesGenerator().also {
        it.clazz = forClass
        it.parameterType = parameterType
        it.parameterTypeContext = parameterTypeContext
        it.depth = depth
    })
}


fun GeneratorRepository.getOrProduceGenerator(field: Field, depth: Int = 0): Generator<*> =
    getOrProduceGenerator(ParameterTypeContext.forField(field), depth)

fun GeneratorRepository.getOrProduceGenerator(param: Parameter, depth: Int = 0): Generator<*> =
    getOrProduceGenerator(ParameterTypeContext.forParameter(param), depth)

fun GeneratorRepository.getOrProduceGenerator(
    parameterTypeContext: ParameterTypeContext,
    depth: Int = 0
): Generator<*> {
    val allTypeParameters = parameterTypeContext.getAllTypeParameters()
    val clazz = parameterTypeContext.type().toClass()
    var generator: Generator<*>
    while (true) {
        try {
            println("TRYING TO GET GENERATOR FOR ${parameterTypeContext.type()}")
            generator = this.produceGenerator(parameterTypeContext)
            if (generator is UserClassesGenerator) {
                generator.depth = depth
            } else if (generator is LambdaGenerator<*, *> && clazz.hasAtLeastOneOfModifiers(Modifier.INTERFACE, Modifier.ABSTRACT)) {
                throw IllegalStateException("")
            }
            break
        } catch (e: java.lang.IllegalArgumentException) {
            //ADD GENERATOR
            val className = e.localizedMessage.substringAfterLast("of type ")
            val classWithMissingGenerator = Class.forName(className.substringBefore('<'))
            val typeWithActualTypeParams =
                allTypeParameters.find { it.typeName == className } ?: parameterTypeContext.type()
            this.addGenerator(
                classWithMissingGenerator,
                typeWithActualTypeParams,
                parameterTypeContext,
                depth
            )
        } catch (e: java.lang.IllegalStateException) {
            val classWithMissingGenerator = parameterTypeContext.type().toClass()
            val typeWithActualTypeParams =
                allTypeParameters.find { it.typeName == classWithMissingGenerator.name } ?: parameterTypeContext.type()
            this.addGenerator(
                classWithMissingGenerator,
                typeWithActualTypeParams,
                parameterTypeContext,
                depth
            )
        }
    }
    return generator
}

fun org.javaruntype.type.Type<*>.canBeReplacedBy(other: org.javaruntype.type.Type<*>): Boolean {
    if (this.componentClass != other.componentClass) return false
    return this.typeParameters.zip(other.typeParameters).all { it.first.type.isAssignableFrom(it.second.type) }
}

fun ParameterTypeContext.getGenericContext(): GenericsContext {
    val generics = this.javaClass.getAllDeclaredFields().find { it.name == "generics" }!!
    return generics.let {
        it.isAccessible = true
        it.get(this) as GenericsContext
    }.also { generics.isAccessible = false }
}

fun ParameterTypeContext.getResolvedType(): org.javaruntype.type.Type<*> {
    val generics = this.javaClass.getAllDeclaredFields().find { it.name == "resolved" }!!
    return generics.let {
        it.isAccessible = true
        it.get(this) as org.javaruntype.type.Type<*>
    }.also { generics.isAccessible = false }
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

private fun org.javaruntype.type.Type<*>.getAllTypeParameters(): List<org.javaruntype.type.Type<*>> {
    val res = mutableListOf<org.javaruntype.type.Type<*>>()
    val queue = ArrayDeque<org.javaruntype.type.Type<*>>()
    this.typeParameters.forEach { queue.add(it.type) }
    while (queue.isNotEmpty()) {
        val el = queue.removeFirst()
        res.add(el)
        el.typeParameters.forEach { queue.add(it.type) }
    }
    return res
}

//org.javaruntype.type.Type<*>

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