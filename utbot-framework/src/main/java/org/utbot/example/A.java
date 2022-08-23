package org.utbot.example;

import java.util.ArrayList;

public class A<R extends Number> {

    public class Lil {
        int a = 1;

        Lil(int a) {
            this.a = a;
        }
    }

    int a = 1;
    final int b = 1;
    static int c = 2;
    int d;
    R e;
    B<R> bInstance;

    public static <T, E extends Number> A<E> getInstance1(E a, ArrayList<Integer> arr, Class<? extends T> cl) {
        return new A<E>(1, null, null, null);
    }


    //    public static final A<Number> numberA = new A<Number>(1, 3L, null);
//    public static final A<Integer> intA = new A<Integer>(1, 2, null);
//    public static final A<String> strA = new A<String>(1, "a", null);
//
//    public static A<Object> getInstance() {
//        return new A<Object>(1, null, null);
//    }

    //public static A<Integer> getIntegerInstance() {
//        return new A<Integer>(1, 3);
//    }
//    public static A<Integer> getIntegerInstance(Integer a, B<Integer> bInstance) {
//        return new A<Integer>(a, 3, bInstance);
//    }

    //    private A() {}
    public A(int a, R ba, R e, B<R> bInstance) {
        this.e = e;
        this.a = a;
        this.bInstance = bInstance;
    }
}
