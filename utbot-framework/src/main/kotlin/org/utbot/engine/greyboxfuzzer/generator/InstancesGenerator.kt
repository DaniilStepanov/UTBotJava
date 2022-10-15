@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.isPublic
import org.utbot.engine.rawType
import org.utbot.framework.plugin.api.util.signature
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
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
                    }
                    //Avoiding recursion
                    .filter { it.parameterTypes.all { !it.name.contains(clazz.name) } }
                    .chooseRandomConstructor()
            } catch (e: Error) {
                null
            }
        val randomConstructor =
            try {
                clazz.declaredConstructors
                    .filter { it.parameterTypes.all { !it.name.contains(clazz.name) } }
                    .toList().chooseRandomConstructor()
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
            )
            println("GOT A GENERATOR $generator")
            try {
                generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
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
                    .filter { it.first.parameterTypes.all { !it.name.contains(resolvedType.componentClass.name) } }
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

    fun generateInterfaceInstanceViaStaticCall(
        method: Method,
        parameterTypeContext: ParameterTypeContext,
        depth: Int
    ): Any? {
//        val resolvedType = parameterTypeContext.getResolvedType()
//        val cl = resolvedType.rawClass
//        val clazz = method.genericReturnType as ParameterizedType
//        val resolvedJavaType = parameterTypeContext.getGenericContext().resolveType(parameterTypeContext.type()) as ParameterizedType
//        val gm = LinkedHashMap<String, Type>()
//        clazz.actualTypeArguments.toList().zip(resolvedJavaType.actualTypeArguments.toList()).forEach {
//            gm[it.first.typeName] = it.second
//        }
//        val m = mutableMapOf(cl to gm)
//        val genericsContext = GenericsContext(GenericsInfo(cl, m), cl)
//        val methodReturnType = method.genericReturnType
//        genericsContext
////GenericsResolutionUtils.resolveDirectRawGenerics(method, gm)
////GenericsUtils.resolveTypeVariables(method.typeParameters.first().bounds[0], gm)
        val cl = parameterTypeContext.getResolvedType().rawClass
        val clazz = method.genericReturnType as ParameterizedType
        val resolvedJavaType =
            parameterTypeContext.getGenericContext().resolveType(parameterTypeContext.type()) as ParameterizedType
        val gm = LinkedHashMap<String, Type>()
        clazz.actualTypeArguments.toList().zip(resolvedJavaType.actualTypeArguments.toList()).forEach {
            gm[it.first.typeName] = it.second
        }
        val m = mutableMapOf(cl to gm)
        val generics = LinkedHashMap<String, Type>()
        (method.genericReturnType as? ParameterizedTypeImpl)?.actualTypeArguments?.forEachIndexed { index, typeVariable ->
            generics[typeVariable.typeName] = (resolvedJavaType as GParameterizedTypeImpl).actualTypeArguments[index]
        }
        val gctx = GenericsContext(GenericsInfo(cl, m), cl)
        gctx.method(method).methodGenericsMap().forEach { (s, type) -> generics.getOrPut(s) { type } }
        val args = method.parameters.mapIndexed { index, parameter ->
            val resolvedParameterType = GenericsUtils.resolveTypeVariables(parameter.parameterizedType, generics)
            createParameterContextForParameter(parameter, index, gctx, resolvedParameterType).let { ptx ->
                val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(ptx, depth)
                generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
            }
        }
        return try {
            method.isAccessible = true
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

    //    fun regenerateRandomFields(clazz: Class<*>, classInstance: Any, numOfFields: Int): Any? {
//
//    }
    fun regenerateRandomFields(clazz: Class<*>, classInstance: Any) {
        val parameterTypeContext = ParameterTypeContext.forClass(clazz)
        val fields = clazz.getAllDeclaredFields()
            .filterNot { it.hasModifiers(Modifier.STATIC, Modifier.FINAL) }
            .toMutableList()
        repeat(Random.nextInt(0, 10)) {
            val randomField = fields.randomOrNull() ?: return@repeat
            if (Random.getTrue(20)) {
                randomField.setDefaultValue(classInstance)
            } else {
                setNewFieldValue(randomField, parameterTypeContext, classInstance, 0, false)
            }
            fields.remove(randomField)
        }
    }

    fun regenerateFields(clazz: Class<*>, classInstance: Any, fieldsToRegenerate: List<Field>) {
        val parameterTypeContext = ParameterTypeContext.forClass(clazz)
        for (field in fieldsToRegenerate) {
            if (Random.getTrue(20)) {
                field.setDefaultValue(classInstance)
            } else {
                setNewFieldValue(field, parameterTypeContext, classInstance, 0, false)
            }
        }
    }

    private fun setNewFieldValue(
        field: Field,
        parameterTypeContext: ParameterTypeContext,
        clazzInstance: Any?,
        depth: Int,
        isRecursiveWithUnsafe: Boolean
    ) {
        if (field.hasModifiers(Modifier.STATIC, Modifier.FINAL)) return
        field.isAccessible = true
        val oldFieldValue = field.getFieldValue(clazzInstance)
        if (field.hasAtLeastOneOfModifiers(Modifier.STATIC, Modifier.FINAL) && oldFieldValue != null) return
        val fieldType = parameterTypeContext.getGenericContext().resolveFieldType(field)
        println("F = $field TYPE = $fieldType OLDVALUE = $oldFieldValue")
        val parameterTypeContextForResolvedType = createParameterTypeContext(
            field.name,
            field.annotatedType,
            field.declaringClass.name,
            Types.forJavaLangReflectType(fieldType),
            parameterTypeContext.getGenericContext()
        )
        val generator = DataGeneratorSettings.generatorRepository.getOrProduceGenerator(
            parameterTypeContextForResolvedType,
            depth
        ) ?: return
        if (isRecursiveWithUnsafe) {
            (listOf(generator) + generator.getAllComponents()).forEach {
                if (it is UserClassesGenerator) it.generationMethod = GenerationMethod.UNSAFE
            }
        }
        println("I GOT GENERATOR!! $generator")
        val newFieldValue =
            try {
                generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
            } catch (e: Exception) {
                null
            }
        println("NEW VALUE GENERATED!!")
        if (newFieldValue != null) {
            try {
                println("NEW VALUE = ${newFieldValue} CLASS ${newFieldValue::class.java}")
            } catch (e: Throwable) {
                println("NEW VALUE OF CLASS ${newFieldValue::class.java} generated")
            }
        }
        if (newFieldValue != null) {
            field.setFieldValue(clazzInstance, newFieldValue)
        }
    }

    fun generateInstanceWithUnsafe(
        clazz: Class<*>,
        depth: Int,
        isRecursiveWithUnsafe: Boolean,
        genericsContext: GenericsContext?
    ): Any? {
        println("TRYING TO GENERATE ${clazz.name} instance")
        if (depth >= DataGeneratorSettings.maxDepthOfGeneration) return null
        val clazzInstance =
            try {
                UserClassesGenerator.UNSAFE.allocateInstance(clazz)
            } catch (e: Exception) {
                return null
            } catch (e: Error) {
                return null
            }
        clazz.getAllDeclaredFields().forEach { field ->
            try {
                val ptx = genericsContext?.let { field.buildParameterContext(genericsContext) }
                    ?: ParameterTypeContext.forField(field)
                setNewFieldValue(field, ptx, clazzInstance, depth, isRecursiveWithUnsafe)
            } catch (_: Throwable) {
                println("CANT SET FIELD ${field.name}")
            }
        }
        return clazzInstance
    }

    fun generateInstanceWithDefaultConstructorOrUnsafe(clazz: Class<*>): Any? {
        val defaultConstructor = clazz.constructors.find { it.parameterCount == 0 }
        return if (defaultConstructor != null) {
            defaultConstructor.newInstance()
        } else {
            try {
                UserClassesGenerator.UNSAFE.allocateInstance(clazz)
            } catch (e: Throwable) {
                null
            }
        }
    }

}