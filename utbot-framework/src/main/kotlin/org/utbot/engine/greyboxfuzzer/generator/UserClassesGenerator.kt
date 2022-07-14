package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.javaruntype.type.Types
import org.utbot.engine.isPublic
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.rawType
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.context.MethodGenericsContext
import ru.vyarus.java.generics.resolver.util.GenericsResolutionUtils
import ru.vyarus.java.generics.resolver.util.GenericsUtils
import soot.Hierarchy
import soot.Scene
import sun.misc.Unsafe
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.*
import kotlin.random.Random
import kotlin.system.exitProcess
import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl as GParameterizedTypeImpl

class UserClassesGenerator : ComponentizedGenerator<Any>(Any::class.java) {

    var clazz: Class<*>? = null
    var parameterType: Type? = null
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
            it.parameterType = parameterType
            it.depth = depth
            it.parameterTypeContext = parameterTypeContext
        }
    }

    override fun numberOfNeededComponents(): Int {
        return (parameterType as? ParameterizedTypeImpl)?.actualTypeArguments?.size ?: 0
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

    private fun generateImplementerInstance(random: SourceOfRandomness?, status: GenerationStatus?): Any? {
        val sootClass = Scene.v().classes.find { it.name == parameterType!!.toClass().name } ?: return null
        val hierarchy = Hierarchy()
        val implementers =
            sootClass.getImplementersOfWithChain(hierarchy)//Hierarchy().getImplementersOf(sootClass).ifEmpty { return null }
        val randomImplementersChain = implementers?.randomOrNull()?.drop(1) ?: return null
        val generics = mutableListOf<Pair<Type, MutableList<Type>>>()
        val resolvedType = parameterTypeContext!!.getGenericContext().resolveType(parameterType)
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
            val newTp = (extendType as ParameterizedTypeImpl).actualTypeArguments
            tp.mapIndexed { index, typeVariable -> typeVariable to newTp[index] }
                .forEach { typeVar ->
                    val indexOfTypeParam = generics.indexOfFirst { it.second.last() == typeVar.first }
                    if (indexOfTypeParam != -1) {
                        generics[indexOfTypeParam].second.add(typeVar.second)
                    }
                }
            prevImplementer = javaImplementer
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
                ?: generateInstanceWithStatics(Types.forJavaLangReflectType(parameterizedType), genericsContext, depth + 1)
        } else {
            generateInstanceWithStatics(Types.forJavaLangReflectType(parameterizedType), genericsContext, depth + 1) ?:
            generateInstanceViaConstructor(prevImplementer, genericsContext, depth + 1)
        }
    }

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): Any? {
        parameterType ?: return null
        clazz ?: return null
        println("TRYING TO GENERATE $parameterType")
        if (parameterType!!.toClass().name == "java.lang.Class") return generateClass()
        val modifiers = parameterType!!.toClass().modifiers
        //TODO! generate instances of abstract classes and interfaces
        if (modifiers.and(Modifier.ABSTRACT) > 0 || modifiers.and(Modifier.INTERFACE) > 0) {
            return generateImplementerInstance(random, status)
        }
//        if (parameterType!!.typeName) {
//
//        }
        val resolvedType = parameterTypeContext!!.getGenericContext().resolveType(parameterType)
        val actualTypeParams = (resolvedType as? GParameterizedTypeImpl)?.actualTypeArguments?.toList() ?: emptyList()
        val klassTypeParams = resolvedType.toClass().typeParameters.map { it.name }
        val gm = LinkedHashMap<String, Type>()
        klassTypeParams.zip(actualTypeParams).forEach { gm[it.first] = it.second }
//        val genericContext = parameterTypeContext?.getGenericContext()
//        val genericMap = parameterTypeContext?.getGenericContext()?.genericsMap() as LinkedHashMap
//
//        val resolvedGenerics = GenericsResolver.resolve(clazz).resolveGenericOf(parameterType)
//        exitProcess(0)
        //GenericsResolver.resolve(clazz).method(method)//GenericsResolutionUtils.resolveGenerics(parameterType, mapOf())
        val m = mutableMapOf(clazz!! to gm)
        val genericsInfo = GenericsInfo(clazz, m)
        val gctx = GenericsContext(genericsInfo, clazz)
        val inst =
            if (Random.getTrue(50)) {
                generateInstanceViaConstructor(resolvedType.toClass(), gctx, depth)
                    ?: generateInstanceWithStatics(Types.forJavaLangReflectType(resolvedType), gctx, depth)
            } else {
                generateInstanceWithStatics(Types.forJavaLangReflectType(resolvedType), gctx, depth)
                    ?: generateInstanceViaConstructor(resolvedType.toClass(), gctx, depth)
            }
        return inst
//        repeat(100) {
//            val instanceByConstructorInvocation = generateInstanceViaConstructor(resolvedType.componentClass, gctx)
//            println("INSTANCE = $instanceByConstructorInvocation")
//        }
//        exitProcess(0)
//        repeat(100) {
//            val instanceGeneratedInstanceViaLegalWay =
//                generateInstanceViaLegalWay(parameterType!!, gctx)
//            println("INSTANCE = $instanceGeneratedInstanceViaLegalWay")
//        }
//        return null
//        if (/*Random.getTrue(25)* ||*/ depth >= DataGeneratorSettings.maxDepthOfGeneration) {
//            //generateInstanceViaConstructorInvocation(clazz!!, gctx)?.let { return it }
//            setAllObjectsToNull = true
//        }
//        val i = generateInstanceViaConstructorInvocation(clazz!!, gctx)
//        println("INTANCE = $i")
//        exitProcess(0)
//        val fieldValues = clazz!!.getAllDeclaredFields().map { field ->
//            println("F = $field ${field.name}")
//            val fieldValue =
//                if (Modifier.STATIC.and(field.modifiers) > 0 && Modifier.FINAL.and(field.modifiers) > 0) {
//                    field.getFieldValue(null)
//                } else {
//                    generateFieldValue(field, gctx, setAllObjectsToNull)
//                }
//            field to fieldValue
//        }
//        println("TRYING TO ALLOCATE INSTANCE OF $clazz")
//        val instance = UNSAFE.allocateInstance(clazz)
//        fieldValues.forEach { (field, value) ->
//            field.generateInstance(instance, value)
//        }
//        return instance
    }

    private fun MethodGenericsContext.resolveRetTypeForParameterizedType(gctx: GenericsContext): GParameterizedTypeImpl {
        val retType = gctx.method(this.currentMethod()).resolveReturnType()
        return ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl(
            retType.rawType.toClass().rawType,
            *(parameterType as ParameterizedTypeImpl).actualTypeArguments
        )
    }

    private fun generateInstanceWithStatics(
        resolvedType: org.javaruntype.type.Type<*>,
        gctx: GenericsContext,
        depth: Int
    ): Any? {
        //println("VIA STATIC FIELD")
        if (depth > DataGeneratorSettings.maxDepthOfGeneration) return null
        //TODO filter not suitable methods with generics with bad bounds
//        val m = resolvedType.componentClass.declaredMethods.filter { it.hasModifiers(Modifier.STATIC, Modifier.PUBLIC) }.first()
//        val me = gctx.method(m)
//        val resolvedReturnType = (me.resolveReturnType() as ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl)
//        resolvedType.typeParameters.first().type.
//        val m = this.currentMethod()
//        val generics = gctx.genericsMap()
//        GenericsUtils.resolveTypeVariables(m.parameters.first().parameterizedType, generics)
////GenericsResolutionUtils.resolveRawGeneric()

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
        //println("VIA CONSTRUCTOR INVOCATION")
        val randomPublicConstructor = clazz.declaredConstructors.filter { it.isPublic }.randomOrNull()
        val randomConstructor = clazz.declaredConstructors.randomOrNull()
        val constructor = if (Random.getTrue(75)) randomPublicConstructor ?: randomConstructor else randomConstructor
        constructor ?: return null
        //println("CONSTRUCTOR = $constructor")
        val oldAccessibleFlag = constructor.isAccessible
        constructor.isAccessible = true
        val setAllObjectsToNull = depth >= DataGeneratorSettings.maxDepthOfGeneration
        val parameterValues =
            constructor.parameters.map { parameter ->
                //val parameterType = gctx.resolveType(parameter.parameterizedType)
                val parameterValue = generateParameterValue(parameter, clazz.name, gctx, setAllObjectsToNull)
                //println("DEPTH = $depth VALUE OF PARAM $parameterType is $parameterValue")
                parameterValue
            }
        return try {
            constructor.newInstance(*parameterValues.toTypedArray())
                .also { constructor.isAccessible = oldAccessibleFlag }
        } catch (e: java.lang.IllegalArgumentException) {
            null
        } catch (e: InvocationTargetException) {
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
            parameter.declaringExecutable.let { it.declaringClass.name + '.' + it.name },
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
                    annotatedType,
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
        val fieldValue = generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        return fieldValue
    }


    private fun createParameterTypeContext(
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
}


//class UserClassesGenerator<T> : ComponentizedGenerator<T>(T::class.java) {
//
//    companion object {
//        val UNSAFE = run {
//            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
//            field.isAccessible = true
//            field.get(null) as Unsafe
//        }
//    }
//
//    override fun generate(random: SourceOfRandomness, status: GenerationStatus): T {
////        val subffields = mutableListOf<FField>()
////        val generator = DataGenerator(generatorRepository)
////        val fieldValues = clazz.getAllDeclaredFields().map { field ->
////            val fieldValue = generator.generate(field, random, status)
////            subffields.add(fieldValue!!)
////            field to fieldValue.value
////        }
////        val instance = UNSAFE.allocateInstance(clazz)
////        fieldValues.forEach { (field, value) ->
////            field.generateInstance(instance, value)
////        }
//        return null as T;
//    }
////    override fun generate(random: SourceOfRandomness, status: GenerationStatus): FField {
////        val subffields = mutableListOf<FField>()
////        val generator = DataGenerator(generatorRepository)
////        val fieldValues = clazz.getAllDeclaredFields().map { field ->
////            val fieldValue = generator.generate(field, random, status)
////            subffields.add(fieldValue!!)
////            field to fieldValue.value
////        }
////        val instance = UNSAFE.allocateInstance(clazz)
////        fieldValues.forEach { (field, value) ->
////            field.generateInstance(instance, value)
////        }
////        return FField(clazz, instance, this, classIdForType(clazz), subffields, false)
////    }
//
//}



