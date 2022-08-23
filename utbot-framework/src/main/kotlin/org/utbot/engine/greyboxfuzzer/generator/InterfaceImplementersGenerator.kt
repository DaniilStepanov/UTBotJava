package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import org.javaruntype.exceptions.TypeValidationException
import org.javaruntype.type.Types
import org.utbot.engine.greyboxfuzzer.util.*
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfo
import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl
import soot.Hierarchy
import soot.Scene
import soot.SootMethod
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.random.Random

object InterfaceImplementersGenerator {

    fun generateImplementerInstance(resolvedType: Type, parameterTypeContext: ParameterTypeContext, depth: Int): Any? {
        //val sootMethod = SootStaticsCollector.getStaticInstancesOf(clazz!!).first()
        val staticGenerators = SootStaticsCollector.getStaticInstancesOf(parameterTypeContext.rawClass!!)
        if (staticGenerators.isNotEmpty() /*&& Random.nextBoolean()*/) {
            val randomMethod = staticGenerators.chooseRandomMethodToGenerateInstance()
            println("TRYING TO GENERATE class using $randomMethod")
            if (randomMethod != null) {
                InstancesGenerator.generateInterfaceInstanceViaStaticCall(randomMethod!!, depth)?.let { return it }
            }
        }
        val sootClass = Scene.v().classes.find { it.name == parameterTypeContext.rawClass.name } ?: return null
        val hierarchy = Hierarchy()
        val implementers =
            sootClass.getImplementersOfWithChain(hierarchy)
                ?.filter { it.all { !it.toString().contains("$") } }
                ?.filter { it.last().isConcrete }
        val randomImplementersChain = implementers?.randomOrNull()?.drop(1) ?: return null
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
            val newTp = (extendType as sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl).actualTypeArguments
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
        return if (Random.nextBoolean()) {
            InstancesGenerator.generateInstanceViaConstructor(prevImplementer, genericsContext, depth)
                ?: InstancesGenerator.generateInstanceWithStatics(
                    parameterizedJavaType,
                    genericsContext,
                    parameterTypeContext,
                    depth
                )
        } else {
            InstancesGenerator.generateInstanceWithStatics(
                parameterizedJavaType,
                genericsContext,
                parameterTypeContext,
                depth
            )
                ?: InstancesGenerator.generateInstanceViaConstructor(prevImplementer, genericsContext, depth)
        } ?: InstancesGenerator.generateInstanceWithUnsafe(prevImplementer)
    }

}