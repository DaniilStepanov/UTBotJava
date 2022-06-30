package org.utbot.engine.zestfuzzer.coverage

import org.utbot.framework.plugin.api.Instruction

data class GlobalCoverage(val coveredInstructions: MutableSet<Instruction>) {

    fun addInstructions(instructions: List<Instruction>) {
        instructions.forEach { coveredInstructions.add(it) }
    }

}