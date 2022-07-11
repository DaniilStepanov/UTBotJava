package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.NonTrackingGenerationStatus
import java.util.*

object DataGeneratorSettings {

    val sourceOfRandomness = SourceOfRandomness(Random(42))
    val generatorRepository = GeneratorRepository(sourceOfRandomness).register(ServiceLoaderGeneratorSource())
    val genStatus = NonTrackingGenerationStatus(sourceOfRandomness)
    const val maxDepthOfGeneration = 5

}