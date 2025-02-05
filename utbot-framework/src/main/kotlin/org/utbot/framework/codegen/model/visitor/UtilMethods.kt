package org.utbot.framework.codegen.model.visitor

import org.utbot.framework.codegen.StaticImport
import org.utbot.framework.codegen.model.constructor.builtin.TestClassUtilMethodProvider
import org.utbot.framework.codegen.model.constructor.builtin.UtilMethodProvider
import org.utbot.framework.codegen.model.constructor.context.CgContextOwner
import org.utbot.framework.codegen.model.constructor.util.importIfNeeded
import org.utbot.framework.plugin.api.ClassId
import org.utbot.framework.plugin.api.CodegenLanguage
import org.utbot.framework.plugin.api.MethodId
import org.utbot.framework.plugin.api.MockFramework
import org.utbot.framework.plugin.api.util.id
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.Objects

private enum class Visibility(val text: String) {
    PRIVATE("private"),
    @Suppress("unused")
    PROTECTED("protected"),
    PUBLIC("public");

    infix fun by(language: CodegenLanguage): String {
        if (this == PUBLIC && language == CodegenLanguage.KOTLIN) {
            // public is default in Kotlin
            return ""
        }
        return "$text "
    }
}

internal fun UtilMethodProvider.utilMethodTextById(
    id: MethodId,
    mockFrameworkUsed: Boolean,
    mockFramework: MockFramework,
    codegenLanguage: CodegenLanguage
): Result<String> = runCatching {
    // If util methods are declared in the test class, then they are private. Otherwise, they are public.
    val visibility = if (this is TestClassUtilMethodProvider) Visibility.PRIVATE else Visibility.PUBLIC
    with(this) {
        when (id) {
            getUnsafeInstanceMethodId -> getUnsafeInstance(visibility, codegenLanguage)
            createInstanceMethodId -> createInstance(visibility, codegenLanguage)
            createArrayMethodId -> createArray(visibility, codegenLanguage)
            setFieldMethodId -> setField(visibility, codegenLanguage)
            setStaticFieldMethodId -> setStaticField(visibility, codegenLanguage)
            getFieldValueMethodId -> getFieldValue(visibility, codegenLanguage)
            getStaticFieldValueMethodId -> getStaticFieldValue(visibility, codegenLanguage)
            getEnumConstantByNameMethodId -> getEnumConstantByName(visibility, codegenLanguage)
            deepEqualsMethodId -> deepEquals(visibility, codegenLanguage, mockFrameworkUsed, mockFramework)
            arraysDeepEqualsMethodId -> arraysDeepEquals(visibility, codegenLanguage)
            iterablesDeepEqualsMethodId -> iterablesDeepEquals(visibility, codegenLanguage)
            streamsDeepEqualsMethodId -> streamsDeepEquals(visibility, codegenLanguage)
            mapsDeepEqualsMethodId -> mapsDeepEquals(visibility, codegenLanguage)
            hasCustomEqualsMethodId -> hasCustomEquals(visibility, codegenLanguage)
            getArrayLengthMethodId -> getArrayLength(visibility, codegenLanguage)
            else -> error("Unknown util method for class $this: $id")
        }
    }
}

private fun getEnumConstantByName(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object getEnumConstantByName(Class<?> enumClass, String name) throws IllegalAccessException {
                java.lang.reflect.Field[] fields = enumClass.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    String fieldName = field.getName();
                    if (field.isEnumConstant() && fieldName.equals(name)) {
                        field.setAccessible(true);
                        
                        return field.get(null);
                    }
                }
                
                return null;
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun getEnumConstantByName(enumClass: Class<*>, name: String): kotlin.Any? {
                val fields: kotlin.Array<java.lang.reflect.Field> = enumClass.declaredFields
                for (field in fields) {
                    val fieldName = field.name
                    if (field.isEnumConstant && fieldName == name) {
                        field.isAccessible = true
                        
                        return field.get(null)
                    }
                }
                
                return null
            }
        """
        }
    }.trimIndent()

private fun getStaticFieldValue(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object getStaticFieldValue(Class<?> clazz, String fieldName) throws IllegalAccessException, NoSuchFieldException {
                java.lang.reflect.Field field;
                Class<?> originClass = clazz;
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                        
                        return field.get(null);
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                } while (clazz != null);
        
                throw new NoSuchFieldException("Field '" + fieldName + "' not found on class " + originClass);
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun getStaticFieldValue(clazz: Class<*>, fieldName: String): kotlin.Any? {
                var currentClass: Class<*>? = clazz
                var field: java.lang.reflect.Field
                do {
                    try {
                        field = currentClass!!.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val modifiersField: java.lang.reflect.Field = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                        modifiersField.isAccessible = true
                        modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
                        
                        return field.get(null)
                    } catch (e: NoSuchFieldException) {
                        currentClass = currentClass!!.superclass
                    }
                } while (currentClass != null)
                
                throw NoSuchFieldException("Field '" + fieldName + "' not found on class " + clazz)
            }
        """
        }
    }.trimIndent()

