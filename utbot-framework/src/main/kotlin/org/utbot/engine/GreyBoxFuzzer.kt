package org.utbot.engine

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.utbot.engine.util.SootToAsmMapper
import org.utbot.engine.util.getFieldValue
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.isConstructor
import org.utbot.framework.plugin.api.util.isStatic
import org.utbot.framework.plugin.api.util.jClass
import org.utbot.framework.util.graph
import org.utbot.framework.util.sootMethod
import org.utbot.fuzzer.FuzzedMethodDescription
import org.utbot.fuzzer.FuzzedValue
import org.utbot.fuzzer.UtFuzzedExecution
import org.utbot.greyboxfuzzer.generator.DataGenerator
import org.utbot.greyboxfuzzer.generator.GreyBoxFuzzerGeneratorsAndSettings
import org.utbot.greyboxfuzzer.generator.StaticMethodThisInstance
import org.utbot.greyboxfuzzer.generator.ThisInstance
import org.utbot.greyboxfuzzer.mutator.Mutator
import org.utbot.greyboxfuzzer.mutator.Seed
import org.utbot.greyboxfuzzer.mutator.SeedCollector
import org.utbot.greyboxfuzzer.quickcheck.generator.GeneratorContext
import org.utbot.greyboxfuzzer.util.*
import org.utbot.instrumentation.instrumentation.execution.UtFuzzingConcreteExecutionResult
import soot.Scene
import soot.SootMethod
import soot.asm.AsmMethodSource
import soot.jimple.Stmt
import soot.jimple.internal.JIdentityStmt
import java.lang.reflect.Executable
import java.lang.reflect.Field
import kotlin.random.Random

