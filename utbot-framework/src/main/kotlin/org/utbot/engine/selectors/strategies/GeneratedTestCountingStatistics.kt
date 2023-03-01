package org.utbot.engine.selectors.strategies

import org.utbot.engine.state.ExecutionState
import org.utbot.engine.InterProceduralUnitGraph

class GeneratedTestCountingStatistics(
    graph: InterProceduralUnitGraph
) : TraverseGraphStatistics(graph) {
    var generatedTestsCount = 0
        internal set

    override fun onTraversed(executionState: ExecutionState) {
        generatedTestsCount++
    }
}