private fun getFieldValue(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object getFieldValue(Object obj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
                Class<?> clazz = obj.getClass();
                java.lang.reflect.Field field;
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                        
                        return field.get(obj);
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                } while (clazz != null);
        
                throw new NoSuchFieldException("Field '" + fieldName + "' not found on class " + obj.getClass());
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun getFieldValue(any: kotlin.Any, fieldName: String): kotlin.Any? {
                var clazz: Class<*>? = any.javaClass
                var field: java.lang.reflect.Field
                do {
                    try {
                        field = clazz!!.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val modifiersField: java.lang.reflect.Field = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                        modifiersField.isAccessible = true
                        modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
                        
                        return field.get(any)
                    } catch (e: NoSuchFieldException) {
                        clazz = clazz!!.superclass
                    }
                } while (clazz != null)
                
                throw NoSuchFieldException("Field '" + fieldName + "' not found on class " + any.javaClass)
            }
        """
        }
    }.trimIndent()

private fun setStaticField(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static void setStaticField(Class<?> clazz, String fieldName, Object fieldValue) throws NoSuchFieldException, IllegalAccessException {
                java.lang.reflect.Field field;
    
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                    } catch (Exception e) {
                        clazz = clazz.getSuperclass();
                        field = null;
                    }
                } while (field == null);
                
                java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
    
                field.setAccessible(true);
                field.set(null, fieldValue);
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun setStaticField(defaultClass: Class<*>, fieldName: String, fieldValue: kotlin.Any?) {
                var field: java.lang.reflect.Field?
                var clazz = defaultClass
        
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName)
                    } catch (e: Exception) {
                        clazz = clazz.superclass
                        field = null
                    }
                } while (field == null)
        
                val modifiersField: java.lang.reflect.Field = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        
                field.isAccessible = true
                field.set(null, fieldValue)
            }
        """
        }
    }.trimIndent()

