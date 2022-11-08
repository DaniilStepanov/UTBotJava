package org.utbot.engine.greyboxfuzzer.mutator

import org.utbot.engine.greyboxfuzzer.generator.FParameter
import org.utbot.framework.plugin.api.ClassId

data class Seed(
    val thisInstance: Any?,
    val arguments: List<Pair<FParameter?, ClassId>>,
    val priority: Double = 0.0
)