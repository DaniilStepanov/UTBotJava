package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.javaruntype.type.TypeParameter
import org.javaruntype.type.Types
import org.utbot.engine.isPublic
import org.utbot.engine.greyboxfuzzer.util.*
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.util.GenericsUtils
import ru.vyarus.java.generics.resolver.util.TypeUtils
import soot.Hierarchy
import soot.Scene
import sun.misc.Unsafe
import sun.reflect.annotation.AnnotatedTypeFactory
import sun.reflect.annotation.TypeAnnotation
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl
import java.lang.reflect.*
import kotlin.random.Random
import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl as GParameterizedTypeImpl

class UserClassesGenerator : ComponentizedGenerator<Any>(Any::class.java) {

    var clazz: Class<*>? = null
    var parameterTypeContext: ParameterTypeContext? = null
    var depth = 0

    companion object {
        val UNSAFE = run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }

    override fun copy(): Generator<Any> {
        return UserClassesGenerator().also {
            it.clazz = clazz
            it.depth = depth
            it.parameterTypeContext = parameterTypeContext
        }
    }

    override fun canGenerateForParametersOfTypes(typeParameters: MutableList<TypeParameter<*>>?): Boolean {
        return true
    }

    override fun numberOfNeededComponents(): Int {
        return parameterTypeContext?.getResolvedType()?.typeParameters?.size ?: 0
        //return (parameterType as? ParameterizedTypeImpl)?.actualTypeArguments?.size ?: 0
    }

    private fun generateClass(): Class<*> {
        return parameterTypeContext!!.getResolvedType().typeParameters.first().type.componentClass
    }

    private fun generateParameterizedTypeImpl(
        clazz: Class<*>,
        actualTypeParameters: Array<Type>
    ): ParameterizedTypeImpl {
        val constructor = ParameterizedTypeImpl::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        return constructor.newInstance(clazz, actualTypeParameters, null) as ParameterizedTypeImpl
    }

