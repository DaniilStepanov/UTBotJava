package org.utbot.summary

import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.isCheckedException
import org.utbot.summary.UtSummarySettings.MIN_NUMBER_OF_EXECUTIONS_FOR_CLUSTERING
import org.utbot.summary.clustering.MatrixUniqueness
import org.utbot.summary.clustering.SplitSteps
import org.utbot.summary.tag.TraceTag
import org.utbot.summary.tag.TraceTagWithoutExecution

class TagGenerator {
    fun testSetToTags(testSet: UtMethodTestSet): List<TraceTagCluster> {
        val clusteredExecutions = toClusterExecutions(testSet)
        val traceTagClusters = mutableListOf<TraceTagCluster>()

        val numberOfSuccessfulClusters = clusteredExecutions.filterIsInstance<SuccessfulExecutionCluster>().size

        if (clusteredExecutions.isNotEmpty()) {
            val listOfSplitSteps = clusteredExecutions.map {
                val mUniqueness = MatrixUniqueness(it.executions)
                mUniqueness.splitSteps()
            }

            // intersections of steps ONLY in successful clusters
            var stepsIntersections = listOf<Step>()

            // we only want to find intersections if there is more than one successful execution
            if (numberOfSuccessfulClusters > 1 && REMOVE_INTERSECTIONS) {
                val commonStepsInSuccessfulEx = listOfSplitSteps
                    .filterIndexed { i, _ -> clusteredExecutions[i] is SuccessfulExecutionCluster } //search only in successful
                    .map { it.commonSteps }
                    .filter { it.isNotEmpty() }
                if (commonStepsInSuccessfulEx.size > 1) {
                    stepsIntersections = commonStepsInSuccessfulEx.first()
                    for (steps in commonStepsInSuccessfulEx) {
                        stepsIntersections = stepsIntersections.intersect(steps).toList()
                    }
                }
            }

            // for every cluster and step add TraceTagCluster
            clusteredExecutions.zip(listOfSplitSteps) { cluster, splitSteps ->
                val commonStepsInCluster =
                    if (stepsIntersections.isNotEmpty() && numberOfSuccessfulClusters > 1) {
                        splitSteps.commonSteps.subtract(stepsIntersections)
                    } else splitSteps.commonSteps

                val splitStepsModified = SplitSteps(
                    uniqueSteps = commonStepsInCluster.toList()
                )

                traceTagClusters.add(
                    TraceTagCluster(
                        cluster.header,
                        generateExecutionTags(cluster.executions, splitSteps),
                        TraceTagWithoutExecution(
                            commonStepsInCluster.toList(),
                            cluster.executions.first().result,
                            splitStepsModified
                        ),
                        cluster is SuccessfulExecutionCluster
                    )
                )
            }
        } // clusteredExecutions should not be empty!

        return traceTagClusters
    }
}

/**
 * @return list of TraceTag created from executions and splitsSteps
 */
private fun generateExecutionTags(executions: List<UtSymbolicExecution>, splitSteps: SplitSteps): List<TraceTag> =
    executions.map { TraceTag(it, splitSteps) }

/**
 * Splits executions into clusters
 * By default there is 5 types of clusters:
 *      Success, UnexpectedFail, ExpectedCheckedThrow, ExpectedUncheckedThrow, UnexpectedUncheckedThrow
 *      These are split by the type of execution result
 *
 * If Success cluster has more than MIN_NUMBER_OF_EXECUTIONS_FOR_CLUSTERING execution
 * then clustering algorithm splits those into more clusters
 *
 * @return clustered executions
 */
private fun toClusterExecutions(testSet: UtMethodTestSet): List<ExecutionCluster> {
    val methodExecutions = testSet.executions.filterIsInstance<UtSymbolicExecution>()
    val clusters = mutableListOf<ExecutionCluster>()
    val commentPostfix = "for method ${testSet.method.displayName}"

    val grouped = methodExecutions.groupBy { it.result.clusterKind() }

    val successfulExecutions = grouped[ClusterKind.SUCCESSFUL_EXECUTIONS] ?: emptyList()
    if (successfulExecutions.isNotEmpty()) {
        val clustered =
            if (successfulExecutions.size >= MIN_NUMBER_OF_EXECUTIONS_FOR_CLUSTERING) {
                MatrixUniqueness.dbscanClusterExecutions(successfulExecutions)
            } else emptyMap()

        if (clustered.size > 1) {
            for (c in clustered) {
                clusters +=
                    SuccessfulExecutionCluster(
                        "${ClusterKind.SUCCESSFUL_EXECUTIONS.displayName} #${clustered.keys.indexOf(c.key)} $commentPostfix",
                        c.value.toList()
                    )
            }
        } else {
            clusters +=
                SuccessfulExecutionCluster(
                    "${ClusterKind.SUCCESSFUL_EXECUTIONS.displayName} $commentPostfix",
                    successfulExecutions.toList()
                )
        }
    }

    clusters += grouped
        .filterNot { (kind, _) -> kind == ClusterKind.SUCCESSFUL_EXECUTIONS }
        .map { (suffixId, group) ->
        FailedExecutionCluster("${suffixId.displayName} $commentPostfix", group)
    }
    return clusters
}

enum class ClusterKind {
    SUCCESSFUL_EXECUTIONS,
    ERROR_SUITE,
    CHECKED_EXCEPTIONS,
    EXPLICITLY_THROWN_UNCHECKED_EXCEPTIONS,
    OVERFLOWS,
    TIMEOUTS,
    CRASH_SUITE,
    SECURITY;

    val displayName: String get() = toString().replace('_', ' ')
}

private fun UtExecutionResult.clusterKind() = when (this) {
    is UtExecutionSuccess -> ClusterKind.SUCCESSFUL_EXECUTIONS
    is UtExecutionSuccessConcrete -> ClusterKind.SUCCESSFUL_EXECUTIONS
    is UtImplicitlyThrownException -> if (this.exception.isCheckedException) ClusterKind.CHECKED_EXCEPTIONS else ClusterKind.ERROR_SUITE
    is UtExplicitlyThrownException -> if (this.exception.isCheckedException) ClusterKind.CHECKED_EXCEPTIONS else ClusterKind.EXPLICITLY_THROWN_UNCHECKED_EXCEPTIONS
    is UtOverflowFailure -> ClusterKind.OVERFLOWS
    is UtTimeoutException -> ClusterKind.TIMEOUTS
    is UtConcreteExecutionFailure -> ClusterKind.CRASH_SUITE
    is UtSandboxFailure -> ClusterKind.SECURITY
}

/**
 * Structure used to represent execution cluster with header
 */
private sealed class ExecutionCluster(var header: String, val executions: List<UtSymbolicExecution>)

/**
 * Represents successful execution cluster
 */
private class SuccessfulExecutionCluster(header: String, executions: List<UtSymbolicExecution>) :
    ExecutionCluster(header, executions)

/**
 * Represents failed execution cluster
 */
private class FailedExecutionCluster(header: String, executions: List<UtSymbolicExecution>) :
    ExecutionCluster(header, executions)

/**
 * Removes intersections (steps that occur in all of successful executions) from cluster comment
 * If false then intersections will be printed in cluster comment
 */
private const val REMOVE_INTERSECTIONS: Boolean = true

/**
 * Represents execution cluster
 * Contains the entities required for summarization
 */
data class TraceTagCluster(
    var summary: String,
    val traceTags: List<TraceTag>,
    val commonStepsTraceTag: TraceTagWithoutExecution,
    val isSuccessful: Boolean
)
