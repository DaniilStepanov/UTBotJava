@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import org.javaruntype.exceptions.TypeValidationException
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.getAllDeclaredFields
import org.utbot.engine.greyboxfuzzer.util.toClass
import org.utbot.engine.rawType
import ru.vyarus.java.generics.resolver.GenericsResolver
import ru.vyarus.java.generics.resolver.context.ConstructorGenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.*


fun ComponentizedGenerator<*>.getComponents(): List<Generator<*>> {
    val components = this.javaClass.getAllDeclaredFields().find { it.name == "components" } ?: return listOf()
    return components.let {
        it.isAccessible = true
        it.get(this) as List<Generator<*>>
    }.also { components.isAccessible = false }
}

fun Generator<*>.getAllComponents(): List<Generator<*>> {
    if (this !is ComponentizedGenerator<*>) return emptyList()
    val que = ArrayDeque<Generator<*>>()
    val res = mutableListOf<Generator<*>>()
    this.getComponents().forEach { que.add(it) }
    while (que.isNotEmpty()) {
        val comp = que.removeFirst()
        res.add(comp)
        (comp as? ComponentizedGenerator<*>)?.getComponents()?.forEach(que::add)
    }
    return res
}

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
        it.parameterTypeContext = parameterTypeContext
        it.depth = depth
    })
}

fun GeneratorRepository.replaceGenerator(
    forClass: Class<*>,
    newGenerators: Set<Generator<*>>
) {
    val generatorsField = this.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
    generatorsField.isAccessible = true
    val map = generatorsField.get(this) as java.util.HashMap<Class<*>, Set<Generator<*>>>
    map[forClass] = newGenerators
}


fun GeneratorRepository.getGenerators(): HashMap<Class<*>, Set<Generator<*>>> {
    val generatorsField = this.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
    generatorsField.isAccessible = true
    return generatorsField.get(this) as java.util.HashMap<Class<*>, Set<Generator<*>>>
}

fun GeneratorRepository.removeGenerator(
    forClass: Class<*>
) {
    val generatorsField = this.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
    generatorsField.isAccessible = true
    val map = generatorsField.get(this) as java.util.HashMap<Class<*>, Set<Generator<*>>>
    map.remove(forClass)
}


fun GeneratorRepository.getOrProduceGenerator(field: Field, depth: Int = 0): Generator<*>? =
    getOrProduceGenerator(ParameterTypeContext.forField(field), depth)

fun GeneratorRepository.getOrProduceGenerator(param: Parameter, parameterIndex: Int, depth: Int = 0): Generator<*>? {
    val parameterTypeContext = param.createParameterTypeContext(parameterIndex)
    return getOrProduceGenerator(parameterTypeContext, depth)
}

fun Parameter.createParameterTypeContext(parameterIndex: Int): ParameterTypeContext =
    try {
        ParameterTypeContext.forParameter(this)
    } catch (e: TypeValidationException) {
        val clazz = this.type
        val parametersBounds =
            this.type.typeParameters.map { it.bounds.firstOrNull() ?: Any::class.java.rawType }.toTypedArray()
        val p = ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl(
            this.type,
            *parametersBounds
        )
        val genericContext = createGenericsContext(p, clazz)
        createParameterContextForParameter(this, parameterIndex, genericContext, p)
    }


fun GeneratorRepository.getOrProduceGenerator(clazz: Class<*>, depth: Int = 0): Generator<*>? =
    getOrProduceGenerator(ParameterTypeContext.forClass(clazz), depth)

