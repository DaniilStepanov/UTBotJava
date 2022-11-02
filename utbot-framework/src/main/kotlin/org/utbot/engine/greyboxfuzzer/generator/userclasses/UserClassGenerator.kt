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
import org.utbot.engine.logger
import org.utbot.framework.plugin.api.UtModel
import org.utbot.framework.plugin.api.util.id
import java.lang.reflect.*

class UserClassGenerator : ComponentizedGenerator<Any>(Any::class.java) {

    var clazz: Class<*>? = null
    var parameterTypeContext: ParameterTypeContext? = null
    var depth = 0
    var generationMethod = GenerationMethod.ANY

    override fun copy(): Generator<Any> {
        return UserClassGenerator().also {
            it.clazz = clazz
            it.depth = depth
            it.parameterTypeContext = parameterTypeContext
        }
    }

    override fun canGenerateForParametersOfTypes(typeParameters: MutableList<TypeParameter<*>>?): Boolean {
        return true
    }

    override fun numberOfNeededComponents(): Int {
        return parameterTypeContext?.resolved?.typeParameters?.size ?: 0
    }

    fun generate(random: SourceOfRandomness?, status: GenerationStatus?, generationMethod: GenerationMethod): UtModel? {
        this.generationMethod = generationMethod
        return generate(random, status)
    }

    private fun generateClass() =
        parameterTypeContext!!.resolved.typeParameters.randomOrNull()?.type?.componentClass?.let {
            UtModelGenerator.utModelConstructor.construct(it, Class::class.java.id)
        }

    private fun generateObject() = GreyBoxFuzzerGenerators.generatorRepository
        .generators
        .toList()
        .flatMap { it.second }
        .filter { !it.hasComponents() }
        .randomOrNull()
        ?.generate(GreyBoxFuzzerGenerators.sourceOfRandomness, GreyBoxFuzzerGenerators.genStatus)

    override fun generate(random: SourceOfRandomness?, status: GenerationStatus?): UtModel? {
        clazz ?: return null
        if (depth >= GreyBoxFuzzerGenerators.maxDepthOfGeneration) return null
        val parameterType = parameterTypeContext!!.resolved
        logger.debug { "Trying to generate $parameterType. Current depth depth: $depth" }
        //TODO! generate inner classes instances
        if (parameterType.toString().substringBefore('<').contains("$") && !clazz!!.hasModifiers(Modifier.STATIC)) {
            return null
        }
        if (parameterType.componentClass.name == Any::class.java.name) return generateObject()
        if (parameterType.componentClass.name == "java.lang.Class") return generateClass()
        val resolvedJavaType = parameterTypeContext!!.generics.resolveType(parameterTypeContext!!.type())
        val gctx = resolvedJavaType.createGenericsContext(clazz!!)
        if (resolvedJavaType.toClass()!!.hasAtLeastOneOfModifiers(Modifier.ABSTRACT, Modifier.INTERFACE)) {
            return InterfaceImplementersGenerator.generateImplementerInstance(
                resolvedJavaType, parameterTypeContext!!, depth
            )
        }
        val typeOfGenerations = when (generationMethod) {
            GenerationMethod.CONSTRUCTOR -> mutableListOf('c')
            GenerationMethod.STATIC -> mutableListOf('s')
            GenerationMethod.STATIC_EXT -> mutableListOf('e')
            else -> mutableListOf('c', 'c', 's', 'e')
        }
        while (true) {
            val randomTypeOfGeneration = typeOfGenerations.randomOrNull() ?: return null
            logger.debug { "Type of generation: $randomTypeOfGeneration" }
            val generatedInstance =
                when (randomTypeOfGeneration) {
                    'c' -> InstancesGenerator.generateInstanceUsingConstructor(
                        resolvedJavaType.toClass()!!,
                        gctx,
                        parameterTypeContext!!.generics,
                        depth
                    )
                    's' -> InstancesGenerator.generateInstanceUsingStatics(
                        Types.forJavaLangReflectType(resolvedJavaType),
                        gctx,
                        parameterTypeContext!!,
                        depth
                    )
                    'e' -> run {
                        val staticGenerators =
                            SootStaticsCollector.getStaticInitializersOf(parameterTypeContext!!.rawClass)
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
                }
            generatedInstance?.let { return it } ?: typeOfGenerations.removeIf { it == randomTypeOfGeneration }
        }
    }
}
