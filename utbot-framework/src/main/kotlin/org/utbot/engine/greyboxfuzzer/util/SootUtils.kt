package org.utbot.engine.greyboxfuzzer.util

import soot.Hierarchy
import soot.SootClass
import soot.util.ArraySet
import java.util.*
import kotlin.collections.ArrayDeque

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