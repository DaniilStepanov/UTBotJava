package org.utbot.engine.greyboxfuzzer.util

import org.utbot.engine.greyboxfuzzer.generator.UserClassesGenerator
import org.utbot.framework.codegen.model.constructor.tree.isStatic
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
import java.lang.reflect.Constructor
import java.lang.reflect.Array as RArray
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.random.Random
import kotlin.system.exitProcess

fun Class<*>.getAllDeclaredFields(): List<Field> {
    val res = mutableListOf<Field>()
    var current: Class<*>? = this
    while (current != null) {
        try {
            res.addAll(current.declaredFields)
        } catch (_: Error) {

        }
        current = current.superclass
    }
    return res
}

fun Class<*>.getAllDeclaredFieldsRecursive(instance: Any): List<Pair<Any, Field>> {
    val queue = ArrayDeque<Pair<Any, Field>>()
    val res = mutableListOf<Pair<Any, Field>>()
    this.getAllDeclaredFields().forEach { queue.add(instance to it) }
    while (queue.isNotEmpty()) {
        val curField = queue.removeFirst()
        val fieldValue = curField.second.getFieldValue(curField.first)
        if (fieldValue != null && res.all { it.second != curField.second }) {
            res.add(curField)
            curField.second.type.toClass()?.getAllDeclaredFields()?.forEach { queue.add(fieldValue to it) }
        }
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

fun Class<*>.getDeclaredFieldEvenNested(name: String) = this.getAllDeclaredFields().find { it.name == name }
fun Class<*>.getAllSuperClassesAndInterfaces(): List<Class<*>> {
    val res = mutableListOf<Class<*>>()
    var superClass = this.superclass
    res.add(superClass)
    res.addAll(this.interfaces)
    while (superClass != null) {
        res.addAll(superClass.interfaces)
        superClass = superClass.superclass?.also { res.add(it) }
    }
    return res
}

fun Field.getFieldValue(instance: Any?): Any? {
    val oldAccessibleFlag = this.isAccessible
    this.isAccessible = true
    val fixedInstance =
        if (this.isStatic) {
            null
        } else instance
    return when (this.type) {
        Boolean::class.javaPrimitiveType -> this.getBoolean(fixedInstance)
        Byte::class.javaPrimitiveType -> this.getByte(fixedInstance)
        Char::class.javaPrimitiveType -> this.getChar(fixedInstance)
        Short::class.javaPrimitiveType -> this.getShort(fixedInstance)
        Int::class.javaPrimitiveType -> this.getInt(fixedInstance)
        Long::class.javaPrimitiveType -> this.getLong(fixedInstance)
        Float::class.javaPrimitiveType -> this.getFloat(fixedInstance)
        Double::class.javaPrimitiveType -> this.getDouble(fixedInstance)
        else -> this.get(fixedInstance)
    }.also { this.isAccessible = oldAccessibleFlag }
}

fun Field.setFieldValue(instance: Any?, fieldValue: Any?) {
    val oldAccessibleFlag = this.isAccessible
    this.isAccessible = true
    val fixedInstance =
        if (this.isStatic) {
            null
        } else instance
    when (this.type) {
        Boolean::class.javaPrimitiveType -> this.setBoolean(fixedInstance, fieldValue as Boolean)
        Byte::class.javaPrimitiveType -> this.setByte(fixedInstance, fieldValue as Byte)
        Char::class.javaPrimitiveType -> this.setChar(fixedInstance, fieldValue as Char)
        Short::class.javaPrimitiveType -> this.setShort(fixedInstance, fieldValue as Short)
        Int::class.javaPrimitiveType -> this.setInt(fixedInstance, fieldValue as Int)
        Long::class.javaPrimitiveType -> this.setLong(fixedInstance, fieldValue as Long)
        Float::class.javaPrimitiveType -> this.setFloat(fixedInstance, fieldValue as Float)
        Double::class.javaPrimitiveType -> this.setDouble(fixedInstance, fieldValue as Double)
        else -> this.set(fixedInstance, fieldValue)
    }.also { this.isAccessible = oldAccessibleFlag }
}

fun Field.setDefaultValue(instance: Any?) {
    val oldAccessibleFlag = this.isAccessible
    this.isAccessible = true
    this.isFinal = false
    val fixedInstance =
        if (this.isStatic) {
            null
        } else instance
    when (this.type) {
        Boolean::class.javaPrimitiveType -> this.setBoolean(fixedInstance, false)
        Byte::class.javaPrimitiveType -> this.setByte(fixedInstance, 0)
        Char::class.javaPrimitiveType -> this.setChar(fixedInstance, '\u0000')
        Short::class.javaPrimitiveType -> this.setShort(fixedInstance, 0)
        Int::class.javaPrimitiveType -> this.setInt(fixedInstance, 0)
        Long::class.javaPrimitiveType -> this.setLong(fixedInstance, 0)
        Float::class.javaPrimitiveType -> this.setFloat(fixedInstance, 0.0f)
        Double::class.javaPrimitiveType -> this.setDouble(fixedInstance, 0.0)
        else -> try {
            this.set(fixedInstance, null)
        } catch (e: Throwable) {
            setNullToFieldUsingUnsafe(this)
        }
    }.also { this.isAccessible = oldAccessibleFlag }
}

private fun setNullToFieldUsingUnsafe(field: Field) {
    if (field.isStatic) {
        val cl = UserClassesGenerator.UNSAFE.staticFieldBase(field)
        val offset = UserClassesGenerator.UNSAFE.staticFieldOffset(field)
        UserClassesGenerator.UNSAFE.getAndSetObject(cl, offset, null)
    }
}

fun Type.toClass(): Class<*>? =
    try {
        when (this) {
            is ParameterizedTypeImpl -> this.rawType
            is ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl -> this.rawType.toClass()
            is GenericArrayTypeImpl -> this.genericComponentType.toClass()
            else -> this as Class<*>
        }
    } catch (e: Exception) {
        null
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

fun Method.isStatic() = modifiers.and(Modifier.STATIC) > 0
fun Field.isStatic() = modifiers.and(Modifier.STATIC) > 0
fun Method.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Field.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Class<*>.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Constructor<*>.hasModifiers(vararg modifiers: Int) = modifiers.all { it.and(this.modifiers) > 0 }
fun Constructor<*>.hasAtLeastOneOfModifiers(vararg modifiers: Int) = modifiers.any { it.and(this.modifiers) > 0 }
fun Class<*>.hasAtLeastOneOfModifiers(vararg modifiers: Int) = modifiers.any { it.and(this.modifiers) > 0 }
fun Field.hasAtLeastOneOfModifiers(vararg modifiers: Int) = modifiers.any { it.and(this.modifiers) > 0 }

fun ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl.getActualArguments(): Array<Type> {
    val args = this.javaClass.getAllDeclaredFields().find { it.name == "actualArguments" } ?: return arrayOf()
    return args.let {
        it.isAccessible = true
        it.get(this) as Array<Type>
    }.also { args.isAccessible = false }
}

fun List<Constructor<*>>.chooseRandomConstructor() =
    if (Random.getTrue(75)) {
        this.shuffled().minByOrNull { it.parameterCount }
    } else this.randomOrNull()

fun List<Method>.chooseRandomMethodToGenerateInstance() =
    if (Random.getTrue(75)) {
        this.shuffled().minByOrNull { it.parameterCount }
    } else this.randomOrNull()

fun generateParameterizedTypeImpl(
    clazz: Class<*>,
    actualTypeParameters: Array<Type>
): ParameterizedTypeImpl {
    val constructor = ParameterizedTypeImpl::class.java.declaredConstructors.first()
    constructor.isAccessible = true
    return constructor.newInstance(clazz, actualTypeParameters, null) as ParameterizedTypeImpl
}