private fun setField(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static void setField(Object object, String fieldName, Object fieldValue) throws NoSuchFieldException, IllegalAccessException {
                Class<?> clazz = object.getClass();
                java.lang.reflect.Field field;
    
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                    } catch (Exception e) {
                        clazz = clazz.getSuperclass();
                        field = null;
                    }
                } while (field == null);
                
                java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
    
                field.setAccessible(true);
                field.set(object, fieldValue);
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun setField(any: kotlin.Any, fieldName: String, fieldValue: kotlin.Any?) {
                var clazz: Class<*> = any.javaClass
                var field: java.lang.reflect.Field?
                do {
                    try {
                        field = clazz.getDeclaredField(fieldName)
                    } catch (e: Exception) {
                        clazz = clazz.superclass
                        field = null
                    }
                } while (field == null)
        
                val modifiersField: java.lang.reflect.Field = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        
                field.isAccessible = true
                field.set(any, fieldValue)
            }
        """
        }
    }.trimIndent()

private fun createArray(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object[] createArray(String className, int length, Object... values) throws ClassNotFoundException {
                Object array = java.lang.reflect.Array.newInstance(Class.forName(className), length);
    
                for (int i = 0; i < values.length; i++) {
                    java.lang.reflect.Array.set(array, i, values[i]);
                }
                
                return (Object[]) array;
            }
        """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun createArray(
                className: String, 
                length: Int, 
                vararg values: kotlin.Any
            ): kotlin.Array<kotlin.Any?> {
                val array: kotlin.Any = java.lang.reflect.Array.newInstance(Class.forName(className), length)
                
                for (i in values.indices) {
                    java.lang.reflect.Array.set(array, i, values[i])
                }
                
                return array as kotlin.Array<kotlin.Any?>
            }
        """
        }
    }.trimIndent()

private fun createInstance(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object createInstance(String className) throws Exception {
                Class<?> clazz = Class.forName(className);
                return Class.forName("sun.misc.Unsafe").getDeclaredMethod("allocateInstance", Class.class)
                    .invoke(getUnsafeInstance(), clazz);
            }
            """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun createInstance(className: String): kotlin.Any? {
                val clazz: Class<*> = Class.forName(className)
                return Class.forName("sun.misc.Unsafe").getDeclaredMethod("allocateInstance", Class::class.java)
                    .invoke(getUnsafeInstance(), clazz)
            }
            """
        }
    }.trimIndent()

private fun getUnsafeInstance(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static Object getUnsafeInstance() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
                java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return f.get(null);
            }
            """
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun getUnsafeInstance(): kotlin.Any? {
                val f: java.lang.reflect.Field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
                f.isAccessible = true
                return f[null]
            }
            """
        }
    }.trimIndent()

/**
 * Mockito mock uses its own equals which we cannot rely on
 */
private fun isMockCondition(mockFrameworkUsed: Boolean, mockFramework: MockFramework): String {
    if (!mockFrameworkUsed) return ""

    return when (mockFramework) {
        MockFramework.MOCKITO -> " && !org.mockito.Mockito.mockingDetails(o1).isMock()"
        // in case we will add any other mock frameworks, newer Kotlin compiler versions
        // will report a non-exhaustive 'when', so we will not forget to support them here as well
    }
}

private fun deepEquals(
    visibility: Visibility,
    language: CodegenLanguage,
    mockFrameworkUsed: Boolean,
    mockFramework: MockFramework
): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            static class FieldsPair {
                final Object o1;
                final Object o2;
        
                public FieldsPair(Object o1, Object o2) {
                    this.o1 = o1;
                    this.o2 = o2;
                }
        
                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    FieldsPair that = (FieldsPair) o;
                    return java.util.Objects.equals(o1, that.o1) && java.util.Objects.equals(o2, that.o2);
                }
        
                @Override
                public int hashCode() {
                    return java.util.Objects.hash(o1, o2);
                }
            }
        
            ${visibility by language}static boolean deepEquals(Object o1, Object o2) {
                return deepEquals(o1, o2, new java.util.HashSet<>());
            }
        
            private static boolean deepEquals(Object o1, Object o2, java.util.Set<FieldsPair> visited) {
                visited.add(new FieldsPair(o1, o2));

                if (o1 == o2) {
                    return true;
                }
        
                if (o1 == null || o2 == null) {
                    return false;
                }
        
                if (o1 instanceof Iterable) {
                    if (!(o2 instanceof Iterable)) {
                        return false;
                    }
        
                    return iterablesDeepEquals((Iterable<?>) o1, (Iterable<?>) o2, visited);
                }
                
                if (o2 instanceof Iterable) {
                    return false;
                }
                
                if (o1 instanceof java.util.stream.Stream) {
                    if (!(o2 instanceof java.util.stream.Stream)) {
                        return false;
                    }
        
                    return streamsDeepEquals((java.util.stream.Stream<?>) o1, (java.util.stream.Stream<?>) o2, visited);
                }
        
                if (o2 instanceof java.util.stream.Stream) {
                    return false;
                }
        
                if (o1 instanceof java.util.Map) {
                    if (!(o2 instanceof java.util.Map)) {
                        return false;
                    }
        
                    return mapsDeepEquals((java.util.Map<?, ?>) o1, (java.util.Map<?, ?>) o2, visited);
                }
                
                if (o2 instanceof java.util.Map) {
                    return false;
                }
        
                Class<?> firstClass = o1.getClass();
                if (firstClass.isArray()) {
                    if (!o2.getClass().isArray()) {
                        return false;
                    }
        
                    // Primitive arrays should not appear here
                    return arraysDeepEquals(o1, o2, visited);
                }
        
                // common classes

                // check if class has custom equals method (including wrappers and strings)
                // It is very important to check it here but not earlier because iterables and maps also have custom equals 
                // based on elements equals 
                if (hasCustomEquals(firstClass)${isMockCondition(mockFrameworkUsed, mockFramework)}) {
                    return o1.equals(o2);
                }
        
                // common classes without custom equals, use comparison by fields
                final java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
                while (firstClass != Object.class) {
                    fields.addAll(java.util.Arrays.asList(firstClass.getDeclaredFields()));
                    // Interface should not appear here
                    firstClass = firstClass.getSuperclass();
                }
        
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    try {
                        final Object field1 = field.get(o1);
                        final Object field2 = field.get(o2);
                        if (!visited.contains(new FieldsPair(field1, field2)) && !deepEquals(field1, field2, visited)) {
                            return false;
                        }
                    } catch (IllegalArgumentException e) {
                        return false;
                    } catch (IllegalAccessException e) {
                        // should never occur because field was set accessible
                        return false;
                    }
                }
        
                return true;
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun deepEquals(o1: kotlin.Any?, o2: kotlin.Any?): Boolean = deepEquals(o1, o2, hashSetOf())
            
            private fun deepEquals(
                o1: kotlin.Any?, 
                o2: kotlin.Any?, 
                visited: kotlin.collections.MutableSet<kotlin.Pair<kotlin.Any?, kotlin.Any?>>
            ): Boolean {
                visited += o1 to o2
                
                if (o1 === o2) return true
        
                if (o1 == null || o2 == null) return false
        
                if (o1 is kotlin.collections.Iterable<*>) {
                    return if (o2 !is kotlin.collections.Iterable<*>) false else iterablesDeepEquals(o1, o2, visited)
                }
                
                if (o2 is kotlin.collections.Iterable<*>) return false
                
                if (o1 is java.util.stream.Stream<*>) {
                    return if (o2 !is java.util.stream.Stream<*>) false else streamsDeepEquals(o1, o2, visited)
                }
                
                if (o2 is java.util.stream.Stream<*>) return false
        
                if (o1 is kotlin.collections.Map<*, *>) {
                    return if (o2 !is kotlin.collections.Map<*, *>) false else mapsDeepEquals(o1, o2, visited)
                }
                
                if (o2 is kotlin.collections.Map<*, *>) return false
        
                var firstClass: Class<*> = o1.javaClass
                if (firstClass.isArray) {
                    return if (!o2.javaClass.isArray) { 
                        false
                    } else { 
                        arraysDeepEquals(o1, o2, visited)
                    }
                }
        
                // check if class has custom equals method (including wrappers and strings)
                // It is very important to check it here but not earlier because iterables and maps also have custom equals
                // based on elements equals
                if (hasCustomEquals(firstClass)${isMockCondition(mockFrameworkUsed, mockFramework)}) { 
                    return o1 == o2
                }
        
                // common classes without custom equals, use comparison by fields
                val fields: kotlin.collections.MutableList<java.lang.reflect.Field> = mutableListOf()
                while (firstClass != kotlin.Any::class.java) {
                    fields += listOf(*firstClass.declaredFields)
                    // Interface should not appear here
                    firstClass = firstClass.superclass
                }
        
                for (field in fields) {
                    field.isAccessible = true
                    try {
                        val field1 = field[o1]
                        val field2 = field[o2]
                        if ((field1 to field2) !in visited && !deepEquals(field1, field2, visited)) return false
                    } catch (e: IllegalArgumentException) {
                        return false
                    } catch (e: IllegalAccessException) {
                        // should never occur
                        return false
                    }
                }
        
                return true
            }
            """.trimIndent()
        }
    }

