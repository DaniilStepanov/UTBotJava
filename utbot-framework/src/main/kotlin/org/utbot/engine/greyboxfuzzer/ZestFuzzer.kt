package org.utbot.engine.greyboxfuzzer

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance
import org.utbot.engine.executeConcretely
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.util.CoverageCollector
import org.utbot.engine.isStatic
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.signature
import org.utbot.framework.plugin.api.util.utContext
import org.utbot.instrumentation.ConcreteExecutor
import soot.Scene
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName
import kotlin.reflect.jvm.kotlinFunction
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

    suspend fun fuzz(): Sequence<List<UtModel>> {
        val kfunction = methodUnderTest.callable as KFunction<*>
        val method = kfunction.javaMethod!!
        val clazz = methodUnderTest.clazz
        //TODO!! DO NOT FORGET TO REMOVE IT
        //if (method.name != "checkArgsAreAscending") return sequenceOf()
        val cl = Scene.v().classes.find { it.name == methodUnderTest.clazz.jvmName }!!
        val sootMethod =
            cl.methods.find {
                val sig = it.bytecodeSignature.drop(1).dropLast(1).substringAfter("${clazz.jvmName}: ")
                kfunction.javaMethod!!.signature == sig
            }
        val methodLines = sootMethod!!.activeBody.units.map { it.javaSourceStartLineNumber }.filter { it != -1 }.toSet()
        var maxCoverage = 0
        val repeatTimes = 100
        repeat(repeatTimes) {
            println("EXECUTION NUMBER $it")
            val generatedParameters =
                method.parameters.mapIndexed { index, parameter ->
                    DataGenerator.generate(
                        parameter,
                        index,
                        DataGeneratorSettings.sourceOfRandomness,
                        DataGeneratorSettings.genStatus
                    ) to classIdForType(parameter.type)
                }

            //println("GENERATED PARAMETERS = $generatedParameters")
            //println("GENERATED PARAMS = $generatedParameters")
//            val generatedParameterAsUtModel = generatedParameters.map { UtPrimitiveModel(it!!.value) }
            //public void testLocalDateTimeSerialization(int year, int month, int dayOfMonth, int hour, int minute, int second) {
            val generatedParameterAsUtModel = generatedParameters.map {
                if (it.first == null) {
                    UtNullModel(it.second)
                } else {
                    try {
                        UtModelConstructor(IdentityHashMap()).construct(it.first!!.value, it.second)
                    } catch (e: Throwable) {
                        UtNullModel(it.second)
                    }
                }
            }

//            val myThisInstance =
//                if (!methodUnderTest.isStatic) {
//                    val generator = DataGenerator.generatorRepository.getOrProduceGenerator(ParameterTypeContext.forClass(clazz.java), 0)
//                    generator?.generate(DataGeneratorSettings.sourceOfRandomness, DataGeneratorSettings.genStatus)?.let {
//                        UtModelConstructor(IdentityHashMap()).construct(it, classIdForType(clazz.java))
//                    } ?: thisInstance
//    //                InstancesGenerator.generateInstanceWithUnsafe(clazz.java, 0)?.let {
//    //                    UtModelConstructor(IdentityHashMap()).construct(it, classIdForType(clazz.java))
//    //                } ?: thisInstance
//                } else thisInstance

            //TODO regenerate fiels of thisInstance
            val initialEnvironmentModels = EnvironmentModels(thisInstance, generatedParameterAsUtModel, mapOf())
            println("EXECUTING FUNCTION ${method.name}")
            try {
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
                executionResult.coverage.coveredInstructions.forEach { CoverageCollector.coverage.add(it) }
                val coveredMethodInstructions = CoverageCollector.coverage
                    .filter { it.methodSignature == method.signature }
                    .map { it.lineNumber }
                    .toSet()
                if (coveredMethodInstructions.size == methodLines.size) {
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
                println("Exception :( $e")
            }
            println("--------------------------------")
        }
        println("MAX COVERAGE = $maxCoverage")
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