package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.isPublic
import org.utbot.engine.rawType
import org.utbot.framework.plugin.api.util.signature
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.util.GenericsUtils
import sun.reflect.annotation.AnnotatedTypeFactory
import sun.reflect.annotation.TypeAnnotation
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.*
import kotlin.random.Random
import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl as GParameterizedTypeImpl

object InstancesGenerator {

    fun generateInstanceViaConstructor(clazz: Class<*>, gctx: GenericsContext, depth: Int): Any? {
        val randomPublicConstructor =
            try {
                clazz.declaredConstructors
                    .filter {
                        it.isPublic || !it.hasAtLeastOneOfModifiers(
                            Modifier.PROTECTED,
                            Modifier.PRIVATE
                        )
                    }.chooseRandomConstructor()
            } catch (e: Error) {
                null
            }
        val randomConstructor =
            try {
                clazz.declaredConstructors.toList().chooseRandomConstructor()
            } catch (e: Error) {
                null
            }
        val constructor = if (Random.getTrue(75)) randomPublicConstructor ?: randomConstructor else randomConstructor
        constructor ?: return null
        constructor.isAccessible = true
        val resolvedConstructor = gctx.constructor(constructor)
        val parameterValues = constructor.parameters.withIndex().map { indexedParameter ->
            val parameterContext =
                createParameterContextForParameter(indexedParameter.value, indexedParameter.index, resolvedConstructor)
            val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(
                parameterContext,
                depth
            )!!
            try {
                generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
            } catch (e: Exception) {
                null
            }
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

    //TODO rewrite this
    fun generateInstanceWithStatics(
        resolvedType: org.javaruntype.type.Type<*>,
        gctx: GenericsContext,
        parameterTypeContext: ParameterTypeContext,
        depth: Int
    ): Any? {
        println("VIA STATIC FIELD")
        if (depth > DataGeneratorSettings.maxDepthOfGeneration) return null
        //TODO filter not suitable methods with generics with bad bounds
        println()
        //TODO make it work for subtypes
        val resolvedStaticMethods =
            try {
                resolvedType.componentClass.declaredMethods.filter { it.hasModifiers(Modifier.STATIC, Modifier.PUBLIC) }
                    .map { it to gctx.method(it).resolveReturnType() }
                    .filter { it.first.returnType.toClass() == resolvedType.componentClass }
            } catch (e: Error) {
                listOf()
            }
        val resolvedStaticFields =
            try {
                resolvedType.componentClass.declaredFields.filter { it.hasModifiers(Modifier.STATIC, Modifier.PUBLIC) }
                    .map { it to gctx.resolveFieldType(it) }
                    .filter { it.first.type.toClass() == resolvedType.componentClass }
            } catch (e: Error) {
                listOf()
            }
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
                            resolvedType,
                            parameterTypeContext,
                            depth
                        )
                    } else {
                        fieldOrMethodToProvideInstance.parameters.map { parameter ->
                            generateParameterValue(
                                parameter,
                                resolvedType.componentClass.name,
                                gctx,
                                false,
                                null,
                                depth
                            )
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

    fun generateInterfaceInstanceViaStaticCall(method: Method, depth: Int): Any? {
        val args = method.parameters.withIndex().map { indexedParameter ->
            println("PARAMETER = ${indexedParameter.value}")
            println("METHOD = ${method.signature}")
            val parameterContext =
                try {
                    ParameterTypeContext.forParameter(indexedParameter.value)
                } catch (e: Exception) {
                    val clazz = indexedParameter.value.type
                    val parametersBounds =
                        indexedParameter.value.type.typeParameters.map { it.bounds.firstOrNull() ?: Any::class.java.rawType }.toTypedArray()
                    val p = ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl(
                        indexedParameter.value.type,
                        *parametersBounds
                    )
                    val genericContext = createGenericsContext(p, clazz)
                    createParameterContextForParameter(indexedParameter.value, indexedParameter.index, genericContext, p)
                }
            val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(
                parameterContext,
                depth
            )!!
            generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        }
        return try {
            method.invoke(null, *args.toTypedArray())
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

    private fun generateParameterValuesToFunctionsWithGenerics(
        method: Method,
        gctx: GenericsContext,
        resolvedType: org.javaruntype.type.Type<*>,
        parameterTypeContext: ParameterTypeContext,
        depth: Int
    ): List<Any?> {
        val parameterType = parameterTypeContext.getGenericContext().resolveType(parameterTypeContext.type())
        val generics = LinkedHashMap<String, Type>()
        (method.genericReturnType as? ParameterizedTypeImpl)?.actualTypeArguments?.forEachIndexed { index, typeVariable ->
            generics[typeVariable.typeName] = (parameterType as GParameterizedTypeImpl).actualTypeArguments[index]
        }
        gctx.method(method).methodGenericsMap().forEach { (s, type) -> generics.getOrPut(s) { type } }
        return method.parameters.map { parameter ->
            println("OLD TYPE = ${parameter.type}")
            val resolvedParameterType = GenericsUtils.resolveTypeVariables(parameter.parameterizedType, generics)
            println("NEW TYPE = ${resolvedParameterType}")
            val value =
                generateParameterValue(
                    parameter,
                    resolvedType.componentClass.name,
                    gctx,
                    false,
                    resolvedParameterType,
                    depth
                )
            println("GENERATED VALUE = $value")
            value
        }
    }


    fun generateValueOfType(
        fieldOrParameterForGeneration: Any,
        gctx: GenericsContext,
        name: String,
        annotatedType: AnnotatedType,
        declaringTypeName: String,
        alternativeTypeContext: ParameterTypeContext,
        setAllObjectsToNull: Boolean,
        resolvedType: Type? = null,
        depth: Int
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

        val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(context, depth)
        //generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        return generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
    }

    fun generateParameterValue(
        parameter: Parameter,
        clazzName: String,
        gctx: GenericsContext,
        setAllObjectsToNull: Boolean,
        resolvedType: Type? = null,
        depth: Int
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
            resolvedType,
            depth
        )

    //TODO! Improve unsafe generation!
    fun generateInstanceWithUnsafe(clazz: Class<*>): Any? {
        return null
        return UserClassesGenerator.UNSAFE.allocateInstance(clazz)
    }

//    private fun generateFieldValue(field: Field, gctx: GenericsContext, setAllObjectsToNull: Boolean, depth: Int): Any? =
//        generateValueOfType(
//            field,
//            gctx,
//            field.name,
//            field.annotatedType,
//            field.declaringClass.name,
//            ParameterTypeContext.forField(field),
//            setAllObjectsToNull,
//            depth
//        )

}