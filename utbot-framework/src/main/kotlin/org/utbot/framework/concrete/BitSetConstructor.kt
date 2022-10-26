package org.utbot.framework.concrete

import org.utbot.framework.plugin.api.UtAssembleModel
import org.utbot.framework.plugin.api.UtExecutableCallModel
import org.utbot.framework.plugin.api.UtStatementModel
import org.utbot.framework.plugin.api.util.id
import org.utbot.framework.plugin.api.util.longArrayClassId
import org.utbot.framework.plugin.api.util.methodId

internal class BitSetConstructor : UtAssembleModelConstructorBase() {
    override fun UtAssembleModel.modifyChains(
        internalConstructor: UtModelConstructorInterface,
        instantiationChain: MutableList<UtStatementModel>,
        modificationChain: MutableList<UtStatementModel>,
        valueToConstructFrom: Any
    ) {
        valueToConstructFrom as java.util.BitSet
        val classId = valueToConstructFrom::class.java.id
        with(internalConstructor) {
            instantiationChain += UtExecutableCallModel(
                instance = null,
                methodId(classId, "valueOf", classId, longArrayClassId),
                listOf(
                    construct(valueToConstructFrom.toLongArray(), longArrayClassId),
                ),
                this@modifyChains
            )
        }
    }
}