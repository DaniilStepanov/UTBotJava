package org.utbot.engine.greyboxfuzzer.util

import soot.Scene
import java.lang.reflect.Method

object SootStaticsCollector {

    private val classToStaticInstance = mutableMapOf<Class<*>, List<Method>>()

    fun getStaticInstancesOf(clazz: Class<*>): List<Method> {
        try {
            if (classToStaticInstance.contains(clazz)) return classToStaticInstance[clazz]!!
            val classes = Scene.v().classes.filter { !it.name.contains("$") }
            val sootMethods = classes.flatMap {
                it.methods
                    .asSequence()
                    .filter { it.isStatic && it.returnType.toString() == clazz.name }
                    .filter { it.parameterTypes.all { !it.toString().contains(clazz.name) } }
                    .filter { !it.toString().contains('$') }
                    .toList()
            }
            val javaMethods = sootMethods.flatMap { sootMethod ->
                sootMethod.declaringClass.toJavaClass().methods.filter {
                    it.isStatic() && it.name == sootMethod.name && it.parameterCount == sootMethod.parameterCount
                }
            }
            classToStaticInstance[clazz] = javaMethods
            return javaMethods
        } catch (e: Throwable) {
            return emptyList()
        }
    }

}