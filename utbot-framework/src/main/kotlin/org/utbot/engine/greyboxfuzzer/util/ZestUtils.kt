package org.utbot.engine.greyboxfuzzer.util

import org.utbot.instrumentation.util.TunedKryo

object ZestUtils {
    fun setUnserializableFieldsToNull(instance: Any): Boolean {
        val tunedKryo = TunedKryo()
        val fields =
            try {
                instance::class.java.getAllDeclaredFieldsRecursive(instance).reversed()
            } catch (e: Throwable) {
                println("CANT GET FIELDS OF $instance")
                e.printStackTrace()
                return false
            }
        for (field in fields) {
            val fieldValue =
                field.second.getFieldValue(field.first) ?: continue//field.getFieldValue(myThisInstance) ?: continue
            try {
                tunedKryo.tryToSerialize(fieldValue)
            } catch (e: Throwable) {
                try {
                    println("SET ${field.second.name} field of class ${field.first::class.java} to default value")
                    field.second.setDefaultValue(field.first)
                } catch (e: Throwable) {
                    println("CAN NOT SET ${field.second.name} to null")
                }
            }
        }
        return try {
            tunedKryo.tryToSerialize(instance)
            true
        } catch (e: Throwable) {
            false
        }
    }
}