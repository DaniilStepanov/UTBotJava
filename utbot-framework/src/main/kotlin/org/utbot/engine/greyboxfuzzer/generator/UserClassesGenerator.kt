@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.utbot.engine.greyboxfuzzer.generator

import org.utbot.quickcheck.generator.ComponentizedGenerator
import org.utbot.quickcheck.generator.GenerationStatus
import org.utbot.quickcheck.generator.Generator
import org.utbot.quickcheck.internal.ParameterTypeContext
import org.utbot.quickcheck.random.SourceOfRandomness
import org.javaruntype.type.TypeParameter
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.rawType
import org.utbot.framework.plugin.api.UtModel
import sun.misc.Unsafe
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl
import java.lang.reflect.*

class UserClassesGenerator : ComponentizedGenerator<Any>(Any::class.java) {

    var clazz: Class<*>? = null
    var parameterTypeContext: ParameterTypeContext? = null
    var depth = 0
    var generationMethod = GenerationMethod.ANY

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

    fun generate(random: SourceOfRandomness?, status: GenerationStatus?, generationMethod: GenerationMethod): UtModel? {
        this.generationMethod = generationMethod
        return generate(random, status)
    }

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): UtModel? {
        clazz ?: return null
        if (depth >= DataGeneratorSettings.maxDepthOfGeneration) return null
        val parameterType = parameterTypeContext!!.getResolvedType()
        println("TRYING TO GENERATE $parameterType depth: $depth")
        //TODO! generate inner classes instances
        //if (TypeUtils.getOuter(resolvedJavaType) != null) return null
        if (parameterType.toString().substringBefore('<')
                .contains("$") && !clazz!!.hasModifiers(Modifier.STATIC)
        ) return null
        if (parameterType.componentClass.name == "java.lang.Object") {
            return DataGeneratorSettings.generatorRepository
                .getGenerators()
                .toList()
                .flatMap { it.second }
                .filter { !it.hasComponents() }
                .randomOrNull()
                ?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
        }
//        if (parameterType.componentClass.name == "java.lang.Class") return generateClass()
//        else if (parameterType.componentClass.name == "java.lang.reflect.Type") return generateType()
        val modifiers = parameterType.componentClass.modifiers
        val resolvedJavaType = parameterTypeContext!!.getGenericContext().resolveType(parameterTypeContext!!.type())
        val gctx = createGenericsContext(resolvedJavaType, clazz!!)
        if (modifiers.and(Modifier.ABSTRACT) > 0 || modifiers.and(Modifier.INTERFACE) > 0) {
            return InterfaceImplementersGenerator.generateImplementerInstance(
                resolvedJavaType, parameterTypeContext!!, depth, generationMethod == GenerationMethod.UNSAFE
            )
        }
        val typeOfGenerations = when (generationMethod) {
            GenerationMethod.CONSTRUCTOR -> mutableListOf('c')
            GenerationMethod.STATIC -> mutableListOf('s')
            GenerationMethod.STATIC_EXT -> mutableListOf('e')
            GenerationMethod.UNSAFE -> mutableListOf('u')
            else -> mutableListOf('c', 'c', 's', 'e')
        }
        while (true) {
            val randomTypeOfGeneration = typeOfGenerations.randomOrNull() ?: return null
            val generatedInstance =
                when (randomTypeOfGeneration) {
                    'c' -> InstancesGenerator.generateInstanceViaConstructor(
                        resolvedJavaType.toClass()!!,
                        gctx,
                        parameterTypeContext!!.getGenericContext(),
                        depth
                    )
                    's' -> InstancesGenerator.generateInstanceWithStatics(
                        Types.forJavaLangReflectType(resolvedJavaType),
                        gctx,
                        parameterTypeContext!!,
                        depth
                    )
                    'e' -> run {
                        val staticGenerators =
                            SootStaticsCollector.getStaticInstancesOf(parameterTypeContext!!.rawClass)
                        if (staticGenerators.isNotEmpty()) {
                            val randomMethod = staticGenerators.chooseRandomMethodToGenerateInstance()
                            if (randomMethod != null) {
                                InstancesGenerator.generateInterfaceInstanceViaStaticCall(
                                    randomMethod,
                                    parameterTypeContext!!,
                                    depth
                                )
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
//                    else -> InstancesGenerator.generateInstanceWithUnsafe(
//                        resolvedJavaType.toClass()!!,
//                        depth,
//                        generationMethod == GenerationMethod.UNSAFE,
//                        gctx
//                    )
                }
            generatedInstance?.let { return it } ?: typeOfGenerations.removeIf { it == randomTypeOfGeneration }
        }
    }
}
