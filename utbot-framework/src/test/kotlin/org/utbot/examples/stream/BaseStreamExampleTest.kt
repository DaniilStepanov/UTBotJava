package org.utbot.examples.stream

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.utbot.examples.AbstractTestCaseGeneratorTest
import org.utbot.examples.DoNotCalculate
import org.utbot.examples.StaticsType
import org.utbot.examples.eq
import org.utbot.examples.ignoreExecutionsNumber
import org.utbot.examples.isException
import org.utbot.examples.withoutConcrete
import org.utbot.framework.codegen.CodeGeneration
import org.utbot.framework.plugin.api.CodegenLanguage
import java.util.Optional
import java.util.stream.Stream
import kotlin.streams.toList

// TODO 1 instruction is always uncovered https://github.com/UnitTestBot/UTBotJava/issues/193
// TODO failed Kotlin compilation (generics) JIRA:1332
class BaseStreamExampleTest : AbstractTestCaseGeneratorTest(
    testClass = BaseStreamExample::class,
    testCodeGeneration = true,
    languagePipelines = listOf(
        CodeGenerationLanguageLastStage(CodegenLanguage.JAVA),
        CodeGenerationLanguageLastStage(CodegenLanguage.KOTLIN, CodeGeneration)
    )
) {
    @Test
    fun testReturningStreamExample() {
        withoutConcrete {
            check(
                BaseStreamExample::returningStreamExample,
                ignoreExecutionsNumber,
                // NOTE: the order of the matchers is important because Stream could be used only once
                { c, r -> c.isNotEmpty() && c == r!!.toList() },
                { c, r -> c.isEmpty() && c == r!!.toList() },
                coverage = DoNotCalculate
            )
        }
    }

    @Test
    fun testFilterExample() {
        check(
            BaseStreamExample::filterExample,
            ignoreExecutionsNumber,
            { c, r -> null !in c && r == false },
            { c, r -> null in c && r == true },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testMapExample() {
        check(
            BaseStreamExample::mapExample,
            ignoreExecutionsNumber,
            { c, r -> r.contentEquals(c.map { it * 2 }.toTypedArray()) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testDistinctExample() {
        check(
            BaseStreamExample::distinctExample,
            ignoreExecutionsNumber,
            { c, r -> c == c.distinct() && r == false },
            { c, r -> c != c.distinct() && r == true },
            coverage = DoNotCalculate
        )
    }

    @Test
    @Disabled("TODO too long, improve performance")
    fun testSortedExample() {
        check(
            BaseStreamExample::sortedExample,
            ignoreExecutionsNumber,
            { c, r -> c.last() < c.first() && r!!.asSequence().isSorted() },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testPeekExample() {
        checkThisAndStaticsAfter(
            BaseStreamExample::peekExample,
            ignoreExecutionsNumber,
            *streamConsumerStaticsMatchers,
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testLimitExample() {
        check(
            BaseStreamExample::limitExample,
            ignoreExecutionsNumber,
            { c, r -> c.size <= 5 && c.toTypedArray().contentEquals(r) },
            { c, r -> c.size > 5 && c.take(5).toTypedArray().contentEquals(r) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testSkipExample() {
        check(
            BaseStreamExample::skipExample,
            ignoreExecutionsNumber,
            { c, r -> c.size > 5 && c.drop(5).toTypedArray().contentEquals(r) },
            { c, r -> c.size <= 5 && r!!.isEmpty() },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testForEachExample() {
        checkThisAndStaticsAfter(
            BaseStreamExample::forEachExample,
            ignoreExecutionsNumber,
            *streamConsumerStaticsMatchers,
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testToArrayExample() {
        check(
            BaseStreamExample::toArrayExample,
            eq(2),
            { c, r -> c.toTypedArray().contentEquals(r) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testReduceExample() {
        check(
            BaseStreamExample::reduceExample,
            ignoreExecutionsNumber,
            { c, r -> c.sum() + 42 == r },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkWithException(
            BaseStreamExample::optionalReduceExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { c, r -> c.isNotEmpty() && c.single() == null && r.isException<NullPointerException>() },
            { c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.sum()) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testComplexReduceExample() {
        check(
            BaseStreamExample::complexReduceExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && c.sumOf { it.toDouble() } + 42.0 == r },
            { c: List<Int?>, r -> c.isNotEmpty() && c.sumOf { it?.toDouble() ?: 0.0 } + 42.0 == r },
            coverage = DoNotCalculate
        )
    }

    @Test
    @Disabled("TODO 0 executions")
    fun testCollectorExample() {
        check(
            BaseStreamExample::collectorExample,
            ignoreExecutionsNumber,
            { c, r -> c.toSet() == r },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testCollectExample() {
        check(
            BaseStreamExample::collectExample,
            ignoreExecutionsNumber,
            { c, r -> c.sum() == r },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testMinExample() {
        checkWithException(
            BaseStreamExample::minExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.minOrNull()!!) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testMaxExample() {
        checkWithException(
            BaseStreamExample::maxExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.maxOrNull()!!) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testCountExample() {
        check(
            BaseStreamExample::countExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r == 0L },
            { c, r -> c.isNotEmpty() && c.size.toLong() == r },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testAnyMatchExample() {
        check(
            BaseStreamExample::anyMatchExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r == false },
            { c, r -> c.isNotEmpty() && c.all { it == null } && r == true },
            { c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == true },
            { c, r -> c.isNotEmpty() && c.none { it == null } && r == false },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testAllMatchExample() {
        check(
            BaseStreamExample::allMatchExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r == true },
            { c, r -> c.isNotEmpty() && c.all { it == null } && r == true },
            { c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == false },
            { c, r -> c.isNotEmpty() && c.none { it == null } && r == false },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testNoneMatchExample() {
        check(
            BaseStreamExample::noneMatchExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r == true },
            { c, r -> c.isNotEmpty() && c.all { it == null } && r == false },
            { c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == false },
            { c, r -> c.isNotEmpty() && c.none { it == null } && r == true },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testFindFirstExample() {
        checkWithException(
            BaseStreamExample::findFirstExample,
            ignoreExecutionsNumber,
            { c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { c: List<Int?>, r -> c.isNotEmpty() && c.first() == null && r.isException<NullPointerException>() },
            { c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of(c.first()) },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testIteratorExample() {
        checkWithException(
            BaseStreamExample::iteratorSumExample,
            ignoreExecutionsNumber,
            { c, r -> null in c && r.isException<NullPointerException>() },
            { c, r -> null !in c && r.getOrThrow() == c.sum() },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testStreamOfExample() {
        withoutConcrete {
            check(
                BaseStreamExample::streamOfExample,
                ignoreExecutionsNumber,
                // NOTE: the order of the matchers is important because Stream could be used only once
                { c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
                { c, r -> c.isEmpty() && Stream.empty<Int>().toArray().contentEquals(r!!.toArray()) },
                coverage = DoNotCalculate
            )
        }
    }

    @Test
    fun testClosedStreamExample() {
        checkWithException(
            BaseStreamExample::closedStreamExample,
            eq(1),
            { _, r -> r.isException<IllegalStateException>() },
            coverage = DoNotCalculate
        )
    }

    @Test
    fun testGenerateExample() {
        check(
            BaseStreamExample::generateExample,
            eq(1),
            { r -> r!!.contentEquals(Array(10) { 42 }) }
        )
    }

    @Test
    fun testIterateExample() {
        check(
            BaseStreamExample::iterateExample,
            eq(1),
            { r -> r!!.contentEquals(Array(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testConcatExample() {
        check(
            BaseStreamExample::concatExample,
            eq(1),
            { r -> r!!.contentEquals(Array(10) { 42 } + Array(10) { i -> 42 + i }) }
        )
    }

    @Test
    @Disabled("TODO too long, improve performance")
    fun testComplexExample() {
        check(
            BaseStreamExample::complexExample,
            ignoreExecutionsNumber,
            { c, r -> r == c.filter { it != null && it > 0 && it < 125 }.map { it * 2 }.distinct().count().toLong() },
            coverage = DoNotCalculate
        )
    }

    private val streamConsumerStaticsMatchers = arrayOf(
        // one of the matchers below is minimized
        // { _: BaseStreamExample, c: List<Int?>, _: StaticsType, _: Int? -> null in c },
        { _: BaseStreamExample, c: List<Int?>, statics: StaticsType, r: Int? ->
            val x = statics[statics.keys.single()]!!.value as Int

            r!! + c.sumOf { it ?: 0 } == x
        }
    )
}

private fun <E : Comparable<E>> Sequence<E>.isSorted(): Boolean = zipWithNext { a, b -> a <= b }.all { it }
