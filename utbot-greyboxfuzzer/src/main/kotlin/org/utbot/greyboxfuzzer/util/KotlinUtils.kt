package org.utbot.greyboxfuzzer.util

import org.utbot.greyboxfuzzer.util.kcheck.nextInRange
import java.util.*

fun kotlin.random.Random.getTrue(prob: Int) = Random().nextInt(101) < prob

fun <T> List<T>.sublistBeforeLast(element: T): List<T> =
    this.indexOfLast { it == element }.let { lastIndex ->
        if (lastIndex == -1) this
        else this.subList(0, lastIndex)
    }
fun <T> MutableList<T>.removeIfAndReturnRemovedElements(cond: (T) -> Boolean): List<T> {
    val res = mutableListOf<T>()
    val iterator = this.iterator()
    while (iterator.hasNext()) {
        val element = iterator.next()
        if (cond.invoke(element)) {
            res.add(element)
            iterator.remove()
        }
    }
    return res
}

fun String.removeBetweenAll(startChar: Char, endChar: Char): String {
    val resultingString = StringBuilder()
    var flToRemove = false
    for (ch in this) {
        if (ch == startChar) {
            flToRemove = true
            continue
        } else if (ch == endChar) {
            flToRemove = false
            continue
        }
        if (!flToRemove) {
            resultingString.append(ch)
        }
    }
    return resultingString.toString()
}

fun <T, R : Comparable<R>> List<T>.filterDuplicatesBy(f: (T) -> R): List<T> {
    val list1 = this.zip(this.map(f))
    val res = mutableListOf<Pair<T, R>>()
    for (i in 0 until size) {
        val item = list1[i].second
        if (res.all { it.second != item }) res.add(list1[i])
    }
    return res.map { it.first }
}