package org.utbot.engine.greyboxfuzzer

import org.utbot.engine.*
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.mutator.Mutator
import org.utbot.engine.greyboxfuzzer.mutator.Seed
import org.utbot.engine.greyboxfuzzer.mutator.SeedCollector
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.*
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.signature
import org.utbot.framework.plugin.api.util.utContext
import org.utbot.instrumentation.ConcreteExecutor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.random.Random
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class GreyBoxFuzzer(
    private val pathsToUserClasses: String,
    private val pathsToDependencyClasses: String,
    private val methodUnderTest: UtMethod<*>,
) {

    private val seeds = SeedCollector()
    val kfunction = methodUnderTest.callable as KFunction<*>
    private val explorationStageIterations = 10
    private val exploitationStageIterations = 100
    private var thisInstance: UtModel? = generateThisInstance(methodUnderTest.clazz.java)

    //TODO make it return Sequence<UtExecution>
    suspend fun fuzz(): Sequence<List<UtModel>> {
        logger.debug { "Started to fuzz ${kfunction.name}" }
        val javaMethod = kfunction.javaMethod ?: run {
            logger.error { "Can't get Java function from Kotlin method ${kfunction.name}" }
            return sequenceOf()
        }
        val javaClazz = methodUnderTest.clazz.java
        val sootMethod = javaMethod.toSootMethod() ?: run {
            logger.error { "Can't convert Java method ${javaMethod.name} to Soot method" }
            return sequenceOf()
        }
        val classFieldsUsedByFunc = sootMethod.getClassFieldsUsedByFunc(javaClazz)
        val methodLines = sootMethod.activeBody.units.map { it.javaSourceStartLineNumber }.filter { it != -1 }.toSet()
        val currentCoverageByLines = CoverageCollector.coverage
            .filter { it.methodSignature == javaMethod.signature }
            .map { it.lineNumber }
            .toSet()
        //TODO repeat or while
        explorationStage(
            javaMethod,
            explorationStageIterations,
            methodLines,
            javaClazz,
            classFieldsUsedByFunc,
            methodUnderTest,
            currentCoverageByLines
        )
        println("SEEDS AFTER EXPLORATION STAGE = ${seeds.seedsSize()}")
        println(seeds.getBestSeed())
        exploitationStage(exploitationStageIterations, javaClazz, methodLines, currentCoverageByLines)
        //UtModelGenerator.reset()
        return sequenceOf()
    }

    private suspend fun explorationStage(
        method: Method,
        numberOfIterations: Int,
        methodLinesToCover: Set<Int>,
        clazz: Class<*>,
        classFieldsUsedByFunc: Set<Field>,
        methodUnderTest: UtMethod<*>,
        prevMethodCoverage: Set<Int>
    ) {
        val parametersToGenericsReplacer = method.parameters.map { it to GenericsReplacer() }
        repeat(numberOfIterations) { iterationNumber ->
            logger.debug { "Iteration number $iterationNumber" }
            if (thisInstance != null && iterationNumber != 0) {
                if (Random.getTrue(20)) {
                    logger.debug { "Trying to regenerate this instance" }
                    generateThisInstance(clazz)?.let { thisInstance = it }
                } else if (Random.getTrue(50) && thisInstance is UtAssembleModel) {
                    thisInstance =
                        Mutator.regenerateFields(
                            clazz,
                            thisInstance as UtAssembleModel,
                            classFieldsUsedByFunc.toList()
                        )
                }
            }
            /**
             * Replacing unresolved generics to random compatible to bounds type
             */
//            when {
//                Random.getTrue(10) -> parametersToGenericsReplacer.map { it.second.revert() }
//                Random.getTrue(50) -> parametersToGenericsReplacer.map {
//                    it.second.replaceUnresolvedGenericsToRandomTypes(
//                        it.first
//                    )
//                }
//            }
            val generatedParameters =
                method.parameters.mapIndexed { index, parameter ->
                    DataGenerator.generate(
                        parameter,
                        index,
                        GreyBoxFuzzerGenerators.sourceOfRandomness,
                        GreyBoxFuzzerGenerators.genStatus
                    ) to classIdForType(parameter.type)
                }
            //test
            val seed = Seed(thisInstance, generatedParameters, 3.0)
            println(seed)
            //
            logger.debug { "Generated params = $generatedParameters" }
            logger.debug { "This instance = $thisInstance" }
            val stateBefore =
                EnvironmentModels(thisInstance, generatedParameters.map { it.first.utModel }, mapOf())
            try {
                val executionResult = execute(stateBefore, methodUnderTest) ?: return@repeat
                logger.debug { "Execution result: $executionResult" }
                val seedScore =
                    handleCoverage(
                        executionResult,
                        prevMethodCoverage,
                        methodLinesToCover
                    )
                seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
                logger.debug { "Execution result: ${executionResult.result}" }
            } catch (e: Throwable) {
                logger.debug(e) { "Exception while execution :(" }
                return@repeat
            }
        }
    }

    private fun handleCoverage(
        executionResult: UtFuzzingConcreteExecutionResult,
        prevMethodCoverage: Set<Int>,
        currentMethodLines: Set<Int>
    ): Int {
        val coverage =
            executionResult.coverage.coveredInstructions
                .map { it.lineNumber }
                .filter { it in currentMethodLines }
                .toSet()
        executionResult.coverage.coveredInstructions.forEach { CoverageCollector.coverage.add(it) }
        return (coverage - prevMethodCoverage).size
    }


    private suspend fun exploitationStage(
        numberOfIterations: Int,
        clazz: Class<*>,
        methodLinesToCover: Set<Int>,
        prevMethodCoverage: Set<Int>
    ) {
        logger.debug { "Exploitation began" }
        repeat(numberOfIterations) {
            val randomSeed = seeds.getRandomWeightedSeed() ?: return@repeat
        }
    }
//    private suspend fun exploitationStage(
//        numberOfIterations: Int,
//        clazz: Class<*>,
//        methodLinesToCover: Set<Int>,
//        prevMethodCoverage: Set<Int>
//    ) {
//        logger.debug { "Exploitation began" }
//        repeat(numberOfIterations) {
//            val randomSeed = seeds.getRandomWeightedSeed() ?: return@repeat
//            val randomSeedArguments = randomSeed.arguments.toMutableList()
//            val m = IdentityHashMap<Any, UtModel>()
//            val modelConstructor = UtModelConstructor(m)
//            val randomParameterIndex =
//                when {
//                    randomSeedArguments.isEmpty() -> return@repeat
//                    randomSeedArguments.size == 1 -> 0
//                    else -> Random.nextInt(0, randomSeedArguments.size)
//                }
//            val randomArgument = randomSeedArguments[randomParameterIndex]
//            println("BEFORE = ${randomArgument.first!!.utModel}")
//            val fRandomArgument = randomArgument.first!!
//            val randomSeedArgumentsAsUtModels =
//                modelConstructor.constructModelFromValues(randomSeedArguments).toMutableList()
//            val initialInstanceForMutation =
//                randomSeedArguments[randomParameterIndex].first?.utModel as? UtReferenceModel ?: return@repeat
//            val mutatedArgument =
//                Mutator.mutateParameter(
//                    fRandomArgument,
//                    initialInstanceForMutation,
//                    modelConstructor
//                )
////            randomSeedArguments[randomParameterIndex] = fRandomArgument to randomArgument.second
//            println("AFTER = ${mutatedArgument!!.utModel}")
//            if (mutatedArgument?.utModel == null) return@repeat
//            randomSeedArgumentsAsUtModels[randomParameterIndex] = mutatedArgument.utModel
//            val stateBefore =
//                EnvironmentModels(thisInstance, randomSeedArgumentsAsUtModels, mapOf())
//            //println(stateBefore)
//            try {
//                val executionResult = execute(stateBefore, methodUnderTest)
//                val seedScore =
//                    handleCoverage(
//                        executionResult!!,
//                        prevMethodCoverage,
//                        methodLinesToCover
//                    )
//                //seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
//                println("MUTATED SEED SCORE = $seedScore")
//                println("Execution result1: ${executionResult.result}")
//                println("-----------------------------------------")
//            } catch (e: Throwable) {
//                return@repeat
//            }
//        }
//    }

    private suspend fun ConcreteExecutor<UtFuzzingConcreteExecutionResult, UtFuzzingExecutionInstrumentation>.executeConcretely(
        methodUnderTest: UtMethod<*>,
        stateBefore: EnvironmentModels,
        instrumentation: List<UtInstrumentation>
    ): UtFuzzingConcreteExecutionResult = executeAsync(
        methodUnderTest.callable,
        arrayOf(),
        parameters = UtConcreteExecutionData(stateBefore, instrumentation)
    )

    private suspend fun execute(
        stateBefore: EnvironmentModels,
        methodUnderTest: UtMethod<*>
    ): UtFuzzingConcreteExecutionResult? =
        try {
            val executor =
                ConcreteExecutor(
                    UtFuzzingExecutionInstrumentation,
                    pathsToUserClasses,
                    pathsToDependencyClasses
                ).apply { this.classLoader = utContext.classLoader }
            val res = executor.executeConcretely(methodUnderTest, stateBefore, listOf())
            res
        } catch (e: Throwable) {
            logger.debug {"Exception in ${methodUnderTest.displayName} :( $e"}
            null
        }

    private fun generateThisInstance(clazz: Class<*>) =
        if (!methodUnderTest.isStatic) {
            DataGenerator.generate(
                clazz,
                GreyBoxFuzzerGenerators.sourceOfRandomness,
                GreyBoxFuzzerGenerators.genStatus
            )
        } else {
            null
        }
}