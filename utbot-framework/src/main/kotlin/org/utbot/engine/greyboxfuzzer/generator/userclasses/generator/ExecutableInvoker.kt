package org.utbot.engine.greyboxfuzzer.generator.userclasses.generator

import org.utbot.engine.greyboxfuzzer.generator.DataGenerator
import org.utbot.engine.greyboxfuzzer.generator.createParameterContextForParameter
import org.utbot.engine.greyboxfuzzer.util.constructAssembleModelUsingMethodInvocation
import org.utbot.framework.plugin.api.ExecutableId
import org.utbot.framework.plugin.api.UtModel
import org.utbot.engine.greyboxfuzzer.quickcheck.generator.GenerationStatus
import org.utbot.engine.greyboxfuzzer.quickcheck.generator.GeneratorContext
import org.utbot.engine.greyboxfuzzer.quickcheck.internal.ParameterTypeContext
import org.utbot.engine.greyboxfuzzer.quickcheck.random.SourceOfRandomness
import ru.vyarus.java.generics.resolver.context.GenericsContext
import java.lang.reflect.Executable

class ExecutableInvoker(
    private val executable: Executable,
    private val clazz: Class<*>,
    private val executableId: ExecutableId,
    private val genericsContext: GenericsContext?,
    private val sourceOfRandomness: SourceOfRandomness,
    private val generationStatus: GenerationStatus,
    private val generatorContext: GeneratorContext,
    private val depth: Int
) {
    fun invoke(): UtModel {
        val parameterValues = executable.parameters.withIndex().map { indexedParameter ->
            val parameterContext =
                if (genericsContext != null) {
                    createParameterContextForParameter(indexedParameter.value, indexedParameter.index, genericsContext)
                } else {
                    ParameterTypeContext.forParameter(indexedParameter.value)
                }
            DataGenerator.generateUtModel(parameterContext, depth, generatorContext, sourceOfRandomness, generationStatus)
        }
        return generatorContext.utModelConstructor.constructAssembleModelUsingMethodInvocation(
            clazz,
            executableId,
            parameterValues,
            generatorContext
        )
    }
}