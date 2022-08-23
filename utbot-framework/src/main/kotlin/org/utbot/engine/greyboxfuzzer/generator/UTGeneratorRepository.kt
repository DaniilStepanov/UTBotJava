package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.LambdaGenerator
import com.pholser.junit.quickcheck.internal.generator.MarkerInterfaceGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import org.utbot.engine.greyboxfuzzer.util.toClass

class UTGeneratorRepository(random: SourceOfRandomness) : GeneratorRepository(random) {

    override fun generatorFor(parameter: ParameterTypeContext): Generator<*>? {
        println("TRYING TO GET GENERATOR FOR ${parameter.type()}")
        if (parameter.type()?.toClass()?.name == "com.pholser.junit.quickcheck.internal.Zilch") return null
        val generator = super.generatorFor(parameter)
        if (generator is MarkerInterfaceGenerator<*>) {
            throw IllegalArgumentException(
                "Cannot find generator for " + parameter.name()
                        + " of type " + parameter.type().typeName
            )
        } else if (generator is LambdaGenerator<*, *>) {
            return null
        }
        return generator
    }
}