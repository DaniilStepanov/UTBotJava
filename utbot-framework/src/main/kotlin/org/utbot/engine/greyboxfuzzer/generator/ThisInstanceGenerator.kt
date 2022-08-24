package org.utbot.engine.greyboxfuzzer.generator

import org.utbot.external.api.classIdForType
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.UtModel
import java.util.*

object ThisInstanceGenerator {

    var utModelThisInstance: UtModel? = null

    fun generateThis(clazz: Class<*>) {
        utModelThisInstance = InstancesGenerator.generateInstanceWithUnsafe(clazz, 0)?.let {
            UtModelConstructor(IdentityHashMap()).construct(it, classIdForType(clazz))
        }
    }

}