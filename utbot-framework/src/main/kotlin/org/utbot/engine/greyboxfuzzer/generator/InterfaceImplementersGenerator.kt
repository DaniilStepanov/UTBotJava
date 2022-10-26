@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.utbot.engine.greyboxfuzzer.generator

import org.utbot.quickcheck.internal.ParameterTypeContext
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.framework.plugin.api.UtModel
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl
import soot.Hierarchy
import soot.Scene
import java.lang.reflect.Type
import kotlin.random.Random

object InterfaceImplementersGenerator {

    fun generateImplementerInstance(
        resolvedType: Type,
        parameterTypeContext: ParameterTypeContext,
        depth: Int,
        isRecursiveUnsafe: Boolean
    ): UtModel? {
        //val sootMethod = SootStaticsCollector.getStaticInstancesOf(clazz!!).first()
        val staticGenerators = SootStaticsCollector.getStaticInstancesOf(parameterTypeContext.rawClass!!)
        if (staticGenerators.isNotEmpty() && (Random.nextBoolean() || resolvedType.toClass()
                ?.isFunctionalInterface() == true)
        ) {
            val randomMethod = staticGenerators.chooseRandomMethodToGenerateInstance()
            println("TRYING TO GENERATE class using $randomMethod")
            if (randomMethod != null) {
                InstancesGenerator.generateInterfaceInstanceViaStaticCall(randomMethod, parameterTypeContext, depth)
                    ?.let { return it }
            }
        }
//        if (resolvedType.toClass()?.isFunctionalInterface() == true) {
//            InstancesGenerator.generateFunctionalInterface(parameterTypeContext, depth)
//        }
        val sootClass = Scene.v().classes.find { it.name == parameterTypeContext.rawClass.name } ?: return null
        val hierarchy = Hierarchy()
        val implementers =
            sootClass.getImplementersOfWithChain(hierarchy)
                ?.filter { it.all { !it.toString().contains("$") } }
                ?.filter { it.last().isConcrete }
                ?.filter {
                    val allocatedInstance =
                        try {
                            UserClassesGenerator.UNSAFE.allocateInstance(it.last().toJavaClass())
                        } catch (e: Throwable) {
                            null
                        }
                    allocatedInstance != null
                }
        val randomImplementersChain =
            if (Random.getTrue(75)) {
                implementers?.shuffled()?.minByOrNull { it.size }?.drop(1)
            } else {
                implementers?.randomOrNull()?.drop(1)
            } ?: return null
        val generics = mutableListOf<Pair<Type, MutableList<Type>>>()
        var prevImplementer = sootClass.toJavaClass()
        (resolvedType as? ParameterizedTypeImpl)?.actualTypeArguments?.forEachIndexed { index, typeVariable ->
            if (prevImplementer.toClass() != null) {
                generics.add(typeVariable to mutableListOf(prevImplementer.toClass()!!.typeParameters[index]))
            }
        }
        println("IMPLEMENTER = ${prevImplementer.name}")
        for (implementer in randomImplementersChain) {
            val javaImplementer = implementer.toJavaClass()
            val extendType = javaImplementer.let { it.genericInterfaces + it.genericSuperclass }
                .find { it.toClass() == prevImplementer }
            val tp = prevImplementer.typeParameters
            prevImplementer = javaImplementer
            if (tp.isEmpty()) continue
            val newTp =
                (extendType as? sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl)?.actualTypeArguments
                    ?: return null
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
        g.forEach { gm[it.key] = it.value!! }
        val m = mutableMapOf(prevImplementer to gm)
        val genericsContext = GenericsContext(GenericsInfo(prevImplementer, m), prevImplementer)
        val parameterizedJavaType = try {
            Types.forJavaLangReflectType(parameterizedType)
        } catch (e: Exception) {
            return null
        }
        println("TRYING TO GENERATE instance of ${prevImplementer.name}")
        val typeOfGenerations =
            if (isRecursiveUnsafe) mutableListOf('u')
            else mutableListOf('c', 'c', 's')
        while (true) {
            val randomTypeOfGeneration = typeOfGenerations.randomOrNull() ?: break
            println("TYPE OF GENERATION $randomTypeOfGeneration")
            val generatedInstance =
                when (randomTypeOfGeneration) {
                    'c' -> InstancesGenerator.generateInstanceViaConstructor(
                        prevImplementer,
                        genericsContext,
                        parameterTypeContext.getGenericContext(),
                        depth
                    )
                    's' -> InstancesGenerator.generateInstanceWithStatics(
                        parameterizedJavaType,
                        genericsContext,
                        parameterTypeContext,
                        depth
                    )
//                    else -> InstancesGenerator.generateInstanceWithUnsafe(
//                        prevImplementer,
//                        depth,
//                        isRecursiveUnsafe,
//                        genericsContext
//                    )
                    else -> null
//                        if (isRecursiveUnsafe) {
//                        InstancesGenerator.generateInstanceWithUnsafe(prevImplementer, depth, true, genericsContext)
//                    } else {
//                        InstancesGenerator.generateInstanceWithUnsafe(prevImplementer, depth, false, genericsContext)
//                    }
                }
            generatedInstance?.let { return it } ?: typeOfGenerations.removeIf { it == randomTypeOfGeneration }
        }
        if (staticGenerators.isNotEmpty()) {
            val randomMethod = staticGenerators.chooseRandomMethodToGenerateInstance()
            println("TRYING TO GENERATE class using $randomMethod")
            if (randomMethod != null) {
                InstancesGenerator.generateInterfaceInstanceViaStaticCall(randomMethod, parameterTypeContext, depth)
                    ?.let { return it }
            }
        }
        return null
    }

}