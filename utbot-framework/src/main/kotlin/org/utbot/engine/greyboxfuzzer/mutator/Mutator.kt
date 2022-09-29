package org.utbot.engine.greyboxfuzzer.mutator

import com.pholser.junit.quickcheck.internal.ParameterTypeContext
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance
import org.utbot.engine.greyboxfuzzer.generator.*
import org.utbot.engine.greyboxfuzzer.util.ZestUtils
import org.utbot.engine.greyboxfuzzer.util.toClass
import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtReferenceModel
import org.utbot.framework.plugin.api.util.method
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.*

object Mutator {

    /**
     * Mean number of mutations to perform in each round.
     */
    private val MEAN_MUTATION_COUNT = 8.0

    /**
     * Mean number of contiguous bytes to mutate in each mutation.
     */
    private val MEAN_MUTATION_SIZE = 4.0 // Bytes
//    fun mutateParameter(
//        originalParameter: Parameter,
//        paramAsModel: UtReferenceModel,
//        modelConstructor: UtModelConstructor
//    ): UtAssembleModel {
//        val randomMethod = paramAsModel.classId.allMethods.first { it.name == "addAll" }
//        val generatedParams =
//            randomMethod.method.parameters.mapIndexed { index, parameter ->
//                val resolvedParameterCtx =
//                    resolveParameterTypeAndBuildParameterContext(originalParameter, parameter, index, randomMethod.method)
//                DataGenerator.generate(
//                    resolvedParameterCtx,
//                    parameter,
//                    index,
//                    modelConstructor,
//                    DataGeneratorSettings.sourceOfRandomness,
//                    DataGeneratorSettings.genStatus
//                ) to classIdForType(parameter.type)
//            }.map {
//                ZestUtils.setUnserializableFieldsToNull(it.first!!.value)
//                modelConstructor.construct(it.first!!.value, it.second)
//            }
//        val callModel = UtExecutableCallModel(paramAsModel, randomMethod, generatedParams)
//        val resUtModelId = modelConstructor.computeUnusedIdAndUpdate()
//        return UtAssembleModel(
//            resUtModelId,
//            paramAsModel.classId,
//            "$resUtModelId",
//            emptyList(),
//            listOf(callModel),
//            initialInstance = paramAsModel
//        )
//    }

    private fun resolveParameterTypeAndBuildParameterContext(
        originalParameter: Parameter,
        parameter: Parameter,
        parameterIndex: Int,
        method: Method
    ): ParameterTypeContext {
        val parameterTypeContext = originalParameter.createParameterTypeContext(0)
        val resolvedOriginalType = parameterTypeContext.getGenericContext().resolveType(parameterTypeContext.type())
        val genericContext = createGenericsContext(resolvedOriginalType, originalParameter.type.toClass()!!)
        val resolvedParameterType = genericContext.method(method).resolveParameterType(parameterIndex)
        val newGenericContext = createGenericsContext(resolvedParameterType, resolvedParameterType.toClass()!!)
        return createParameterContextForParameter(parameter, parameterIndex, newGenericContext, resolvedParameterType)
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