class GreyBoxFuzzer(
    private val methodUnderTest: ExecutableId,
    private val constants: Map<ClassId, List<UtModel>>,
    private val globalGraph: InterProceduralUnitGraph,
    private val fuzzerUtModelConstructor: FuzzerUtModelConstructor,
    private val executor: suspend (ExecutableId, EnvironmentModels, List<UtInstrumentation>) -> UtFuzzingConcreteExecutionResult,
    private val valueConstructor: (EnvironmentModels) -> List<UtConcreteValue<*>>,
    private val timeBudgetInMillis: Long
) {

    private var methodInstructions: Set<Instruction>? = null
    private var seeds: SeedCollector = SeedCollector()
    private val timeRemain
        get() = timeOfStart + timeBudgetInMillis - System.currentTimeMillis()
    private val timeOfStart = System.currentTimeMillis()
    private val percentageOfTimeBudgetToChangeMode = 25
    private val logger = KotlinLogging.logger {}
    private val classMutator = Mutator()
    private val processedMethods = mutableMapOf<SootMethod, List<AbstractInsnNode>>()


    val methodASMInstructions = run {
        val jClass = methodUnderTest.classId.jClass
        val str = jClass.classLoader.getResourceAsStream(jClass.name.replace('.', '/') + ".class")!!
        val classNode = ClassNode()
        val classReader = ClassReader(str)
        classReader.accept(classNode, 0)
        classNode.methods
            .find { it.name + it.desc == methodUnderTest.signature }!!
            .instructions
            .toList()
    }

    private fun SootMethod.getAsmInstructions(): List<AbstractInsnNode>? {
        return (this.source as? AsmMethodSource)?.getFieldValue<InsnList>("instructions")?.toList()
    }

    suspend fun fuzz() = flow {
        logger.debug { "Started to fuzz ${methodUnderTest.name}" }
        val javaClazz = methodUnderTest.classId.jClass
        val sootMethod = methodUnderTest.sootMethod
        val instructionsToSootStmts: MutableMap<String, List<Pair<AbstractInsnNode, Stmt?>>> = mutableMapOf()
        SootToAsmMapper.mapInstructions(sootMethod).also { instructionsToSootStmts[sootMethod.pureJavaSignature] = it }
        processedMethods[sootMethod] = sootMethod.getAsmInstructions() ?: listOf()
        val javaMethod = sootMethod.toJavaMethod() ?: return@flow
        val generatorContext = GeneratorContext(fuzzerUtModelConstructor, constants)
        val classFieldsUsedByFunc = sootMethod.getClassFieldsUsedByFunc(javaClazz)
        while (timeRemain > 0 || !isMethodCovered()) {
            explorationStage(
                javaMethod,
                classFieldsUsedByFunc,
                methodUnderTest,
                generatorContext,
                instructionsToSootStmts
            )
            logger.debug { "SEEDS AFTER EXPLORATION STAGE = ${seeds.seedsSize()}" }
            if (timeRemain < 0 || isMethodCovered()) break
            exploitationStage(instructionsToSootStmts)
        }
    }

    private suspend fun FlowCollector<UtExecution>.explorationStage(
        method: Executable,
        classFieldsUsedByFunc: Set<Field>,
        methodUnderTest: ExecutableId,
        generatorContext: GeneratorContext,
        instructionsToSootStmts: MutableMap<String, List<Pair<AbstractInsnNode, Stmt?>>>
    ) {
        val parametersToGenericsReplacer = method.parameters.map { it to GenericsReplacer() }
        var regenerateThis = false
        val thisInstancesHistory = ArrayDeque<ThisInstance>()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeBudgetInMillis / (100L / percentageOfTimeBudgetToChangeMode)
        var iterationNumber = 0
        while (System.currentTimeMillis() < endTime) {
            try {
                if (timeRemain < 0 || isMethodCovered()) return
                logger.debug { "Func: ${methodUnderTest.name} Iteration number $iterationNumber" }
                iterationNumber++
                while (thisInstancesHistory.size > 1) {
                    thisInstancesHistory.removeLast()
                }
                if (thisInstancesHistory.isEmpty()) {
                    thisInstancesHistory += generateThisInstance(methodUnderTest.classId, generatorContext)
                }
                if (iterationNumber != 0) {
                    if (regenerateThis || Random.getTrue(30)) {
                        logger.debug { "Trying to regenerate this instance" }
                        thisInstancesHistory.clear()
                        thisInstancesHistory += generateThisInstance(methodUnderTest.classId, generatorContext)
                        regenerateThis = false
                    } else if (Random.getTrue(60)) {
                        logger.debug { "Trying to mutate this instance" }
                        thisInstancesHistory += classMutator.mutateThisInstance(
                            thisInstancesHistory.last(),
                            classFieldsUsedByFunc.toList(),
                            generatorContext
                        )
                    }
                }
                /**
                 * Replacing unresolved generics to random compatible to bounds type
                 */
                when {
                    Random.getTrue(10) -> parametersToGenericsReplacer.map { it.second.revert() }
                    Random.getTrue(50) -> parametersToGenericsReplacer.map {
                        it.second.replaceUnresolvedGenericsToRandomTypes(
                            it.first
                        )
                    }
                }
                val thisInstance = thisInstancesHistory.last()
                val generatedParameters =
                    method.parameters.mapIndexed { index, parameter ->
                        DataGenerator.generate(
                            parameter,
                            index,
                            generatorContext,
                            GreyBoxFuzzerGeneratorsAndSettings.sourceOfRandomness,
                            GreyBoxFuzzerGeneratorsAndSettings.genStatus
                        )
                    }
                logger.debug { "Generated params = $generatedParameters" }
                logger.debug { "This instance = $thisInstance" }
                val stateBefore =
                    EnvironmentModels(thisInstance.utModelForExecution, generatedParameters.map { it.utModel }, mapOf())
                try {
                    logger.debug { "Execution of ${methodUnderTest.name} started" }
                    val executionResult = (executor::invoke)(methodUnderTest, stateBefore, listOf())
                    if (methodInstructions == null && executionResult.methodInstructions != null) {
                        methodInstructions = executionResult.methodInstructions!!.toSet()
                    }
                    val coveredMethods = executionResult.coverage.coveredInstructions.map { it.methodSignature }.toSet()
                    coveredMethods
                        .filterNot { methodSignature -> processedMethods.keys.any { it.pureJavaSignature == methodSignature } }
                        .forEach { methodToProcess ->
                            val sootMethodToProcess =
                                Scene.v().classes
                                    .flatMap { it.methods }
                                    .find { it.pureJavaSignature == methodToProcess }
                                    ?: return@forEach
                            SootToAsmMapper.mapInstructions(sootMethodToProcess).let {
                                instructionsToSootStmts[sootMethodToProcess.pureJavaSignature] = it
                            }
                            processedMethods[sootMethodToProcess] = sootMethodToProcess.getAsmInstructions() ?: listOf()
                        }
                    logger.debug { "Execution of ${methodUnderTest.name} result: $executionResult" }
//                    val instructionsIdsToSootStmts =
//                        methodInstructions!!.mapIndexed { index, instruction -> instruction.id to instructionsToSootStmts[index].second }.toMap()
                    val methodsFirstInstructions =
                        executionResult.coverage.coveredInstructions
                            .sortedBy { it.id }
                            .filterDuplicatesBy { it.methodSignature }
                            .associate { it.methodSignature to it.id }
                    val instructionsIdToSootStmts =
                        instructionsToSootStmts.mapValues {
                            val methodFirstInstruction = methodsFirstInstructions[it.key]!!
                            it.value.mapIndexed { ind, p -> ind + methodFirstInstruction to p.second }
                        }
                    println(instructionsIdToSootStmts)
                    val coveredSootStmtToSootMethods =
                        executionResult.coverage.coveredInstructions.mapNotNull { instr ->
                            val instructionMethod = instr.methodSignature
                            instructionsIdToSootStmts[instructionMethod]?.find { it.first == instr.id }?.second
                        }.map { si -> si to processedMethods.keys.find { it.activeBody.units.contains(si) } }
                    for (i in 0 until coveredSootStmtToSootMethods.size - 1) {
                        val curStmt = coveredSootStmtToSootMethods[i].first
                        val curSootMethod = coveredSootStmtToSootMethods[i].second
                        val nextSootMethod = coveredSootStmtToSootMethods[i + 1].second
                        if (curSootMethod != nextSootMethod) {
                            //Join graph
                            globalGraph.join(curStmt, nextSootMethod!!.jimpleBody().graph(), nextSootMethod.isLibraryMethod)
                        }
                    }
                    val coveredSootStmts = mutableListOf<Stmt>()
                    coveredSootStmts.addAll(methodUnderTest.sootMethod.activeBody.units.takeWhile { it is JIdentityStmt }.map { it as Stmt })
                    var currentInvokeStmt: Stmt? = null
                    for (i in 0 until coveredSootStmtToSootMethods.size - 1) {
                        val curStmt = coveredSootStmtToSootMethods[i].first
                        val curSootMethod = coveredSootStmtToSootMethods[i].second!!
                        val nextSootMethod = coveredSootStmtToSootMethods[i + 1].second!!
                        if (curSootMethod != nextSootMethod) {
                            if (!curSootMethod.jimpleBody().graph().tails.contains(curStmt)) {
                                currentInvokeStmt = curStmt
                                coveredSootStmts.add(curStmt)
                                coveredSootStmts.addAll(nextSootMethod.activeBody.units.takeWhile { it is JIdentityStmt }.map { it as Stmt })
                            } else {
                                coveredSootStmts.add(curStmt)
                                currentInvokeStmt?.let { coveredSootStmts.add(it) }
                            }
                        } else {
                            coveredSootStmts.add(curStmt)
                        }
                    }
                    coveredSootStmts.add(coveredSootStmtToSootMethods.last().first)
                    //processedMethods.mapValues { it.value.filter { it !is LineNumberNode && it !is LabelNode } }
                    //val coveredSootStmts = coveredSootStmtToSootMethods.map { it.first }
                    if (coveredSootStmts.isNotEmpty()) {
                        globalGraph.coveredPaths.add(coveredSootStmts)
                        val edges = globalGraph.allEdges
                        val globalGraphCoveredPath =
                            coveredSootStmts.zipWithNext().map { (src, dst) ->
                                val edge = edges.find { it.src == src && it.dst == dst }
                                if (edge == null) {
                                    null
                                    //TODO("Make implicit edge")
                                } else edge
                            }.toSet()
                        //TODO deal smth
                        if (globalGraphCoveredPath.all { it != null }) {
                            globalGraph.traversed(
                                coveredSootStmts.last(),
                                globalGraphCoveredPath.map { it!! }.toSet()
                            )
                        }
                        println("COVERED SOOT statements = ${coveredSootStmts.joinToString("\n")}")
                    }
//                    val currentMethodCoverageInLines = executionResult.coverage.coveredInstructions
//                        .asSequence()
//                        .filter { it.className == Type.getInternalName(methodUnderTest.classId.jClass) }
//                        .filter { it.methodSignature == methodUnderTest.signature }
//                        .map { it.lineNumber }
//                        .toSet()
//                    val touchedSootStatementsByLineNumbers = globalGraph.stmts.filter { it.javaSourceStartLineNumber in currentMethodCoverageInLines }
//                    globalGraph.touchStatements(touchedSootStatementsByLineNumbers)
//                    val arrModel = (stateBefore.parameters.first() as UtArrayModel)
//                    if (arrModel.length >= 3 && arrModel.stores.all { (it.value as UtArrayModel).length == arrModel.length }) {
//                        println(touchedSootStatementsByLineNumbers)
//                    }
                    val seedCoverage = getCoverage(executionResult.coverage)
                    logger.debug { "Calculating seed score" }
                    val seedScore = seeds.calcSeedScore(seedCoverage)
                    logger.debug { "Adding seed" }
                    val seed = Seed(thisInstance, generatedParameters, seedCoverage, seedScore)
                    if (seeds.isSeedOpensNewCoverage(seed)) {
                        emit(
                            run {
                                val parametersModels =
                                    if (stateBefore.thisInstance == null) {
                                        stateBefore.parameters
                                    } else {
                                        listOfNotNull(stateBefore.thisInstance) + stateBefore.parameters
                                    }
                                val stateBeforeWithNullsAsUtModels =
                                    valueConstructor.invoke(stateBefore).zip(parametersModels)
                                        .map { (concreteValue, model) ->
                                            concreteValue.value?.let { model } ?: UtNullModel(model.classId)
                                        }
                                        .let { if (stateBefore.thisInstance != null) it.drop(1) else it }
                                val newStateBefore = EnvironmentModels(
                                    thisInstance.utModelForExecution,
                                    stateBeforeWithNullsAsUtModels,
                                    mapOf()
                                )
                                if (executionResult.stateAfter != null) {
                                    UtFuzzedExecution(
                                        stateBefore = newStateBefore,
                                        stateAfter = executionResult.stateAfter!!,
                                        result = executionResult.result,
                                        coverage = executionResult.coverage,
                                        fuzzingValues = generatedParameters.map { FuzzedValue(it.utModel) },
                                        fuzzedMethodDescription = FuzzedMethodDescription(methodUnderTest)
                                    )
                                } else {
                                    UtGreyBoxFuzzedExecution(
                                        newStateBefore,
                                        executionResult,
                                        coverage = executionResult.coverage
                                    )
                                }
                            }

                        )
                    }
                    seeds.addSeed(seed)
                    logger.debug { "Execution of ${methodUnderTest.name} concrete result: ${executionResult.result}" }
                    logger.debug { "Seed score = $seedScore" }
                } catch (e: Throwable) {
                    logger.debug(e) { "Exception while execution in method ${methodUnderTest.name} of class ${methodUnderTest.classId.name}" }
                    thisInstancesHistory.clear()
                    regenerateThis = true
                    continue
                }
            } catch (e: FuzzerIllegalStateException) {
                logger.error(e) { "Something wrong in the fuzzing process" }
            }
        }
    }

    private suspend fun FlowCollector<UtExecution>.exploitationStage(instructionsToSootStmts: MutableMap<String, List<Pair<AbstractInsnNode, Stmt?>>>) {
        logger.debug { "Exploitation began" }
        if (seeds.seedsSize() == 0) return
        if (seeds.all { it.parameters.isEmpty() }) return
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeBudgetInMillis / (100L / percentageOfTimeBudgetToChangeMode)
        var iterationNumber = 0
        while (System.currentTimeMillis() < endTime) {
            if (timeRemain < 0 || isMethodCovered()) return
            //Infinite cycle of cant mutate seed
            if (iterationNumber > 30_000) return
            logger.debug { "Func: ${methodUnderTest.name} Mutation iteration number $iterationNumber" }
            iterationNumber++
            val randomSeed = seeds.getRandomWeightedSeed()
            logger.debug { "Random seed params = ${randomSeed.parameters}" }
            val mutatedSeed =
                classMutator.mutateSeed(
                    randomSeed,
                    GreyBoxFuzzerGeneratorsAndSettings.sourceOfRandomness,
                    GreyBoxFuzzerGeneratorsAndSettings.genStatus
                )
            if (mutatedSeed == randomSeed) {
                logger.debug { "Cant mutate seed" }
                continue
            }
            logger.debug { "Mutated params = ${mutatedSeed.parameters}" }
            val stateBefore = mutatedSeed.createEnvironmentModels()
            try {
                val executionResult = (executor::invoke)(methodUnderTest, stateBefore, listOf())
                logger.debug { "Execution result: $executionResult" }
//                val instructionsIdsToSootStmts =
//                    methodInstructions!!.mapIndexed { index, instruction -> instruction.id to instructionsToSootStmts[index].second }.toMap()
//                val coveredSootStmts = executionResult.coverage.coveredInstructions.mapNotNull { instructionsIdsToSootStmts[it.id] }
//                if (coveredSootStmts.isNotEmpty()) {
//                    val sootStmtPrefix = methodUnderTest.sootMethod.activeBody.units.toList().map { it as Stmt }
//                        .takeWhile { it != coveredSootStmts.first() }
//                    globalGraph.coveredPaths.add(sootStmtPrefix + coveredSootStmts)
//                    val edges = globalGraph.allEdges
//                    val prefixEdges = edges.takeWhile { it.dst != coveredSootStmts.first() } + edges.find { it.dst == coveredSootStmts.first() }
//                    val globalGraphCoveredPath =
//                        prefixEdges.toSet() + coveredSootStmts.zipWithNext().map { (src, dst) ->
//                            val edge = edges.find { it.src == src && it.dst == dst }
//                            if (edge == null) {
//                                null
//                                //TODO("Make implicit edge")
//                            } else edge
//                        }.toSet()
//                    if (globalGraphCoveredPath.all { it != null }) {
//                        globalGraph.traversed(
//                            coveredSootStmts.last(),
//                            globalGraphCoveredPath.map { it!! }.toSet()
//                        )
//                    }
//                    println("COVERED SOOT statements = ${coveredSootStmts.joinToString("\n")}")
//                }
                val seedScore = getCoverage(executionResult.coverage)
                mutatedSeed.score = 0.0
                if (seeds.isSeedOpensNewCoverage(mutatedSeed)) {
                    emit(
                        run {
                            val parametersModels =
                                if (stateBefore.thisInstance == null) {
                                    stateBefore.parameters
                                } else {
                                    listOfNotNull(stateBefore.thisInstance) + stateBefore.parameters
                                }
                            val stateBeforeWithNullsAsUtModels =
                                valueConstructor.invoke(stateBefore).zip(parametersModels)
                                    .map { (concreteValue, model) ->
                                        concreteValue.value?.let { model } ?: UtNullModel(
                                            model.classId
                                        )
                                    }
                                    .let { if (stateBefore.thisInstance != null) it.drop(1) else it }
                            val newStateBefore =
                                EnvironmentModels(stateBefore.thisInstance, stateBeforeWithNullsAsUtModels, mapOf())
                            if (executionResult.stateAfter != null) {
                                UtFuzzedExecution(
                                    stateBefore = newStateBefore,
                                    stateAfter = executionResult.stateAfter!!,
                                    result = executionResult.result,
                                    coverage = executionResult.coverage,
                                    fuzzingValues = mutatedSeed.parameters.map { FuzzedValue(it.utModel) },
                                    fuzzedMethodDescription = FuzzedMethodDescription(methodUnderTest)
                                )
                            } else {
                                UtGreyBoxFuzzedExecution(
                                    newStateBefore,
                                    executionResult,
                                    coverage = executionResult.coverage
                                )
                            }
                        }
                    )
                }
                seeds.addSeed(mutatedSeed)
                logger.debug { "Execution result: ${executionResult.result}" }
                logger.debug { "Seed score = $seedScore" }
            } catch (e: Throwable) {
                logger.debug(e) { "Exception while execution in method ${methodUnderTest.name} of class ${methodUnderTest.classId.name}" }
                continue
            }
        }
    }

    private fun getCoverage(
        coverage: Coverage
    ): Set<Instruction> {
        val currentMethodCoverage = coverage.coveredInstructions
            .asSequence()
            .filter { it.className == Type.getInternalName(methodUnderTest.classId.jClass) }
            .filter { it.methodSignature == methodUnderTest.signature }
//            .map { it.id }
            //.filter { it in methodInstructionsIds!! }
            .toSet()
        logger.debug { "Covered instructions ${currentMethodCoverage.count()} from ${methodInstructions?.size}" }
        coverage.coveredInstructions.forEach { CoverageCollector.addCoverage(it) }
        return currentMethodCoverage
    }

    private fun isMethodCovered(): Boolean {
        methodInstructions ?: return false
        val coveredInstructions =
            CoverageCollector.coverage
                .filter { it.className == Type.getInternalName(methodUnderTest.classId.jClass) }
                .filter { it.methodSignature == methodUnderTest.signature }
                .toSet()
        return coveredInstructions.containsAll(methodInstructions!!)
    }

    private fun generateThisInstance(classId: ClassId, generatorContext: GeneratorContext): ThisInstance =
        if (!methodUnderTest.isStatic && !methodUnderTest.isConstructor) {
            DataGenerator.generateThis(
                classId,
                generatorContext,
                GreyBoxFuzzerGeneratorsAndSettings.sourceOfRandomness,
                GreyBoxFuzzerGeneratorsAndSettings.genStatus
            )
        } else {
            StaticMethodThisInstance
        }

}