private fun arraysDeepEquals(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static boolean arraysDeepEquals(Object arr1, Object arr2, java.util.Set<FieldsPair> visited) {
                final int length = java.lang.reflect.Array.getLength(arr1);
                if (length != java.lang.reflect.Array.getLength(arr2)) {
                    return false;
                }
        
                for (int i = 0; i < length; i++) {
                    if (!deepEquals(java.lang.reflect.Array.get(arr1, i), java.lang.reflect.Array.get(arr2, i), visited)) {
                        return false;
                    }
                }
        
                return true;
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun arraysDeepEquals(
                arr1: kotlin.Any?, 
                arr2: kotlin.Any?, 
                visited: kotlin.collections.MutableSet<kotlin.Pair<kotlin.Any?, kotlin.Any?>>
            ): Boolean {
                val size = java.lang.reflect.Array.getLength(arr1)
                if (size != java.lang.reflect.Array.getLength(arr2)) return false
        
                for (i in 0 until size) {
                    if (!deepEquals(java.lang.reflect.Array.get(arr1, i), java.lang.reflect.Array.get(arr2, i), visited)) { 
                        return false
                    }
                }
        
                return true
            }
            """.trimIndent()
        }
    }

private fun iterablesDeepEquals(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static boolean iterablesDeepEquals(Iterable<?> i1, Iterable<?> i2, java.util.Set<FieldsPair> visited) {
                final java.util.Iterator<?> firstIterator = i1.iterator();
                final java.util.Iterator<?> secondIterator = i2.iterator();
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    if (!deepEquals(firstIterator.next(), secondIterator.next(), visited)) {
                        return false;
                    }
                }
        
                if (firstIterator.hasNext()) {
                    return false;
                }
        
                return !secondIterator.hasNext();
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun iterablesDeepEquals(
                i1: Iterable<*>, 
                i2: Iterable<*>, 
                visited: kotlin.collections.MutableSet<kotlin.Pair<kotlin.Any?, kotlin.Any?>>
            ): Boolean {
                val firstIterator = i1.iterator()
                val secondIterator = i2.iterator()
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    if (!deepEquals(firstIterator.next(), secondIterator.next(), visited)) return false
                }
        
                return if (firstIterator.hasNext()) false else !secondIterator.hasNext()
            }
            """.trimIndent()
        }
    }

