package org.utbot.engine.greyboxfuzzer.util

import org.utbot.engine.greyboxfuzzer.util.kcheck.nextInRange
import java.util.*

fun kotlin.random.Random.getTrue(prob: Int) = Random().nextInRange(0, 100) < prob

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
