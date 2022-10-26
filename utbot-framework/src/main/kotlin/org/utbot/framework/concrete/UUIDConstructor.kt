package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.constructorId
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.longArrayClassId
import org.utbot.framework.plugin.api.util.longClassId
import org.utbot.framework.plugin.api.util.methodId

internal class UUIDConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.util.UUID
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                constructorId(classId, longClassId, longClassId),
                listOf(
                    construct(valueToConstructFrom.mostSignificantBits, longClassId),
                    construct(valueToConstructFrom.leastSignificantBits, longClassId),
                ),
                this@modifyChains
            )
        }
    }
}