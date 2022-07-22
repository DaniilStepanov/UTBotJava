package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.InRange
import com.pholser.junit.quickcheck.generator.Size
import com.pholser.junit.quickcheck.generator.java.lang.ByteGenerator
import com.pholser.junit.quickcheck.generator.java.lang.CharacterGenerator
import com.pholser.junit.quickcheck.generator.java.lang.DoubleGenerator
import com.pholser.junit.quickcheck.generator.java.lang.FloatGenerator
import com.pholser.junit.quickcheck.generator.java.lang.IntegerGenerator
import com.pholser.junit.quickcheck.generator.java.lang.ShortGenerator
import com.pholser.junit.quickcheck.generator.java.util.CollectionGenerator
import com.pholser.junit.quickcheck.internal.generator.ArrayGenerator
import org.utbot.engine.greyboxfuzzer.util.getTrue
import org.utbot.engine.greyboxfuzzer.util.setFieldValue
import kotlin.random.Random

object GeneratorConfigurator {
    private const val minByte: Byte = -100
    private const val maxByte: Byte = 100
    private val minShort: Short = -100
    private val maxShort: Short = 100
    private val minChar: Char = Character.MIN_VALUE
    private val maxChar: Char = Character.MAX_VALUE
    private val minInt: Int = -100
    private val maxInt: Int = 100
    private val minLong: Long = -100
    private val maxLong: Long = 100
    private val minFloat: Float = -100.0f
    private val maxFloat: Float = 100.0f
    private val minDouble: Double = -100.0
    private val maxDouble: Double = 100.0
    private val minStringLength: Int = 1
    private val maxStringLength: Int = 10
    private val minCollectionSize: Int = 1
    private val maxCollectionSize: Int = 10

    private val sizeAnnotationInstance: Size
    private val inRangeAnnotationInstance: InRange

    init {
        val sizeAnnotationParams =
            Size::class.constructors.first().parameters.associateWith {
                if (it.name == "max") maxCollectionSize else minCollectionSize
            }
        sizeAnnotationInstance = Size::class.constructors.first().callBy(sizeAnnotationParams)
        val inRangeAnnotationParams =
            InRange::class.constructors.first().parameters.associateWith {
                when (it.name) {
                    "minByte" -> minByte
                    "maxByte" -> maxByte
                    "minShort" -> minShort
                    "maxShort" -> maxShort
                    "minChar" -> minChar
                    "maxChar" -> maxChar
                    "minInt" -> minInt
                    "maxInt" -> maxInt
                    "minLong" -> minLong
                    "maxLong" -> maxLong
                    "minFloat" -> minFloat
                    "maxFloat" -> maxFloat
                    "minDouble" -> minDouble
                    "maxDouble" -> maxDouble
                    "max" -> ""
                    "min" -> ""
                    "format" -> ""
                    else -> ""
                }
            }
        inRangeAnnotationInstance = InRange::class.constructors.first().callBy(inRangeAnnotationParams)
    }

    fun configureGenerator(generator: Generator<*>, prob: Int) {
        (listOf(generator) + generator.getAllComponents()).forEach {
            if (Random.getTrue(prob)) handleGenerator(it)
        }
    }

    private fun handleGenerator(generator: Generator<*>) =
        when (generator) {
            is IntegerGenerator -> generator.configure(inRangeAnnotationInstance)
            is ByteGenerator -> generator.configure(inRangeAnnotationInstance)
            is ShortGenerator -> generator.configure(inRangeAnnotationInstance)
            is CharacterGenerator -> generator.configure(inRangeAnnotationInstance)
            is FloatGenerator -> generator.configure(inRangeAnnotationInstance)
            is DoubleGenerator -> generator.configure(inRangeAnnotationInstance)
            is CollectionGenerator<*> -> generator.configure(sizeAnnotationInstance)
            is ArrayGenerator -> generator.configure(sizeAnnotationInstance)
            else -> Unit
        }

}