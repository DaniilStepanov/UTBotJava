package org.utbot.engine.greyboxfuzzer

import org.utbot.engine.displayName
import org.utbot.engine.executeConcretely
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.mutator.Mutator
import org.utbot.engine.greyboxfuzzer.mutator.Seed
import org.utbot.engine.greyboxfuzzer.mutator.SeedCollector
import org.utbot.engine.greyboxfuzzer.util.CoverageCollector
import org.utbot.engine.greyboxfuzzer.util.ZestUtils
import org.utbot.engine.greyboxfuzzer.util.constructModelFromValues
import org.utbot.engine.greyboxfuzzer.util.getAllDeclaredFields
import org.utbot.engine.isStatic
import org.utbot.engine.javaMethod
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.signature
import org.utbot.framework.plugin.api.util.utContext
import org.utbot.instrumentation.ConcreteExecutor
import soot.Scene
import soot.jimple.internal.JAssignStmt
import soot.jimple.internal.JInstanceFieldRef
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName
import kotlin.system.exitProcess

class GreyBoxFuzzer(
    private val concreteExecutor: ConcreteExecutor<UtConcreteExecutionResult, UtExecutionInstrumentation>,
    private val methodUnderTest: UtMethod<*>,
    private val instrumentation: List<UtInstrumentation>,
    private var thisInstance: Any?,
) {

    val seeds = SeedCollector()
    val kfunction = methodUnderTest.callable as KFunction<*>
    val explorationStageIterations = 1000
    val exploitationStageIterations = 1000

    suspend fun fuzz(): Sequence<List<UtModel>> {
        println("STARTED TO FUZZ ${kfunction.name}")
        val method = kfunction.javaMethod!!
        val clazz = methodUnderTest.clazz
        val cl = Scene.v().classes.find { it.name == methodUnderTest.clazz.jvmName }!!
        val sootMethod =
            cl.methods.find {
                val sig = it.bytecodeSignature.drop(1).dropLast(1).substringAfter("${clazz.jvmName}: ")
                kfunction.javaMethod!!.signature == sig
            }
        val classFieldsUsedByFunc =
            sootMethod!!.activeBody.units
                .asSequence()
                .mapNotNull { it as? JAssignStmt }
                .map { it.rightBox.value }
                .mapNotNull { it as? JInstanceFieldRef }
                .mapNotNull { fieldRef -> clazz.java.getAllDeclaredFields().find { it.name == fieldRef.field.name } }
                .toSet()
        val methodLines = sootMethod.activeBody.units.map { it.javaSourceStartLineNumber }.filter { it != -1 }.toSet()
        var maxCoverage = 0
        val repeatTimes = 1000
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
        println("SEEDS AFTER EXPLORATION STAGE = ${seeds.seedsSize()}")
        exploitationStage(exploitationStageIterations, clazz.java, methodLines, currentCoverageByLines)
        return sequenceOf()
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

    private suspend fun explorationStage(
        numberOfIterations: Int,
        methodLinesToCover: Set<Int>,
        clazz: Class<*>,
        classFieldsUsedByFunc: Set<Field>,
        methodUnderTest: UtMethod<*>,
        prevMethodCoverage: Set<Int>
    ) {
        val method = kfunction.javaMethod ?: return
        repeat(numberOfIterations) { iterationNumber ->
            println("Iteration number $iterationNumber")
            val m = IdentityHashMap<Any, UtModel>()
            val modelConstructor = UtModelConstructor(m)
            if (thisInstance != null && iterationNumber != 0) {
                InstancesGenerator.regenerateFields(clazz, thisInstance!!, classFieldsUsedByFunc.toList())
                ZestUtils.setUnserializableFieldsToNull(thisInstance!!)
            }
            val generatedParameters =
                method.parameters.mapIndexed { index, parameter ->
                    DataGenerator.generate(
                        parameter,
                        index,
                        modelConstructor,
                        DataGeneratorSettings.sourceOfRandomness,
                        DataGeneratorSettings.genStatus
                    ) to classIdForType(parameter.type)
                }
            //val generatedParametersAsUtModels = constructModelsFromValues(generatedParameters, modelConstructor)
            val thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz, modelConstructor)
            val stateBefore =
                EnvironmentModels(thisInstanceAsUtModel, generatedParameters.map { it.first.utModel }, mapOf())
            val executionResult = execute(stateBefore, methodUnderTest)
            val seedScore =
                handleCoverage(executionResult!!, methodUnderTest.javaMethod!!, prevMethodCoverage, methodLinesToCover)
            seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
            println("Execution result: ${executionResult.result}")
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
            val thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz, modelConstructor)
            val stateBefore =
                EnvironmentModels(thisInstanceAsUtModel, randomSeedArgumentsAsUtModels, mapOf())
            //println(stateBefore)
            val executionResult = execute(stateBefore, methodUnderTest)
            val seedScore =
                handleCoverage(executionResult!!, methodUnderTest.javaMethod!!, prevMethodCoverage, methodLinesToCover)
            //seeds.addSeed(Seed(thisInstance, generatedParameters, seedScore.toDouble()))
            println("MUTATED SEED SCORE = $seedScore")
            println("Execution result: ${executionResult.result}")
            println("-----------------------------------------")
        }
    }

    private suspend fun tryToRepairThisInstance(
        thisInstance: Any,
        generatedParameterAsUtModel: List<UtModel>,
        clazz: KClass<*>,
        modelConstructor: UtModelConstructor
    ): Any {
        val repairedThisInstance =
            modelConstructor.construct(thisInstance, classIdForType(clazz.java))
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


    private fun createUtModelFromThis(
        thisInstance: Any?,
        clazz: Class<*>,
        modelConstructor: UtModelConstructor
    ): UtModel? =
        if (thisInstance is UtModel) {
            thisInstance
        } else if (thisInstance != null) {
            modelConstructor.construct(thisInstance, classIdForType(clazz))
        } else {
            if (methodUnderTest.isStatic) {
                null
            } else {
                UtNullModel(classIdForType(clazz))
            }
        }
}