    private fun generateImplementerInstance(resolvedType: Type): Any? {
        val sootClass = Scene.v().classes.find { it.name == parameterTypeContext!!.rawClass.name } ?: return null
        val hierarchy = Hierarchy()
        val implementers =
            sootClass.getImplementersOfWithChain(hierarchy)//Hierarchy().getImplementersOf(sootClass).ifEmpty { return null }
        val randomImplementersChain = implementers?.randomOrNull()?.drop(1) ?: return null
        val generics = mutableListOf<Pair<Type, MutableList<Type>>>()
        var prevImplementer = sootClass.toJavaClass()
        (resolvedType as? GParameterizedTypeImpl)?.actualTypeArguments?.forEachIndexed { index, typeVariable ->
            generics.add(typeVariable to mutableListOf(prevImplementer.toClass().typeParameters[index]))
        }
        for (implementer in randomImplementersChain) {
            val javaImplementer = implementer.toJavaClass()
            val extendType = javaImplementer.let { it.genericInterfaces + it.genericSuperclass }
                .find { it.toClass() == prevImplementer }
            (extendType as? ParameterizedTypeImpl)
            val tp = prevImplementer.typeParameters
            prevImplementer = javaImplementer
            if (tp.isEmpty()) continue
            val newTp = (extendType as ParameterizedTypeImpl).actualTypeArguments
            tp.mapIndexed { index, typeVariable -> typeVariable to newTp[index] }
                .forEach { typeVar ->
                    val indexOfTypeParam = generics.indexOfFirst { it.second.last() == typeVar.first }
                    if (indexOfTypeParam != -1) {
                        generics[indexOfTypeParam].second.add(typeVar.second)
                    }
                }
        }
        val g = prevImplementer.typeParameters.map { tp -> tp.name to generics.find { it.second.last() == tp }?.first }
            .toMap()
        val actualTypeParams = prevImplementer.typeParameters.map { g[it.name] ?: return null }.toTypedArray()
        val parameterizedType = generateParameterizedTypeImpl(prevImplementer, actualTypeParams)
        val gm = LinkedHashMap<String, Type>()
        g.forEach { gm.put(it.key, it.value!!) }
        val m = mutableMapOf(prevImplementer to gm)
        val genericsContext = GenericsContext(GenericsInfo(prevImplementer, m), prevImplementer)
        return if (Random.nextBoolean()) {
            generateInstanceViaConstructor(prevImplementer, genericsContext, depth + 1)
                ?: generateInstanceWithStatics(
                    Types.forJavaLangReflectType(parameterizedType),
                    genericsContext,
                    depth + 1
                )
        } else {
            generateInstanceWithStatics(Types.forJavaLangReflectType(parameterizedType), genericsContext, depth + 1)
                ?: generateInstanceViaConstructor(prevImplementer, genericsContext, depth + 1)
        }
    }

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): Any? {
        clazz ?: return null
        val parameterType = parameterTypeContext!!.getResolvedType()
        println("TRYING TO GENERATE $parameterType depth: $depth")
        if (parameterType.componentClass.name == "java.lang.Class") return generateClass()
        val modifiers = parameterType.componentClass.modifiers
        val resolvedJavaType = parameterTypeContext!!.getGenericContext().resolveType(parameterTypeContext!!.type())
        if (modifiers.and(Modifier.ABSTRACT) > 0 || modifiers.and(Modifier.INTERFACE) > 0) {
            return generateImplementerInstance(resolvedJavaType)
        }
        //TODO!! Implement generation for Inner classes
        if (TypeUtils.getOuter(resolvedJavaType) != null) return null
        val gctx = createGenericsContext(resolvedJavaType, clazz!!)
        val inst =
            if (Random.getTrue(50)) {
                generateInstanceViaConstructor(resolvedJavaType.toClass(), gctx, depth + 1)
                    ?: generateInstanceWithStatics(Types.forJavaLangReflectType(resolvedJavaType), gctx, depth + 1)
            } else {
                generateInstanceWithStatics(Types.forJavaLangReflectType(resolvedJavaType), gctx, depth + 1)
                    ?: generateInstanceViaConstructor(resolvedJavaType.toClass(), gctx, depth + 1)
            }
        if (inst == null) {
            //UNSAFE
        }
        return inst
    }

    private fun generateInstanceWithStatics(
        resolvedType: org.javaruntype.type.Type<*>,
        gctx: GenericsContext,
        depth: Int
    ): Any? {
        println("VIA STATIC FIELD")
        if (depth > DataGeneratorSettings.maxDepthOfGeneration) return null
        //TODO filter not suitable methods with generics with bad bounds
        println()
        //TODO make it work for subtypes
        val resolvedStaticMethods =
            resolvedType.componentClass.declaredMethods.filter { it.hasModifiers(Modifier.STATIC, Modifier.PUBLIC) }
                .map { it to gctx.method(it).resolveReturnType() }
                .filter { it.first.returnType.toClass() == resolvedType.componentClass }
        val resolvedStaticFields =
            resolvedType.componentClass.declaredFields.filter { it.hasModifiers(Modifier.STATIC, Modifier.PUBLIC) }
                .map { it to gctx.resolveFieldType(it) }
                .filter { it.first.type.toClass() == resolvedType.componentClass }
        //println("FIELD = $resolvedStaticFields")
        val (fieldOrMethodToProvideInstance, typeToGenerate) =
            if (Random.nextBoolean()) {
                resolvedStaticFields.randomOrNull() ?: resolvedStaticMethods.randomOrNull()
            } else {
                resolvedStaticMethods.randomOrNull() ?: resolvedStaticFields.randomOrNull()
            } ?: return null
        val fieldValue = when (fieldOrMethodToProvideInstance) {
            is Field -> fieldOrMethodToProvideInstance.getFieldValue(null)
            is Method -> {
                val parameterValues =
                    if (fieldOrMethodToProvideInstance.typeParameters.isNotEmpty()) {
                        generateParameterValuesToFunctionsWithGenerics(
                            fieldOrMethodToProvideInstance,
                            gctx,
                            resolvedType
                        )
                    } else {
                        fieldOrMethodToProvideInstance.parameters.map { parameter ->
                            generateParameterValue(parameter, resolvedType.componentClass.name, gctx, false)
                        }
                    }
                fieldOrMethodToProvideInstance.isAccessible = true
                try {
                    fieldOrMethodToProvideInstance.invoke(null, *parameterValues.toTypedArray())
                } catch (e: InvocationTargetException) {
                    return null
                } catch (e: Exception) {
                    return null
                } catch (e: Error) {
                    return null
                }
            }
            else -> return null
        }
        return fieldValue
    }

    private fun generateParameterValuesToFunctionsWithGenerics(
        method: Method,
        gctx: GenericsContext,
        resolvedType: org.javaruntype.type.Type<*>
    ): List<Any?> {
        val parameterType = parameterTypeContext!!.getGenericContext().resolveType(parameterTypeContext!!.type())
        val generics = LinkedHashMap<String, Type>()
        (method.genericReturnType as? ParameterizedTypeImpl)?.actualTypeArguments?.forEachIndexed { index, typeVariable ->
            generics[typeVariable.typeName] = (parameterType as ParameterizedTypeImpl).actualTypeArguments[index]
        }
        gctx.method(method).methodGenericsMap().forEach { (s, type) -> generics.getOrPut(s) { type } }
        return method.parameters.map { parameter ->
            println("OLD TYPE = ${parameter.type}")
            val resolvedParameterType = GenericsUtils.resolveTypeVariables(parameter.parameterizedType, generics)
            println("NEW TYPE = ${resolvedParameterType}")
            val value =
                generateParameterValue(parameter, resolvedType.componentClass.name, gctx, false, resolvedParameterType)
            println("GENERATED VALUE = $value")
            value
        }
    }

    private fun generateInstanceViaConstructor(clazz: Class<*>, gctx: GenericsContext, depth: Int): Any? {
        val randomPublicConstructor =
            clazz.declaredConstructors
                .filter {
                    it.isPublic || !it.hasAtLeastOneOfModifiers(
                        Modifier.PROTECTED,
                        Modifier.PRIVATE
                    )
                }
                .randomOrNull()
        val randomConstructor = clazz.declaredConstructors.randomOrNull()
        val constructor = if (Random.getTrue(75)) randomPublicConstructor ?: randomConstructor else randomConstructor
        constructor ?: return null
        constructor.isAccessible = true
        val resolvedConstructor = gctx.constructor(constructor)
        val parameterValues = constructor.parameters.withIndex().map { indexedParameter ->
            val parameterContext =
                createParameterContextForParameter(indexedParameter.value, indexedParameter.index, resolvedConstructor)
            val type = parameterContext.type()
            var className = ""
            if (type is TypeVariableImpl<*> || type is ParameterizedTypeImpl) {
                className = parameterContext.getResolvedType().toString()
            }
            val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(
                parameterContext,
                depth + 1,
                className
            )!!
            generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        }
        //println("PARAMETERS = $parameterValues")
        return try {
            constructor.newInstance(*parameterValues.toTypedArray())
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

    private fun generateParameterValue(
        parameter: Parameter,
        clazzName: String,
        gctx: GenericsContext,
        setAllObjectsToNull: Boolean,
        resolvedType: Type? = null
    ): Any? =
        generateValueOfType(
            parameter,
            gctx,
            parameter.name,
            //parameter.declaringExecutable.let { it.declaringClass.name + '.' + it.name },
            parameter.annotatedType,
            clazzName,
            ParameterTypeContext.forParameter(parameter),
            setAllObjectsToNull,
            resolvedType
        )

    private fun generateFieldValue(field: Field, gctx: GenericsContext, setAllObjectsToNull: Boolean): Any? =
        generateValueOfType(
            field,
            gctx,
            field.name,
            field.annotatedType,
            field.declaringClass.name,
            ParameterTypeContext.forField(field),
            setAllObjectsToNull
        )

    private fun generateValueOfType(
        fieldOrParameterForGeneration: Any,
        gctx: GenericsContext,
        name: String,
        annotatedType: AnnotatedType,
        declaringTypeName: String,
        alternativeTypeContext: ParameterTypeContext,
        setAllObjectsToNull: Boolean,
        resolvedType: Type? = null
    ): Any? {
        //TODO!!!!!!! Make it work for inner classes
        if (fieldOrParameterForGeneration.toString().contains("$")) return null

        var clazz: Class<*>?
        val context =
            try {
                val finallyResolvedType = when (fieldOrParameterForGeneration) {
                    is Field -> {
                        clazz = fieldOrParameterForGeneration.type
                        gctx.resolveFieldType(fieldOrParameterForGeneration)
                    }
                    is Parameter -> {
                        clazz = fieldOrParameterForGeneration.type
                        resolvedType ?: gctx.resolveType(fieldOrParameterForGeneration.parameterizedType)
                    }
                    else -> return null
                }
                createParameterTypeContext(
                    name,
                    AnnotatedTypeFactory.buildAnnotatedType(
                        finallyResolvedType,
                        TypeAnnotation.LocationInfo.BASE_LOCATION,
                        arrayOf(),
                        arrayOf(),
                        null
                    ),
                    //annotatedType,
                    declaringTypeName,
                    Types.forJavaLangReflectType(finallyResolvedType),
                    gctx
                )
            } catch (e: java.lang.IllegalArgumentException) {
                clazz = alternativeTypeContext.type().toClass()
                alternativeTypeContext
            }
        if (!clazz!!.isPrimitive && setAllObjectsToNull)
            return null

        val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(context, depth + 1)
        //generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        return generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
    }
}
