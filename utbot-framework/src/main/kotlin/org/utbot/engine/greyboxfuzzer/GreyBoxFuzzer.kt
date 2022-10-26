package org.utbot.engine.greyboxfuzzer

import org.utbot.engine.displayName
import org.utbot.engine.executeConcretely
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.mutator.Mutator
import org.utbot.engine.greyboxfuzzer.mutator.Seed
import org.utbot.engine.greyboxfuzzer.mutator.SeedCollector
import org.utbot.engine.greyboxfuzzer.util.*
import org.utbot.engine.isStatic
import org.utbot.engine.javaMethod
import org.utbot.example.GraphAlgorithms
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.fieldId
import org.utbot.framework.plugin.api.util.jClass
import org.utbot.framework.plugin.api.util.signature
import org.utbot.framework.plugin.api.util.utContext
import org.utbot.fuzzer.FallbackModelProvider
import org.utbot.instrumentation.ConcreteExecutor
import soot.jimple.internal.JAssignStmt
import soot.jimple.internal.JInstanceFieldRef
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess

class GreyBoxFuzzer(
    private val concreteExecutor: ConcreteExecutor<UtConcreteExecutionResult, UtExecutionInstrumentation>,
    private val methodUnderTest: UtMethod<*>,
    private val instrumentation: List<UtInstrumentation>,
    private var thisInstance: UtModel,
    private val fallbackModelProvider: FallbackModelProvider
) {

    private val seeds = SeedCollector()
    val kfunction = methodUnderTest.callable as KFunction<*>
    private val explorationStageIterations = 10
    val exploitationStageIterations = 100

    suspend fun fuzz(): Sequence<List<UtModel>> {
        println("STARTED TO FUZZ ${kfunction.name}")
        val method = kfunction.javaMethod!!
//        FieldInformationCollector().collectInfo(methodUnderTest)
//        exitProcess(0)
        val clazz = methodUnderTest.clazz
        val sootMethod = method.toSootMethod()
        val classFieldsUsedByFunc =
            sootMethod!!.activeBody.units
                .asSequence()
                .mapNotNull { it as? JAssignStmt }
                .map { it.rightBox.value }
                .mapNotNull { it as? JInstanceFieldRef }
                .mapNotNull { fieldRef -> clazz.java.getAllDeclaredFields().find { it.name == fieldRef.field.name } }
                .toSet()
        val methodLines = sootMethod.activeBody.units.map { it.javaSourceStartLineNumber }.filter { it != -1 }.toSet()
        val currentCoverageByLines = CoverageCollector.coverage
            .filter { it.methodSignature == method.signature }
            .map { it.lineNumber }
            .toSet()
        explorationStage(
            explorationStageIterations,
            methodLines,
            clazz.java,
            classFieldsUsedByFunc,
            methodUnderTest,
            currentCoverageByLines
        )
        //println("SEEDS AFTER EXPLORATION STAGE = ${seeds.seedsSize()}")
        //exploitationStage(exploitationStageIterations, clazz.java, methodLines, currentCoverageByLines)
        return sequenceOf()
    }

    private suspend fun explorationStage(
        numberOfIterations: Int,
        methodLinesToCover: Set<Int>,
        clazz: Class<*>,
        classFieldsUsedByFunc: Set<Field>,
        methodUnderTest: UtMethod<*>,
        prevMethodCoverage: Set<Int>
    ) {
        val method = kfunction.javaMethod ?: return
        val parametersToGenericsReplacer = method.parameters.map { it to GenericsReplacer() }
        repeat(numberOfIterations) { iterationNumber ->
            //UtModelGenerator.reset()
            println("Iteration number $iterationNumber")

            thisInstance = InstancesGenerator.regenerateFields(clazz, thisInstance as UtAssembleModel, classFieldsUsedByFunc.toList())
            println(thisInstance)
//            println("THIS = $thisInstance")
//            exitProcess(0)
////            val m = IdentityHashMap<Any, UtModel>()
////            val modelConstructor = UtModelConstructor(m)
            if (iterationNumber != 0) {
                if (Random.getTrue(20)) {
                    println("REGENERATE THIS")
                    thisInstance = DataGenerator.generate(
                        clazz,
                        DataGeneratorSettings.sourceOfRandomness,
                        DataGeneratorSettings.genStatus
                    )!!
                } else if (thisInstance is UtAssembleModel) {
                    thisInstance =
                        InstancesGenerator.regenerateFields(clazz, thisInstance as UtAssembleModel, classFieldsUsedByFunc.toList())
                }
                //ZestUtils.setUnserializableFieldsToNull(thisInstance!!)
            }
            when {
                Random.getTrue(10) -> parametersToGenericsReplacer.map { it.second.revert() }
                Random.getTrue(50) -> parametersToGenericsReplacer.map {
                    it.second.replaceUnresolvedGenericsToRandomTypes(
                        it.first
                    )
                }
            }
            val generatedParameters =
                method.parameters.mapIndexed { index, parameter ->
                    DataGenerator.generate(
                        parameter,
                        index,
                        DataGeneratorSettings.sourceOfRandomness,
                        DataGeneratorSettings.genStatus
                    ) to classIdForType(parameter.type)
                }
            println("GENERATED PARAMETERS = $generatedParameters")
            //val thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz)
            val stateBefore =
                EnvironmentModels(thisInstance, generatedParameters.map { it.first.utModel }, mapOf())
            try {
                val executionResult = execute(stateBefore, methodUnderTest)
                val seedScore =
                    handleCoverage(
                        executionResult!!,
                        methodUnderTest.javaMethod!!,
                        prevMethodCoverage,
                        methodLinesToCover
                    )
                seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
                println("Execution result: ${executionResult.result}")
            } catch (e: Throwable) {
                println("Exception :( $e")
                return@repeat
            }
        }
    }

    private fun handleCoverage(
        executionResult: UtConcreteExecutionResult,
        method: Method,
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
        println("Exploitation began")
        repeat(numberOfIterations) {
            val randomSeed = seeds.getRandomWeightedSeed() ?: return@repeat
            val randomSeedArguments = randomSeed.arguments.toMutableList()
            val m = IdentityHashMap<Any, UtModel>()
            val modelConstructor = UtModelConstructor(m)
            val randomParameterIndex =
                when {
                    randomSeedArguments.isEmpty() -> return@repeat
                    randomSeedArguments.size == 1 -> 0
                    else -> Random.nextInt(0, randomSeedArguments.size)
                }
            val randomArgument = randomSeedArguments[randomParameterIndex]
            println("BEFORE = ${randomArgument.first!!.utModel}")
            val fRandomArgument = randomArgument.first!!
            val randomSeedArgumentsAsUtModels =
                modelConstructor.constructModelFromValues(randomSeedArguments).toMutableList()
            val initialInstanceForMutation =
                randomSeedArguments[randomParameterIndex].first?.utModel as? UtReferenceModel ?: return@repeat
            val mutatedArgument =
                Mutator.mutateParameter(
                    fRandomArgument,
                    initialInstanceForMutation,
                    modelConstructor
                )
//            randomSeedArguments[randomParameterIndex] = fRandomArgument to randomArgument.second
            println("AFTER = ${mutatedArgument!!.utModel}")
            if (mutatedArgument?.utModel == null) return@repeat
            randomSeedArgumentsAsUtModels[randomParameterIndex] = mutatedArgument.utModel
            val thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz)
            val stateBefore =
                EnvironmentModels(thisInstanceAsUtModel, randomSeedArgumentsAsUtModels, mapOf())
            //println(stateBefore)
            try {
                val executionResult = execute(stateBefore, methodUnderTest)
                val seedScore =
                    handleCoverage(
                        executionResult!!,
                        methodUnderTest.javaMethod!!,
                        prevMethodCoverage,
                        methodLinesToCover
                    )
                //seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
                println("MUTATED SEED SCORE = $seedScore")
                println("Execution result1: ${executionResult.result}")
                println("-----------------------------------------")
            } catch (e: Throwable) {
                return@repeat
            }
        }
    }

    private suspend fun tryToRepairThisInstance(
        thisInstance: Any,
        generatedParameterAsUtModel: List<UtModel>,
        clazz: KClass<*>,
        modelConstructor: UtModelConstructor
    ): Any {
        val repairedThisInstance =
            modelConstructor.constructWithTimeoutOrNull(thisInstance, classIdForType(clazz.java))
                ?: UtNullModel(classIdForType(clazz.java))
        val newInitialEnvironmentModels =
            EnvironmentModels(repairedThisInstance, generatedParameterAsUtModel, mapOf())
        val executor =
            ConcreteExecutor(
                UtExecutionInstrumentation,
                concreteExecutor.pathsToUserClasses,
                concreteExecutor.pathsToDependencyClasses
            ).apply { this.classLoader = utContext.classLoader }
        //executor.executeConcretely(methodUnderTest, newInitialEnvironmentModels, listOf())
        return thisInstance
    }

    private suspend fun execute(
        stateBefore: EnvironmentModels,
        methodUnderTest: UtMethod<*>
    ): UtConcreteExecutionResult? =
        try {
            val executor =
                ConcreteExecutor(
                    UtExecutionInstrumentation,
                    concreteExecutor.pathsToUserClasses,
                    concreteExecutor.pathsToDependencyClasses
                ).apply { this.classLoader = utContext.classLoader }
            val res = executor.executeConcretely(methodUnderTest, stateBefore, listOf())
            println("EXEC RES = $res")
            res
        } catch (e: Throwable) {
            println("Exception in ${methodUnderTest.displayName} :( $e")
            null
        }

    private fun createUtModelFromThis(
        thisInstance: Any?,
        clazz: Class<*>,
    ): UtModel? =
        if (thisInstance is UtModel) {
            thisInstance
        } else if (thisInstance != null) {
            val classId = classIdForType(clazz)
            UtModelGenerator.utModelConstructor.constructWithTimeoutOrNull(thisInstance, classId)
                ?: UtNullModel(classId)
        } else {
            if (methodUnderTest.isStatic) {
                null
            } else {
                UtNullModel(classIdForType(clazz))
            }
        }
}