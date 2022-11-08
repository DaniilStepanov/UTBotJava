package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.ConstructorId
import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.stringClassId

internal class BigNumberConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        val stringValue = valueToConstructFrom.toString()
        val stringValueModel = internalConstructor.construct(stringValue, stringClassId)

        val classId = valueToConstructFrom::class.java.id

        instantiationChain += UtExecutableCallModel(
            instance = null,
            ConstructorId(classId, listOf(stringClassId)),
            listOf(stringValueModel),
            this
        )
    }
}
