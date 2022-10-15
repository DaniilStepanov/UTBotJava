package org.utbot.engine.greyboxfuzzer.mutator

import org.utbot.engine.greyboxfuzzer.util.toSootMethod
import org.utbot.engine.javaMethod
import org.utbot.framework.plugin.api.UtMethod

class FieldInformationCollector {

    fun collectInfo(methodUnderTest: UtMethod<*>) {
        val sootMethod = methodUnderTest.javaMethod?.toSootMethod() ?: return
        //TODO light-weighted taint analysis
        println()
    }
}