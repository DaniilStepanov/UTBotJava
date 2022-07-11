package org.utbot.engine.greyboxfuzzer.util

import org.utbot.framework.codegen.model.constructor.tree.isStatic
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.Array as RArray
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

fun Class<*>.getAllDeclaredFields(): List<Field> {
    val res = mutableListOf<Field>()
    var current: Class<*>? = this
    while (current != null) {
        res.addAll(current.declaredFields)
        current = current.superclass
    }
    return res
}

fun Class<*>.getAllDeclaredMethods(): List<Method> {
    val res = mutableListOf<Method>()
    var current: Class<*>? = this
    while (current != null) {
        res.addAll(current.declaredMethods)
        current = current.superclass
    }
    return res
}

fun Field.getArrayValues(instance: Any): List<Any> {
    val arrayOfObjects = (this.getFieldValue(instance) as Array<*>).filterNotNull()
    val typeOfArrayObjects = if (arrayOfObjects.isEmpty()) Object::class.java else arrayOfObjects.first().javaClass
    for (i in arrayOfObjects.indices) {
        val fieldValue = when (typeOfArrayObjects) {
            Boolean::class.javaObjectType -> RArray.getBoolean(arrayOfObjects, i)
            Byte::class.javaObjectType -> RArray.getByte(arrayOfObjects, i)
            Char::class.javaObjectType -> RArray.getChar(arrayOfObjects, i)
            Short::class.javaObjectType -> RArray.getShort(arrayOfObjects, i)
            Int::class.javaObjectType -> RArray.getInt(arrayOfObjects, i)
            Long::class.javaObjectType -> RArray.getLong(arrayOfObjects, i)
            Float::class.javaObjectType -> RArray.getFloat(arrayOfObjects, i)
            Double::class.javaObjectType -> RArray.getDouble(arrayOfObjects, i)
            else -> RArray.get(arrayOfObjects, i)
        }

    }
    return emptyList()
}

//private fun Class<*>.getLowerBoundClass(classes: List<Class<*>>): Class<*> {
//
//}

fun Class<*>.getAllSuperClassesAndInterfaces(): List<Class<*>> {
    val res = mutableListOf<Class<*>>()
    var superClass = this.superclass
    res.add(superClass)
    res.addAll(this.interfaces)
    while(superClass != null) {
        res.addAll(superClass.interfaces)
        superClass = superClass.superclass?.also { res.add(it) }
    }
    return res
}

fun Field.getFieldValue(instance: Any?): Any? {
    val oldAccessibleFlag = this.isAccessible
    this.isAccessible = true
    return when (this.type) {
        Boolean::class.javaObjectType -> this.getBoolean(instance)
        Byte::class.javaObjectType -> this.getByte(instance)
        Char::class.javaObjectType -> this.getChar(instance)
        Short::class.javaObjectType -> this.getShort(instance)
        Int::class.javaObjectType -> this.getInt(instance)
        Long::class.javaObjectType -> this.getLong(instance)
        Float::class.javaObjectType -> this.getFloat(instance)
        Double::class.javaObjectType -> this.getDouble(instance)
        else -> this.get(instance)
    }.also { this.isAccessible = oldAccessibleFlag }
}

fun Type.toClass(): Class<*> =
    when (this) {
        is ParameterizedTypeImpl -> this.rawType
        is ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl -> this.rawType.toClass()
        else -> this as Class<*>
    }

fun Field.generateInstance(instance: Any, generatedValue: Any?) {
    if (this.isStatic && this.isFinal) return
    this.isAccessible = true
    this.isFinal = false
    if (this.isEnumConstant || this.isSynthetic) return
    if (this.type.isPrimitive) {
        val definedValue = generatedValue
        when (definedValue?.javaClass) {
            null -> this.set(instance, null)
            Boolean::class.javaObjectType -> this.setBoolean(instance, definedValue as Boolean)
            Byte::class.javaObjectType -> this.setByte(instance, definedValue as Byte)
            Char::class.javaObjectType -> this.setChar(instance, definedValue as Char)
            Short::class.javaObjectType -> this.setShort(instance, definedValue as Short)
            Int::class.javaObjectType -> this.setInt(instance, definedValue as Int)
            Long::class.javaObjectType -> this.setLong(instance, definedValue as Long)
            Float::class.javaObjectType -> this.setFloat(instance, definedValue as Float)
            Double::class.javaObjectType -> this.setDouble(instance, definedValue as Double)
            else -> return
        }
    } else {
        this.set(instance, generatedValue)
    }
}

private fun Class<*>.processArray(value: Any): List<Field> {
//    return (0 until JArray.getLength(value)).map { JArray.get(value, it) }
//    val subFields = mutableListOf<Field>()
//    for (i in 0 until JArray.getLength(value)) {
//        val field = JArray.get(value, i)
//    }
    return emptyList()
}

//private fun Field

var Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL
    set(value) {
        if (value == this.isFinal) return
        val modifiersField = this.javaClass.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(this, this.modifiers and if (value) Modifier.FINAL else Modifier.FINAL.inv())
    }

fun Method.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Field.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Class<*>.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Class<*>.hasAtLeastOneOfModifiers(vararg modifiers: Int) = modifiers.any { it.and(this.modifiers) > 0 }

fun ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl.getActualArguments(): Array<Type> {
    val args = this.javaClass.getAllDeclaredFields().find { it.name == "actualArguments" } ?: return arrayOf()
    return args.let {
        it.isAccessible = true
        it.get(this) as Array<Type>
    }.also { args.isAccessible = false }
}