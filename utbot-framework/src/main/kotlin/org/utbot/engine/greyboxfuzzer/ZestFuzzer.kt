package org.utbot.engine.greyboxfuzzer

import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance
import org.utbot.engine.displayName
import org.utbot.engine.executeConcretely
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.util.CoverageCollector
import org.utbot.engine.greyboxfuzzer.util.ZestUtils
import org.utbot.engine.greyboxfuzzer.util.getAllDeclaredFields
import org.utbot.engine.isStatic
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.signature
import org.utbot.framework.plugin.api.util.utContext
import org.utbot.instrumentation.ConcreteExecutor
import org.utbot.instrumentation.util.WritingToKryoException
import soot.Scene
import soot.jimple.internal.JAssignStmt
import soot.jimple.internal.JInstanceFieldRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName
import kotlin.system.exitProcess

class ZestFuzzer(
    private val concreteExecutor: ConcreteExecutor<UtConcreteExecutionResult, UtExecutionInstrumentation>,
    private val methodUnderTest: UtMethod<*>,
    private val instrumentation: List<UtInstrumentation>,
    private var thisInstance: Any?,
) {

    /**
     * Mean number of mutations to perform in each round.
     */
    private val MEAN_MUTATION_COUNT = 8.0

    /**
     * Mean number of contiguous bytes to mutate in each mutation.
     */
    private val MEAN_MUTATION_SIZE = 4.0 // Bytes

    suspend fun fuzz(): Sequence<List<UtModel>> {
        val kfunction = methodUnderTest.callable as KFunction<*>
        val method = kfunction.javaMethod!!
        val clazz = methodUnderTest.clazz
        //TODO!! DO NOT FORGET TO REMOVE IT
        //if (method.name != "searchLineNumber") return sequenceOf()
        val cl = Scene.v().classes.find { it.name == methodUnderTest.clazz.jvmName }!!
        val sootMethod =
            cl.methods.find {
                val sig = it.bytecodeSignature.drop(1).dropLast(1).substringAfter("${clazz.jvmName}: ")
                kfunction.javaMethod!!.signature == sig
            }
//        val classFieldsUsedByFunc =
//            sootMethod!!.activeBody.units
//                .asSequence()
//                .mapNotNull { it as? JAssignStmt }
//                .map { it.rightBox.value }
//                .mapNotNull { it as? JInstanceFieldRef }
//                .mapNotNull { fieldRef -> clazz.java.getAllDeclaredFields().find { it.name == fieldRef.field.name } }
//                .toSet()

        val methodLines = sootMethod!!.activeBody.units.map { it.javaSourceStartLineNumber }.filter { it != -1 }.toSet()
        var maxCoverage = 0
        val repeatTimes = 10
        repeat(repeatTimes) {
            println("EXECUTION NUMBER $it")
            if (thisInstance != null) {
//                InstancesGenerator.regenerateFields(clazz.java, thisInstance, classFieldsUsedByFunc.toList())
//                ZestUtils.setUnserializableFieldsToNull(thisInstance)
            }
            println()
            val generatedParameters =
                method.parameters.mapIndexed { index, parameter ->
                    DataGenerator.generate(
                        parameter,
                        index,
                        DataGeneratorSettings.sourceOfRandomness,
                        DataGeneratorSettings.genStatus
                    ) to classIdForType(parameter.type)
                }
            val generatedParameterAsUtModel = generatedParameters.map {
                if (it.first == null) {
                    UtNullModel(it.second)
                } else {
                    try {
                        ZestUtils.setUnserializableFieldsToNull(it.first!!.value)
                        UtModelConstructor(IdentityHashMap()).construct(it.first!!.value, it.second)
                    } catch (e: Throwable) {
                        UtNullModel(it.second)
                    }
                }
            }
            //TODO regenerate fiels of thisInstance
//            if (it != 0 && Random().nextBoolean() && thisInstance != null) {
//                //InstancesGenerator.regenerateRandomFields(clazz.java, thisInstance)
//            }
            var thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz.java)
            val initialEnvironmentModels =
                EnvironmentModels(thisInstanceAsUtModel, generatedParameterAsUtModel, mapOf())
            println("EXECUTING FUNCTION ${method.name}")
            try {
//                while (true) {
//                    try {
//                        val tmpInitialEnvironmentModels =
//                            EnvironmentModels(thisInstanceAsUtModel, generatedParameterAsUtModel, mapOf())
//                        val executor =
//                            ConcreteExecutor(
//                                UtExecutionInstrumentation,
//                                concreteExecutor.pathsToUserClasses,
//                                concreteExecutor.pathsToDependencyClasses
//                            ).apply { this.classLoader = utContext.classLoader }
//                        executor.executeConcretely(methodUnderTest, tmpInitialEnvironmentModels, listOf())
//                        break
//                    } catch (e: WritingToKryoException) {
//
//                        thisInstanceAsUtModel = createUtModelFromThis(thisInstance, clazz.java)
//                    } catch (e: Throwable) {
//                        break
//                    }
//                }
                val executor =
                    ConcreteExecutor(
                        UtExecutionInstrumentation,
                        concreteExecutor.pathsToUserClasses,
                        concreteExecutor.pathsToDependencyClasses
                    ).apply { this.classLoader = utContext.classLoader }
                val executionResult =
                    executor.executeConcretely(methodUnderTest, initialEnvironmentModels, listOf())
                println("EXEC RES = ${executionResult.result}")
                val coveredLines = executionResult.coverage.coveredInstructions.map { it.lineNumber }.toSet()
                println("EXECUTOR = ${executor.instrumentation}")
                executionResult.coverage.coveredInstructions.forEach { CoverageCollector.coverage.add(it) }
                val coveredMethodInstructions = CoverageCollector.coverage
                    .filter { it.methodSignature == method.signature }
                    .map { it.lineNumber }
                    .toSet()
                if (coveredMethodInstructions.size == methodLines.size) {
                    println("I'M DONE WITH ${methodUnderTest.displayName}")
                    return sequenceOf()
                }
//                println("COVERAGE = ${coveredLines.size} $coveredLines")
//                println("COVERED LINES ${coveredLines.size} from $numberOfLinesInMethod ${coveredLines.size / numberOfLinesInMethod.toDouble() * 100.0}")
                if (coveredLines.size > maxCoverage) {
                    maxCoverage = coveredLines.size
                }
            } catch (e: Error) {
                println("Error :(")
            } catch (e: Exception) {
                thisInstance = InstancesGenerator.generateInstanceWithUnsafe(clazz.java, 0, true)
//                if (e is WritingToKryoException && thisInstance != null) {
//                    tryToRepairThisInstance(thisInstance, generatedParameterAsUtModel, clazz)
//                }
                println("Exception in ${methodUnderTest.displayName} :( $e")
                //exitProcess(0)
            }
            println("--------------------------------")
        }
        println("MAX COVERAGE = $maxCoverage")
        return sequenceOf()
    }

    private suspend fun tryToRepairThisInstance(
        thisInstance: Any,
        generatedParameterAsUtModel: List<UtModel>,
        clazz: KClass<*>
    ): Any {
        val repairedThisInstance =
            UtModelConstructor(IdentityHashMap()).construct(thisInstance, classIdForType(clazz.java))
        val newInitialEnvironmentModels =
            EnvironmentModels(repairedThisInstance, generatedParameterAsUtModel, mapOf())
        val executor =
            ConcreteExecutor(
                UtExecutionInstrumentation,
                concreteExecutor.pathsToUserClasses,
                concreteExecutor.pathsToDependencyClasses
            ).apply { this.classLoader = utContext.classLoader }
        executor.executeConcretely(methodUnderTest, newInitialEnvironmentModels, listOf())
        return thisInstance
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


    private fun createUtModelFromThis(thisInstance: Any?, clazz: Class<*>): UtModel? =
        if (thisInstance is UtModel) {
            thisInstance
        } else if (thisInstance != null) {
            UtModelConstructor(IdentityHashMap()).construct(thisInstance, classIdForType(clazz))
        } else {
            if (methodUnderTest.isStatic) {
                null
            } else {
                UtNullModel(classIdForType(clazz))
            }
        }
}