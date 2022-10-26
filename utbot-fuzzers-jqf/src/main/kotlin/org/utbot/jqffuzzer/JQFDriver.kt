package org.utbot.jqffuzzer

import org.utbot.quickcheck.internal.ParameterTypeContext
import org.utbot.quickcheck.internal.generator.GeneratorRepository
import org.utbot.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import org.utbot.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestDriver
import edu.berkeley.cs.jqf.fuzz.guidance.StreamBackedRandom
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.FastSourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.NonTrackingGenerationStatus
import edu.berkeley.cs.jqf.fuzz.repro.ReproDriver
import org.utbot.framework.plugin.api.UtMethod
import org.utbot.framework.plugin.api.UtModel
import org.utbot.fuzzer.FuzzedMethodDescription
import org.utbot.instrumentation.ConcreteExecutor
import java.io.File
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess

object JQFDriver {

    @JvmStatic
    fun main(args: Array<String>) {
        ZestDriver.main(arrayOf("/home/zver/IdeaProjects/UTBotJava/utbot-fuzzers-jqf/src/test/kotlin", "org.utbot.jqffuzzer.BinarySearchTest", "isUnsorted"))
        exitProcess(0)
        val fileWithInput = File("/home/zver/IdeaProjects/UTBotJava/fuzz-results/corpus/id_000000")

        // Construct generators for each parameter
//        val generators = Arrays.stream<Parameter>(method.getMethod().getParameters())
//            .map(Function<Parameter, ParameterTypeContext> { parameter: Parameter? ->
//                this.createParameterTypeContext(
//                    parameter
//                )
//            })
//            .map(Function<ParameterTypeContext, Generator<Any>> { parameter: ParameterTypeContext? ->
//                generatorRepository.produceGenerator(
//                    parameter
//                )
//            })
//            .collect(Collectors.toList())
        // Generate input values
        val randomFile = StreamBackedRandom(fileWithInput.inputStream(), java.lang.Long.BYTES)
        val random: SourceOfRandomness = FastSourceOfRandomness(randomFile)
        val genStatus = NonTrackingGenerationStatus(random)
//        args = generators.stream()
//            .map { g: Generator<*> -> g.generate(random, genStatus) }
//            .toArray()
//
//        // Let guidance observe the generated input args
//
//        // Let guidance observe the generated input args
//        guidance.observeGeneratedArgs(args)

        ReproDriver.main(
            arrayOf(
                "/home/zver/IdeaProjects/UTBotJava/utbot-fuzzers-jqf/src/test/kotlin",
                "org.utbot.jqffuzzer.BinarySearchTest",
                "isUnsorted",
                "/home/zver/IdeaProjects/UTBotJava/fuzz-results/corpus/id_000000"
            )
        )
    }

    fun testMethod(methodDescription: FuzzedMethodDescription) {

    }

    fun fuzzWithJQF(description: FuzzedMethodDescription, utMethod: UtMethod<*>): Sequence<List<UtModel>> {
        // Initialize generator repository with a deterministic seed (for reproducibility)
        val randomness = SourceOfRandomness(Random(42))
        val generatorRepository = GeneratorRepository(randomness).register(ServiceLoaderGeneratorSource());
        val parameter = description.parameters.first()
        val kParameter = utMethod.callable.parameters.first()
        val kfunction = utMethod.callable as KFunction<*>
        val method = kfunction.javaMethod!!
        val param = method.parameters.first()
        val parameterTypeContext = ParameterTypeContext.forParameter(param)
        val generator = generatorRepository.produceGenerator(parameterTypeContext)
        val genStatus = NonTrackingGenerationStatus(randomness)
        generator.generate(randomness, genStatus)
        exitProcess(0)
        println("DESCRIPTION = $description")
        ZestDriver.main(arrayOf("/home/zver/IdeaProjects/UTBotJava/utbot-fuzzers-jqf/build/classes/kotlin/test", "org.utbot.jqffuzzer.BinarySearchTest", "isUnsorted"))
        exitProcess(0)
        return sequenceOf()
    }


    private fun execute(method: UtMethod<*>, vararg args: Any) {

    }


//    fun executeJQF(pathToProject: String, fullClassName: String, funName: String) {
//        ZestDriver.main(arrayOf(pathToProject, fullClassName, funName))
//    }
}