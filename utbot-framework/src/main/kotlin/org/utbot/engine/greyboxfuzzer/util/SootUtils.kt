package org.utbot.engine.greyboxfuzzer.util

import org.utbot.framework.plugin.api.util.signature
import soot.Hierarchy
import soot.Scene
import soot.SootClass
import soot.SootMethod
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

fun SootClass.getImplementersOfWithChain(hierarchy: Hierarchy): List<List<SootClass>>? {
    this.checkLevel(SootClass.HIERARCHY)
    if (!this.isInterface && !this.isAbstract) {
        throw RuntimeException("interfaced needed; got $this")
    }
    val res = mutableListOf(mutableListOf(this))
    val queue = ArrayDeque<SootClass>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val curSootClass = queue.removeFirst()
        val implementers =
            if (curSootClass.isInterface) {
                hierarchy.getDirectImplementersOf(curSootClass)
                    .filter { it.interfaces.contains(curSootClass) } + hierarchy.getDirectSubinterfacesOf(curSootClass)
            } else {
                hierarchy.getDirectSubclassesOf(curSootClass)
            }
        if (implementers.isEmpty()) continue
        val oldLists = res.removeIfAndReturnRemovedElements { it.last() == curSootClass }
        if (curSootClass.isConcrete) {
            oldLists.forEach { res.add(it.toMutableList()) }
        }
        for (implementer in implementers) {
            queue.add(implementer)
            oldLists.forEach { res.add((it + listOf(implementer)).toMutableList()) }
        }
    }
    return res
}

private fun getSubClasses(sootClass: SootClass, hierarchy: Hierarchy) {
    // hierarchy.getDirectImplementersOf(sootClass).
}

fun SootClass.toJavaClass(): Class<*> =
    try {
        Class.forName(this.name)
    } catch (e: ClassNotFoundException) {
        CustomClassLoader.classLoader.loadClass(this.name)
    }

fun KFunction<*>.toSootMethod(): SootMethod? = this.javaMethod?.toSootMethod()

fun Class<*>.toSootClass() =
    Scene.v().classes.find { it.name == this.name }
fun Method.toSootMethod(): SootMethod? {
    val javaClass = this.declaringClass
    val cl = javaClass.toSootClass() ?: return null
    println("CL = $cl")
    return cl.methods.find {
        val sig = it.bytecodeSignature.drop(1).dropLast(1).substringAfter("${cl.name}: ")
        this.signature == sig
    }
}

fun SootMethod.toJavaMethod(): Method? {
    val javaCl = this.declaringClass.toJavaClass()
    val sootCl = this.declaringClass
    return javaCl.getAllDeclaredMethods().find {
        it.signature == this.bytecodeSignature.drop(1).dropLast(1).substringAfter("${sootCl.name}: ")
    }
}

fun SootClass.getAllAncestors(): List<SootClass> {
    val queue = ArrayDeque<SootClass>()
    val res = mutableSetOf<SootClass>()
    this.superclassOrNull?.let { queue.add(it) }
    queue.addAll(this.interfaces)
    while (queue.isNotEmpty()) {
        val el = queue.removeFirst()
        el.superclassOrNull?.let {
            if (!res.contains(it) && !queue.contains(it)) queue.add(it)
        }
        el.interfaces.map { if (!res.contains(it) && !queue.contains(it)) queue.add(it) }
        res.add(el)
    }
    return res.toList()
}

val SootClass.children
    get() =
        Scene.v().classes.filter { it.getAllAncestors().contains(this) }

val SootClass.superclassOrNull
    get() =
        try {
            superclass
        } catch (e: Exception) {
            null
        }


//fun getImplementersOf(i: SootClass): List<SootClass>? {
//    i.checkLevel(SootClass.HIERARCHY)
//    if (!i.isInterface) {
//        throw RuntimeException("interface needed; got $i")
//    }
//    checkState()
//    val set = ArraySet<SootClass>()
//    for (c in getSubinterfacesOfIncluding(i)) {
//        set.addAll(getDirectImplementersOf(c))
//    }
//    val l = ArrayList<SootClass>()
//    l.addAll(set)
//    return Collections.unmodifiableList(l)
//}