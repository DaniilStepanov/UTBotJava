package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.methodId
import org.utbot.framework.plugin.api.util.stringClassId

internal class LocaleConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.util.Locale
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "forLanguageTag", classId, stringClassId),
                listOf(
                    construct(valueToConstructFrom.toLanguageTag(), stringClassId),
                ),
                this@modifyChains
            )
        }
    }
}