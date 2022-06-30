package org.utbot.engine.zestfuzzer.generator

import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.NonTrackingGenerationStatus
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.UtModel
import java.lang.reflect.Parameter
import java.util.*

class ParametersRepresentation(private val parameters: Array<Parameter>) {
    private val ffields = mutableListOf<FField>()

    private fun getUtModelParams(): List<UtModel> = TODO()



    init {
//        //Initial generation of parameters
//        regenerateParameters()
    }

//    fun getParametersValuesAsUtModels(): List<UtModel> = ffields.map { modelConstructor.construct(it.value, it.classId) }
//
//    fun regenerateParameters() {
//        ffields.clear()
//        for (param in parameters) {
//            val generator = getOrProduceGenerator(param, generatorRepository)
//            val generatedValue = generator.generate(randomness, genStatus)
//            ffields.add(FField(param, generatedValue, generator, classIdForType(param.type), false))
//        }
//    }



}