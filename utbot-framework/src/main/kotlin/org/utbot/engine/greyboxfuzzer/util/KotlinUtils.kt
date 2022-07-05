package org.utbot.engine.zestfuzzer.util

import org.utbot.engine.zestfuzzer.util.kcheck.nextInRange
import java.util.*

fun kotlin.random.Random.getTrue(prob: Int) = Random().nextInRange(0, 100) < prob

fun <T> List<T>.sublistBeforeLast(element: T): List<T> =
    this.indexOfLast { it == element }.let { lastIndex ->
        if (lastIndex == -1) this
        else this.subList(0, lastIndex)
    }
