package org.utbot.engine.util

import org.objectweb.asm.tree.*
import org.utbot.common.withAccessibility
import org.utbot.greyboxfuzzer.util.getFieldValue
import soot.PhaseOptions
import soot.SootMethod
import soot.Unit
import soot.asm.AsmMethodSource
import soot.jimple.Jimple
import soot.jimple.JimpleBody
import soot.jimple.Stmt
import soot.jimple.internal.JInvokeStmt
import kotlin.system.exitProcess


class MyAsmMethodSource(
    maxLocals: Int,
    insns: InsnList?,
    localVars: MutableList<LocalVariableNode>?,
    tryCatchBlocks: MutableList<TryCatchBlockNode>?,
    module: String?
) : AsmMethodSource(maxLocals, insns, localVars, tryCatchBlocks, module) {
    override fun tryCorrectingLocalNames(jimp: Jimple?, jb: JimpleBody?) {
        super.tryCorrectingLocalNames(jimp, jb)
        Units.units = myUnits
    }

    val myUnits
        get() = getFieldValue<Map<AbstractInsnNode, Unit>>("units")
}

inline fun <reified T> AsmMethodSource.getFieldValue(fieldName: String): T =
    AsmMethodSource::class.java.getDeclaredField(fieldName).withAccessibility { getFieldValue(this@getFieldValue) as T }

internal object Units {
    var units: Map<AbstractInsnNode, Unit>? = null
}

object SootToAsmMapper {

    fun mapInstructions(sm: SootMethod): List<Pair<AbstractInsnNode, Stmt?>> {
        val asmMethodSource = sm.source as AsmMethodSource
        val maxLocals = asmMethodSource.getFieldValue<Int>("maxLocals")
        val insns = asmMethodSource.getFieldValue<InsnList?>("instructions")
        val localVars = asmMethodSource.getFieldValue<MutableList<LocalVariableNode>?>("localVars")
        val tryCatchBlocks = asmMethodSource.getFieldValue<MutableList<TryCatchBlockNode>?>("tryCatchBlocks")
        val module = asmMethodSource.getFieldValue<String?>("module")
        val myAsmMethodSource = MyAsmMethodSource(maxLocals, insns, localVars, tryCatchBlocks, module)
        PhaseOptions.v().setPhaseOption("jb", "use-original-names")
        val newBody = myAsmMethodSource.getBody(sm, "jb")
        val units = Units.units!!.toMap()
        val instrumentedInstructions = insns!!.toList().filter { it !is LineNumberNode && it !is LabelNode }
        val instructionsToStmts = instrumentedInstructions
            .mapIndexed { i, it -> it to units[it] }
            .flatMap { (inst, unit) ->
                if (unit != null && unit::class.java.name == "soot.asm.UnitContainer") {
                    val arr = unit::class.java.getDeclaredField("units").withAccessibility { getFieldValue(unit) } as Array<*>
                    arr.map { inst to it as Unit }
                } else {
                    listOf(inst to unit)
                }
            }
        val instructionIdToOriginalSootStmt =
            newBody.units.toList()
                .map { unit ->
                    var equivalent =
                        instructionsToStmts
                            .filter { it.second?.javaSourceStartLineNumber == unit.javaSourceStartLineNumber }
                            .find { it.second.toString() == unit.toString() }
                    if (equivalent == null && unit.javaSourceStartLineNumber != -1) {
                        //TODO!! Repair it
                        equivalent =
                            instructionsToStmts
                                .filter { it.second?.javaSourceStartLineNumber == unit.javaSourceStartLineNumber }
                                .minBy { levenshtein(it.second.toString(), unit.toString()) }
                    }
                    equivalent
                }
                .zip(sm.activeBody.units)
                .mapNotNull {
                    if (it.first?.first != null) {
                        it.first?.first to it.second
                    } else null
                }.toMap()
        return instrumentedInstructions.mapIndexed { i, it -> it to instructionIdToOriginalSootStmt[it] as? Stmt }
    }


    private fun levenshtein(lhs : CharSequence, rhs : CharSequence) : Int {
        if(lhs == rhs) { return 0 }
        if(lhs.isEmpty()) { return rhs.length }
        if(rhs.isEmpty()) { return lhs.length }

        val lhsLength = lhs.length + 1
        val rhsLength = rhs.length + 1

        var cost = Array(lhsLength) { it }
        var newCost = Array(lhsLength) { 0 }

        for (i in 1..rhsLength-1) {
            newCost[0] = i

            for (j in 1..lhsLength-1) {
                val match = if(lhs[j - 1] == rhs[i - 1]) 0 else 1

                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = kotlin.math.min(kotlin.math.min(costInsert, costDelete), costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength - 1]
    }

}