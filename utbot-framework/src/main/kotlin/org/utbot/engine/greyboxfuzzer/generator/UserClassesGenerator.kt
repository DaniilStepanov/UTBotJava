package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.ComponentizedGenerator
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.javaruntype.type.TypeParameter
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.rawType
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.util.GenericsUtils
import ru.vyarus.java.generics.resolver.util.TypeUtils
import soot.Hierarchy
import soot.Scene
import sun.misc.Unsafe
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl
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
    }

    private fun generateClass(): Class<*>? {
        return parameterTypeContext!!.getResolvedType().typeParameters.randomOrNull()?.type?.componentClass
    }

    private fun generateType(): Type {
        return Any::class.java.rawType
    }

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): Any? {
        clazz ?: return null
        if (depth >= DataGeneratorSettings.maxDepthOfGeneration) return null
        val parameterType = parameterTypeContext!!.getResolvedType()
        println("TRYING TO GENERATE $parameterType depth: $depth")
        if (parameterType.componentClass.name == "java.lang.Object") {
            return DataGeneratorSettings.generatorRepository
                .getGenerators()
                .toList()
                .flatMap { it.second }
                .filter { !it.hasComponents() }
                .randomOrNull()
                ?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        }
        if (parameterType.componentClass.name == "java.lang.Class") return generateClass()
        else if (parameterType.componentClass.name == "java.lang.reflect.Type") return generateType()
        val modifiers = parameterType.componentClass.modifiers
        val parameterizedTypeImpl = parameterTypeContext!!.type() as? ParameterizedTypeImpl

        if (parameterizedTypeImpl != null && parameterizedTypeImpl.actualTypeArguments.any { it is WildcardTypeImpl }) {
            for ((ind, typeArg) in parameterizedTypeImpl.actualTypeArguments.withIndex()) {
                if (typeArg is WildcardTypeImpl) {
                    val oldUpperBounds = WildcardTypeImpl::class.java.getDeclaredField("upperBounds")
                    oldUpperBounds.isAccessible = true
                    val newBounds = parameterizedTypeImpl.rawType.typeParameters[ind].bounds
                    oldUpperBounds.set(typeArg, newBounds)
                }
            }
        }
        val resolvedJavaType = parameterTypeContext!!.getGenericContext().resolveType(parameterTypeContext!!.type())
        val gctx = createGenericsContext(resolvedJavaType, clazz!!)
        if (modifiers.and(Modifier.ABSTRACT) > 0 || modifiers.and(Modifier.INTERFACE) > 0) {
            return InterfaceImplementersGenerator.generateImplementerInstance(
                resolvedJavaType, parameterTypeContext!!, depth
            )
        }
        //TODO!! Implement generation for Inner classes
        if (TypeUtils.getOuter(resolvedJavaType) != null) return null
        val inst =
            if (Random.getTrue(50)) {
                InstancesGenerator.generateInstanceViaConstructor(
                    resolvedJavaType.toClass()!!,
                    gctx,
                    depth
                ) ?: InstancesGenerator.generateInstanceWithStatics(
                    Types.forJavaLangReflectType(resolvedJavaType),
                    gctx,
                    parameterTypeContext!!,
                    depth
                )
            } else {
                InstancesGenerator.generateInstanceWithStatics(
                    Types.forJavaLangReflectType(resolvedJavaType),
                    gctx,
                    parameterTypeContext!!,
                    depth
                ) ?: InstancesGenerator.generateInstanceViaConstructor(
                    resolvedJavaType.toClass()!!,
                    gctx,
                    depth
                )
            }
        if (inst == null) {
            val staticGenerators = SootStaticsCollector.getStaticInstancesOf(parameterTypeContext!!.rawClass)
            if (staticGenerators.isNotEmpty()) {
                val randomMethod = staticGenerators.chooseRandomMethodToGenerateInstance()
                if (randomMethod != null) {
                    InstancesGenerator.generateInterfaceInstanceViaStaticCall(randomMethod, depth)?.let { return it }
                }
            }
            return InstancesGenerator.generateInstanceWithUnsafe(resolvedJavaType.toClass()!!)
        }
        return inst
    }
}
