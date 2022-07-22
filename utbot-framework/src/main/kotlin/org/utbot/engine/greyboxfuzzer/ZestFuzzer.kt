package org.utbot.engine.greyboxfuzzer

import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance
import org.utbot.engine.executeConcretely
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtConcreteExecutionResult
import org.utbot.framework.concrete.UtExecutionInstrumentation
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.id
import org.utbot.instrumentation.ConcreteExecutor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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

    suspend fun fuzz(): Sequence<List<UtModel>> {
        val kfunction = methodUnderTest.callable as KFunction<*>
        val method = kfunction.javaMethod!!
        var maxCoverage = 0
        repeat(1) {
            println("EXECUTION NUMBER $it")
            val generatedParameters = method.parameters.map { parameter ->
                DataGenerator.generate(
                    parameter,
                    DataGeneratorSettings.sourceOfRandomness,
                    DataGeneratorSettings.genStatus
                )
            }
            if (generatedParameters.any { it == null }) return@repeat
            println("GENERATED PARAMS = $generatedParameters")
//            val generatedParameterAsUtModel = generatedParameters.map { UtPrimitiveModel(it!!.value) }
            //public void testLocalDateTimeSerialization(int year, int month, int dayOfMonth, int hour, int minute, int second) {
            val generatedParameterAsUtModel = generatedParameters.map {
                UtModelConstructor(IdentityHashMap()).construct(it!!.value, classIdForType(it.value::class.java))
            }
            val initialEnvironmentModels = EnvironmentModels(thisInstance, generatedParameterAsUtModel, mapOf())
            println("EXECUTING FUNCTION")
            try {
                val executionResult =
                    concreteExecutor.executeConcretely(methodUnderTest, initialEnvironmentModels, listOf())
                println("EXEC RES = ${executionResult.result}")
                println("COVERAGE = ${executionResult.coverage.coveredInstructions.size}")
                if (executionResult.coverage.coveredInstructions.size > maxCoverage) {
                    maxCoverage = executionResult.coverage.coveredInstructions.size
                }
            } catch (e: Error) {
                println("Error :(")
            } catch (e: Exception) {
                println("Exception :( ")
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