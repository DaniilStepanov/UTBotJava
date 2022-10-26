package org.utbot.engine.greyboxfuzzer.generator

import org.utbot.quickcheck.generator.Generator
import org.utbot.quickcheck.generator.InRange
import org.utbot.quickcheck.generator.Size
import org.utbot.quickcheck.generator.java.lang.ByteGenerator
import org.utbot.quickcheck.generator.java.lang.CharacterGenerator
import org.utbot.quickcheck.generator.java.lang.DoubleGenerator
import org.utbot.quickcheck.generator.java.lang.FloatGenerator
import org.utbot.quickcheck.generator.java.lang.IntegerGenerator
import org.utbot.quickcheck.generator.java.lang.ShortGenerator
import org.utbot.quickcheck.generator.java.util.CollectionGenerator
import org.utbot.quickcheck.generator.java.util.MapGenerator
import org.utbot.quickcheck.internal.generator.ArrayGenerator
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
    val minCollectionSize: Int = 1
    val maxCollectionSize: Int = 5

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
            is MapGenerator -> generator.configure(sizeAnnotationInstance)
            else -> Unit
        }

}