///*
// The MIT License
//
// Copyright (c) 2010-2021 Paul R. Holser, Jr.
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//*/
//
//package org.utbot.quickcheck.runner;
//
//import org.junit.internal.MethodSorter;
//import org.junit.runners.model.FrameworkField;
//import org.junit.runners.model.FrameworkMethod;
//import org.junit.runners.model.TestClass;
//
//import java.lang.annotation.Annotation;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.lang.reflect.Modifier;
//import java.util.*;
//import java.util.stream.Stream;
//
//import static java.util.stream.Collectors.toList;
//
///**
// * Extension of JUnit framework class for junit-quickcheck's discovery
// * of JUnit annotations/methods/fields.
// *
// * This is a member of a JUnit package so that it has access to
// * {@link FrameworkField}.
// */
//public class JUnitQuickcheckTestClass extends TestClass {
//    /**
//     * Makes a new instance, wrapping the given test class.
//     *
//     * @param testClass class with junit-quickcheck tests
//     */
//    JUnitQuickcheckTestClass(Class<?> testClass) {
//        super(testClass);
//    }
//
//    @Override protected void scanAnnotatedMembers(
//        Map<Class<? extends Annotation>, List<FrameworkMethod>> methods,
//        Map<Class<? extends Annotation>, List<FrameworkField>> fields) {
//
//        ancestry().forEachOrdered(c -> {
//            for (Method each : applicableMethodsOf(c)) {
//                addToAnnotationLists(new FrameworkMethod(each), methods);
//            }
//            for (Field each : applicableFieldsOf(c)) {
//                addToAnnotationLists(new FrameworkField(each), fields);
//            }
//        });
//    }
//
//    private static List<Method> applicableMethodsOf(Class<?> clazz) {
//        return Arrays.stream(MethodSorter.getDeclaredMethods(clazz))
//            .filter(m ->
//                !m.getDeclaringClass().isInterface()
//                    || m.isDefault()
//                    || Modifier.isStatic(m.getModifiers()))
//            .collect(toList());
//    }
//
//    private static Field[] applicableFieldsOf(Class<?> clazz) {
//        Field[] declaredFields = clazz.getDeclaredFields();
//        Arrays.sort(declaredFields, Comparator.comparing(Field::getName));
//        return declaredFields;
//    }
//
//    private Stream<Class<?>> ancestry() {
//        return superclassHierarchy(getJavaClass())
//            .flatMap(JUnitQuickcheckTestClass::interfaceAncestry);
//    }
//
//    private static Stream<Class<?>> superclassHierarchy(Class<?> clazz) {
//        List<Class<?>> hierarchy = new ArrayList<>();
//        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
//            hierarchy.add(c);
//        }
//        return hierarchy.stream();
//    }
//
//    private static Stream<Class<?>> interfaceAncestry(Class<?> clazz) {
//        return Stream.concat(
//            Stream.of(clazz),
//            Arrays.stream(clazz.getInterfaces())
//                .flatMap(JUnitQuickcheckTestClass::interfaceAncestry));
//    }
//}
