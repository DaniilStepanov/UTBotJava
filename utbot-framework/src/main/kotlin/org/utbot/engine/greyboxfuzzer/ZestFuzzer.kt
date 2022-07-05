package org.utbot.engine.zestfuzzer

import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.NonTrackingGenerationStatus
import org.utbot.engine.executeConcretely
import org.utbot.engine.zestfuzzer.generator.*
import org.utbot.engine.zestfuzzer.mutator.ObjectMerger
import org.utbot.engine.zestfuzzer.util.getAllDeclaredFields
import org.utbot.example.Graph
import org.utbot.external.api.UtModelFactory
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.instrumentation.ConcreteExecutor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Parameter
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess

class ZestFuzzer(
    private val concreteExecutor: ConcreteExecutor<UtConcreteExecutionResult, UtExecutionInstrumentation>,
    private val methodUnderTest: UtMethod<*>,
    private val instrumentation: List<UtInstrumentation>,
    private val thisInstance: UtModel?,
) {

    /**
     * Mean number of mutations to perform in each round.
     */
    private val MEAN_MUTATION_COUNT = 8.0

    /**
     * Mean number of contiguous bytes to mutate in each mutation.
     */
    private val MEAN_MUTATION_SIZE = 4.0 // Bytes

    //private val sourceOfRandomness = SourceOfRandomness(Random(42))

    //private val generatorRepository = GeneratorRepository(sourceOfRandomness).register(ServiceLoaderGeneratorSource())

    //private val genStatus = NonTrackingGenerationStatus(DataGeneratorSettings.sourceOfRandomness)

    suspend fun fuzz(): Sequence<List<UtModel>> {
        val kfunction = methodUnderTest.callable as KFunction<*>
        val method = kfunction.javaMethod!!
        var maxCoverage = 0
        repeat(1000) {
            println("EXECUTION NUMBER $it")
            val generatedParameters = method.parameters.map { parameter ->
                DataGenerator.generate(
                    parameter,
                    DataGeneratorSettings.sourceOfRandomness,
                    DataGeneratorSettings.genStatus
                )
            }
            val generatedParameterAsUtModel = generatedParameters.map { UtPrimitiveModel(it.value) }
            val initialEnvironmentModels = EnvironmentModels(thisInstance, generatedParameterAsUtModel, mapOf())
            val executionResult =
                concreteExecutor.executeConcretely(methodUnderTest, initialEnvironmentModels, listOf())
            println("EXEC RES = ${executionResult.result}")
            println("COVERAGE = ${executionResult.coverage.coveredInstructions.size}")
            if (executionResult.coverage.coveredInstructions.size > maxCoverage) {
                maxCoverage = executionResult.coverage.coveredInstructions.size
            }
            println("--------------------------------")
        }
        println("MAX COVERAGE = $maxCoverage")
//        val param: Parameter = method.parameters.first()
//        val generatorRepository = DataGeneratorSettings.generatorRepository
//        val generator = DataGenerator(generatorRepository)
//        repeat(100) {
//            val instance1 =
//                generator.generate(param, DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
//            println("INST = $instance1")
//        }
//        val instance2 = generator.generate(param, DataGeneratorSettings.sourceOfRandomness, genStatus)
//        ObjectMerger().mergeObjects(instance1, instance2)
//        println()
        exitProcess(0)
//        val generatorsField = generatorRepository.javaClass.getAllDeclaredFields().find { it.name == "generators" }!!
//        generatorsField.isAccessible = true
//        val generators: MutableMap<Class<*>, Set<Generator<*>>> =
//            (generatorsField.get(generatorRepository) as Map<Class<*>, Set<Generator<*>>>).toMutableMap()

//        generatorRepository.register(UserClassesGenerator.generatorInstance)
//        val instance = Reflection.instantiate(UserClassesGenerator::class.java) as Generator<*>
//        println("IN = $instance")
////        exitProcess(0)

        //val generator =
        //val instance = generator.generate(DataGeneratorSettings.sourceOfRandomness, genStatus)
//        println("INSTANCE = $instance")
//        exitProcess(0)
//        CurrentClassForGeneration.currentClass = Graph::class.java
//        val generator = generatorRepository.produceGenerator(ParameterTypeContext.forClass(CurrentClassForGeneration::class.java))
//        println("GEN = $generator")
//        println("GENERATED = ${generator.generate(DataGeneratorSettings.sourceOfRandomness, genStatus)}")
//        println("INSTANCE = ${CurrentClassForGeneration.instance}")
//        exitProcess(0)
//        exitProcess(0)


//        val paramValue =
//            DataGenerator(generatorRepository).generate(param, DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)
//        println("PARAM VALUE = $paramValue")
        //val parameter = UniversalGenerator(method.parameters.first().type).generate()
        //println("P = $parameter")

//        repeat(1000) {
//
//
//            val initialEnvironmentModels = EnvironmentModels(thisInstance, generatedParams, mapOf())
//            val executionResult =
//                concreteExecutor.executeConcretely(methodUnderTest, initialEnvironmentModels, listOf())
////            println("EXEC RES = ${executionResult.result}")
////            println("COVERAGE = ${executionResult.coverage.coveredInstructions.size}")
////            println("--------------------------------")
//        }
        exitProcess(0)
        return sequenceOf()
    }


    private fun mutateInput(oldData: Any, sourceOfRandomness: SourceOfRandomness): Any {
        val castedData = oldData as LongArray
        print("BEFORE = ")
        castedData.forEach { print("$it ") }
        println()
        // Clone this input to create initial version of new child
        //val newInput = LinearInput(this)
        val bos = ByteArrayOutputStream();
        val oos = ObjectOutputStream(bos);
        oos.writeObject(oldData);
        oos.flush();
        val data = bos.toByteArray()
        val random = Random()//sourceOfRandomness.toJDKRandom()

        // Stack a bunch of mutations
        val numMutations = 3//ZestGuidance.Input.sampleGeometric(random, MEAN_MUTATION_COUNT)
        println("mutations = $numMutations")
        //newInput.desc += ",havoc:$numMutations"
        val setToZero = random.nextDouble() < 0.1 // one out of 10 times
        for (mutation in 1..numMutations) {

            // Select a random offset and size
            val offset = random.nextInt(data.size)
            val mutationSize = ZestGuidance.Input.sampleGeometric(random, MEAN_MUTATION_SIZE)

            // desc += String.format(":%d@%d", mutationSize, idx);

            // Mutate a contiguous set of bytes from offset
            for (i in offset until offset + mutationSize) {
                // Don't go past end of list
                if (i >= data.size) {
                    break
                }

                // Otherwise, apply a random mutation
                val mutatedValue = if (setToZero) 0 else random.nextInt(256)
                data[i] = mutatedValue.toByte()
            }
        }
        val `in` = ByteArrayInputStream(data)
        val `is` = ObjectInputStream(`in`)
        val afterMutationData = `is`.readObject() as LongArray
        print("AFTER = ")
        afterMutationData.forEach { print("$it ") }
        println()
        return data
    }
}