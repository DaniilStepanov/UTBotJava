package org.utbot.engine.greyboxfuzzer.util

import org.utbot.engine.greyboxfuzzer.generator.FParameter
import org.utbot.framework.concrete.UtModelConstructor
import org.utbot.framework.plugin.api.ClassId
import org.utbot.framework.plugin.api.UtNullModel


fun UtModelConstructor.constructModelFromValue(value: Any?, classId: ClassId) =
    if (value == null) {
        UtNullModel(classId)
    } else {
        try {
            ZestUtils.setUnserializableFieldsToNull(value)
            construct(value, classId)
        } catch (e: Throwable) {
            UtNullModel(classId)
        }
    }

fun UtModelConstructor.constructModelFromValues(list: List<Pair<FParameter?, ClassId>>) =
    list.map { (value, classId) ->
        if (value?.value == null) {
            UtNullModel(classId)
        } else {
            try {
                ZestUtils.setUnserializableFieldsToNull(value.value)
                construct(value.value, classId)
            } catch (e: Throwable) {
                UtNullModel(classId)
            }
        }
    }

