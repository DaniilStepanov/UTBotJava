package org.utbot.engine.zestfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.javaruntype.type.Types
import org.utbot.engine.zestfuzzer.util.*
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.util.GenericsResolutionUtils
import sun.misc.Unsafe
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.*
import kotlin.system.exitProcess

class UserClassesGenerator : ComponentizedGenerator<Any>(Any::class.java) {

    var clazz: Class<*>? = null
    var parameterType: Type? = null
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
        }
    }

    override fun numberOfNeededComponents(): Int {
        return (parameterType as? ParameterizedTypeImpl)?.actualTypeArguments?.size ?: 0
    }

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): Any? {
        println("TRYING TO GENERATE ${parameterType!!.typeName}")
        println("DEPTH = $depth")
        val modifiers = parameterType!!.toClass().modifiers
        //TODO! generate instances of abstract classes and interfaces
        if (modifiers.and(Modifier.ABSTRACT) > 0 || modifiers.and(Modifier.INTERFACE) > 0) return null
        parameterType ?: return null
        clazz ?: return null
        val resolvedGenerics = GenericsResolutionUtils.resolveGenerics(parameterType, mapOf())
        val m = mapOf(clazz to resolvedGenerics)
        val genericsInfo = GenericsInfo(clazz, m)
        val gctx = GenericsContext(genericsInfo, clazz)
        var setAllObjectsToNull = false
        repeat(100) {
            val instanceGeneratedInstanceViaLegalWay =
                generateInstanceViaLegalWay(parameterType!!, gctx)
            println("INSTANCE = $instanceGeneratedInstanceViaLegalWay")
        }
        return null
        if (/*Random.getTrue(25)* ||*/ depth >= DataGeneratorSettings.maxDepthOfGeneration) {
            //generateInstanceViaConstructorInvocation(clazz!!, gctx)?.let { return it }
            setAllObjectsToNull = true
        }
//        val i = generateInstanceViaConstructorInvocation(clazz!!, gctx)
//        println("INTANCE = $i")
//        exitProcess(0)
        val fieldValues = clazz!!.getAllDeclaredFields().map { field ->
            println("F = $field ${field.name}")
            val fieldValue =
                if (Modifier.STATIC.and(field.modifiers) > 0 && Modifier.FINAL.and(field.modifiers) > 0) {
                    field.getFieldValue(null)
                } else {
                    generateFieldValue(field, gctx, setAllObjectsToNull)
                }
            field to fieldValue
        }
        println("TRYING TO ALLOCATE INSTANCE OF $clazz")
        val instance = UNSAFE.allocateInstance(clazz)
        fieldValues.forEach { (field, value) ->
            field.generateInstance(instance, value)
        }
        return instance
    }

    private fun generateInstanceViaLegalWay(type: Type, gctx: GenericsContext): Any? {
        val allStaticFields = ""
        val resolvedStaticMethods =
            type.toClass().declaredMethods.filter { it.hasModifiers(Modifier.STATIC) }
                .map { it to gctx.method(it).resolveReturnType() }
                .filter { it.second == type }
        val resolvedStaticFields =
            type.toClass().declaredFields.filter { it.hasModifiers(Modifier.STATIC) }
                .map { it to gctx.resolveFieldType(it) }
                .filter { it.second == type }
        println()
        exitProcess(0)
        return null
    }

    private fun generateInstanceViaConstructor(clazz: Class<*>, gctx: GenericsContext): Any? {
        val constructor = clazz.declaredConstructors.randomOrNull() ?: return null
        val oldAccessibleFlag = constructor.isAccessible
        constructor.isAccessible = true
        val parameterValues =
            constructor.parameters.map { parameter ->
                val parameterType = gctx.resolveType(parameter.parameterizedType)
                val parameterValue = generateParameterValue(parameter, clazz.name, gctx, false)
                //println("VALUE OF PARAM $parameterType is $parameterValue")
                parameterValue
            }

        return constructor.newInstance(*parameterValues.toTypedArray())
            .also { constructor.isAccessible = oldAccessibleFlag }
    }

    private fun generateParameterValue(
        parameter: Parameter,
        clazzName: String,
        gctx: GenericsContext,
        setAllObjectsToNull: Boolean
    ): Any? =
        generateValueOfType(
            parameter,
            gctx,
            parameter.declaringExecutable.let { it.declaringClass.name + '.' + it.name },
            parameter.annotatedType,
            clazzName,
            ParameterTypeContext.forParameter(parameter),
            setAllObjectsToNull
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
        setAllObjectsToNull: Boolean
    ): Any? {
        var clazz: Class<*>? = null
        val context =
            try {
                val resolvedType =
                    when (fieldOrParameterForGeneration) {
                        is Field -> gctx.resolveFieldType(fieldOrParameterForGeneration)
                            .also { clazz = fieldOrParameterForGeneration.type }
                        is Parameter -> gctx.resolveType(fieldOrParameterForGeneration.parameterizedType)
                            .also { clazz = fieldOrParameterForGeneration.type }
                        else -> return null
                    }
                createParameterTypeContext(
                    name,
                    annotatedType,
                    declaringTypeName,
                    Types.forJavaLangReflectType(resolvedType),
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
        val fieldValue = generator.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
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