private fun streamsDeepEquals(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static boolean streamsDeepEquals(
                java.util.stream.Stream<?> s1, 
                java.util.stream.Stream<?> s2, 
                java.util.Set<FieldsPair> visited
            ) {
                final java.util.Iterator<?> firstIterator = s1.iterator();
                final java.util.Iterator<?> secondIterator = s2.iterator();
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    if (!deepEquals(firstIterator.next(), secondIterator.next(), visited)) {
                        return false;
                    }
                }
        
                if (firstIterator.hasNext()) {
                    return false;
                }
        
                return !secondIterator.hasNext();
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun streamsDeepEquals(
                s1: java.util.stream.Stream<*>, 
                s2: java.util.stream.Stream<*>, 
                visited: kotlin.collections.MutableSet<kotlin.Pair<kotlin.Any?, kotlin.Any?>>
            ): Boolean {
                val firstIterator = s1.iterator()
                val secondIterator = s2.iterator()
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    if (!deepEquals(firstIterator.next(), secondIterator.next(), visited)) return false
                }
        
                return if (firstIterator.hasNext()) false else !secondIterator.hasNext()
            }
            """.trimIndent()
        }
    }

private fun mapsDeepEquals(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static boolean mapsDeepEquals(
                java.util.Map<?, ?> m1, 
                java.util.Map<?, ?> m2, 
                java.util.Set<FieldsPair> visited
            ) {
                final java.util.Iterator<? extends java.util.Map.Entry<?, ?>> firstIterator = m1.entrySet().iterator();
                final java.util.Iterator<? extends java.util.Map.Entry<?, ?>> secondIterator = m2.entrySet().iterator();
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    final java.util.Map.Entry<?, ?> firstEntry = firstIterator.next();
                    final java.util.Map.Entry<?, ?> secondEntry = secondIterator.next();
        
                    if (!deepEquals(firstEntry.getKey(), secondEntry.getKey(), visited)) {
                        return false;
                    }
        
                    if (!deepEquals(firstEntry.getValue(), secondEntry.getValue(), visited)) {
                        return false;
                    }
                }
        
                if (firstIterator.hasNext()) {
                    return false;
                }
        
                return !secondIterator.hasNext();
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun mapsDeepEquals(
                m1: kotlin.collections.Map<*, *>, 
                m2: kotlin.collections.Map<*, *>, 
                visited: kotlin.collections.MutableSet<kotlin.Pair<kotlin.Any?, kotlin.Any?>>
            ): Boolean {
                val firstIterator = m1.entries.iterator()
                val secondIterator = m2.entries.iterator()
                while (firstIterator.hasNext() && secondIterator.hasNext()) {
                    val firstEntry = firstIterator.next()
                    val secondEntry = secondIterator.next()
        
                    if (!deepEquals(firstEntry.key, secondEntry.key, visited)) return false
        
                    if (!deepEquals(firstEntry.value, secondEntry.value, visited)) return false
                }
        
                return if (firstIterator.hasNext()) false else !secondIterator.hasNext()
            }
            """.trimIndent()
        }
    }

private fun hasCustomEquals(visibility: Visibility, language: CodegenLanguage): String =
    when (language) {
        CodegenLanguage.JAVA -> {
            """
            ${visibility by language}static boolean hasCustomEquals(Class<?> clazz) {
                while (!Object.class.equals(clazz)) {
                    try {
                        clazz.getDeclaredMethod("equals", Object.class);
                        return true;
                    } catch (Exception e) { 
                        // Interface should not appear here
                        clazz = clazz.getSuperclass();
                    }
                }
        
                return false;
            }
            """.trimIndent()
        }
        CodegenLanguage.KOTLIN -> {
            """
            ${visibility by language}fun hasCustomEquals(clazz: Class<*>): Boolean {
                var c = clazz
                while (kotlin.Any::class.java != c) {
                    try {
                        c.getDeclaredMethod("equals", kotlin.Any::class.java)
                        return true
                    } catch (e: Exception) {
                        // Interface should not appear here
                        c = c.superclass
                    }
                }
                return false
            }
            """.trimIndent()
        }
    }