fun GeneratorRepository.getOrProduceGenerator(
    parameterTypeContext: ParameterTypeContext,
    depth: Int
): Generator<*>? {
    parameterTypeContext.getAllParameterTypeContexts().reversed().forEach { typeContext ->
        if (typeContext.getResolvedType().toString().contains("$")) return null
        try {
            this.produceGenerator(typeContext)
        } catch (e: Exception) {
            val classNameToAddGenerator = typeContext.rawClass
            this.addGenerator(
                classNameToAddGenerator,
                typeContext.type(),
                typeContext,
                depth + 1
            )
        }
    }
    val generator = try {
        this.produceGenerator(parameterTypeContext)
    } catch (e: Exception) {
        println("CANT GET GENERATOR FOR ${parameterTypeContext.getResolvedType()}")
        return null
    }
    (listOf(generator) + generator.getAllComponents()).forEach {
        GeneratorConfigurator.configureGenerator(it, 90)
        if (it is UserClassesGenerator) this.removeGenerator(it.parameterTypeContext!!.getResolvedType().rawClass)
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

fun ParameterTypeContext.getAllParameterTypeContexts(): List<ParameterTypeContext> {
    val res = mutableListOf(this)
    val queue = ArrayDeque<ParameterTypeContext>()
    if (this.isArray) {
        this.arrayComponentContext().let { queue.add(it) }
    }
    this.typeParameterContexts(DataGeneratorSettings.sourceOfRandomness).forEach { queue.add(it) }
    while (queue.isNotEmpty()) {
        val el = queue.removeFirst()
        if (el.isArray) {
            el.arrayComponentContext().let { queue.add(it) }
        }
        el.typeParameterContexts(DataGeneratorSettings.sourceOfRandomness).forEach { queue.add(it) }
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

fun createGenericsContext(resolvedType: Type, clazz: Class<*>): GenericsContext {
    val actualTypeParams =
        (resolvedType as? ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl)?.actualTypeArguments?.toList()
            ?: emptyList()
    val klassTypeParams = resolvedType.toClass()?.typeParameters?.map { it.name }
    val gm = LinkedHashMap<String, Type>()
    klassTypeParams?.zip(actualTypeParams)?.forEach { gm[it.first] = it.second }
    val m = mutableMapOf(clazz to gm)
    val genericsInfo = GenericsInfo(clazz, m)
    return GenericsContext(genericsInfo, clazz)
}

fun createParameterTypeContext(
    parameterName: String,
    parameterType: AnnotatedType,
    declarerName: String,
    resolvedType: org.javaruntype.type.Type<*>,
    generics: GenericsContext
): ParameterTypeContext {
    val constructor = ParameterTypeContext::class.java.declaredConstructors.minByOrNull { it.parameters.size }!!
    constructor.isAccessible = true
    return constructor.newInstance(
        parameterName,
        parameterType,
        declarerName,
        resolvedType,
        generics
    ) as ParameterTypeContext
}

private fun createParameterTypeContext(
    parameterName: String,
    parameterType: AnnotatedType,
    declarerName: String,
    resolvedType: org.javaruntype.type.Type<*>,
    generics: GenericsContext,
    parameterIndex: Int
): ParameterTypeContext {
    val constructor = ParameterTypeContext::class.java.declaredConstructors.maxByOrNull { it.parameters.size }!!
    constructor.isAccessible = true
    return constructor.newInstance(
        parameterName,
        parameterType,
        declarerName,
        resolvedType,
        generics,
        parameterIndex
    ) as ParameterTypeContext
}

fun createParameterContextForParameter(
    parameter: Parameter,
    parameterIndex: Int,
    generics: ConstructorGenericsContext
): ParameterTypeContext {
    val exec = parameter.declaringExecutable
    val clazz = exec.declaringClass
    val declarerName = clazz.name + '.' + exec.name
    val resolvedType = generics.resolveParameterType(parameterIndex)
    return createParameterTypeContext(
        parameter.name,
        parameter.annotatedType,
        declarerName,
        Types.forJavaLangReflectType(
            resolvedType
        ),
        generics,
        parameterIndex
    )
}

fun createParameterContextForParameter(
    parameter: Parameter,
    parameterIndex: Int,
    generics: GenericsContext,
    type: Type
): ParameterTypeContext {
    val exec = parameter.declaringExecutable
    val clazz = exec.declaringClass
    val declarerName = clazz.name + '.' + exec.name
    return createParameterTypeContext(
        parameter.name,
        parameter.annotatedType,
        declarerName,
        Types.forJavaLangReflectType(type),
        generics,
        parameterIndex
    )
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