private fun getArrayLength(visibility: Visibility, language: CodegenLanguage) =
    when (language) {
        CodegenLanguage.JAVA ->
            """
            ${visibility by language}static int getArrayLength(Object arr) {
                return java.lang.reflect.Array.getLength(arr);
            }
            """.trimIndent()
        CodegenLanguage.KOTLIN ->
            """
            ${visibility by language}fun getArrayLength(arr: kotlin.Any?): Int = java.lang.reflect.Array.getLength(arr)
            """.trimIndent()
    }

internal fun CgContextOwner.importUtilMethodDependencies(id: MethodId) {
    // if util methods come from a separate UtUtils class and not from the test class,
    // then we don't need to import any other methods, hence we return from method
    val utilMethodProvider = utilMethodProvider as? TestClassUtilMethodProvider ?: return
    for (classId in utilMethodProvider.regularImportsByUtilMethod(id, codegenLanguage)) {
        importIfNeeded(classId)
    }
    for (methodId in utilMethodProvider.staticImportsByUtilMethod(id)) {
        collectedImports += StaticImport(methodId.classId.canonicalName, methodId.name)
    }
}

private fun TestClassUtilMethodProvider.regularImportsByUtilMethod(
    id: MethodId,
    codegenLanguage: CodegenLanguage
): List<ClassId> {
    val fieldClassId = Field::class.id
    return when (id) {
        getUnsafeInstanceMethodId -> listOf(fieldClassId)
        createInstanceMethodId -> listOf(java.lang.reflect.InvocationTargetException::class.id)
        createArrayMethodId -> listOf(java.lang.reflect.Array::class.id)
        setFieldMethodId -> listOf(fieldClassId, Modifier::class.id)
        setStaticFieldMethodId -> listOf(fieldClassId, Modifier::class.id)
        getFieldValueMethodId -> listOf(fieldClassId, Modifier::class.id)
        getStaticFieldValueMethodId -> listOf(fieldClassId, Modifier::class.id)
        getEnumConstantByNameMethodId -> listOf(fieldClassId)
        deepEqualsMethodId -> when (codegenLanguage) {
            CodegenLanguage.JAVA -> listOf(
                Objects::class.id,
                Iterable::class.id,
                Map::class.id,
                List::class.id,
                ArrayList::class.id,
                Set::class.id,
                HashSet::class.id,
                fieldClassId,
                Arrays::class.id
            )
            CodegenLanguage.KOTLIN -> listOf(fieldClassId, Arrays::class.id)
        }
        arraysDeepEqualsMethodId -> when (codegenLanguage) {
            CodegenLanguage.JAVA -> listOf(java.lang.reflect.Array::class.id, Set::class.id)
            CodegenLanguage.KOTLIN -> listOf(java.lang.reflect.Array::class.id)
        }
        iterablesDeepEqualsMethodId -> when (codegenLanguage) {
            CodegenLanguage.JAVA -> listOf(Iterable::class.id, Iterator::class.id, Set::class.id)
            CodegenLanguage.KOTLIN -> emptyList()
        }
        streamsDeepEqualsMethodId -> when (codegenLanguage) {
            CodegenLanguage.JAVA -> listOf(java.util.stream.Stream::class.id, Set::class.id)
            CodegenLanguage.KOTLIN -> emptyList()
        }
        mapsDeepEqualsMethodId -> when (codegenLanguage) {
            CodegenLanguage.JAVA -> listOf(Map::class.id, Iterator::class.id, Set::class.id)
            CodegenLanguage.KOTLIN -> emptyList()
        }
        hasCustomEqualsMethodId -> emptyList()
        getArrayLengthMethodId -> listOf(java.lang.reflect.Array::class.id)
        else -> error("Unknown util method for class $this: $id")
    }
}

// Note: for now always returns an empty list, because no util method
// requires static imports, but this may change in the future
@Suppress("unused", "unused_parameter")
private fun TestClassUtilMethodProvider.staticImportsByUtilMethod(id: MethodId): List<MethodId> = emptyList()