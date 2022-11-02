package org.utbot.example;

import java.util.ArrayList;

class A<R> {
    public A(int a, R at) {
        this.a = a;
        this.at = at;
    }

    public class Lil {
        int a = 1;

        Lil(int a) {
            this.a = a;
        }
    }

    int a = 1;
    int c;
    R at;
    final int b = 1;

    public static A<Number> produceA(int a, Number b) {
        return new A<Number>(a, b);
    }
    public static A<Number> aInstance = new A<Number>(1, 13.0);

    //public int lol(R a, ArrayList<R> arr);
//    public static <R extends Number> A<R> getInstance1(R a, ArrayList<R> arr) {
//        return new B<R>(a, 777, arr);
//    }


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
//    public A(int a, R ba, R e, B<R> bInstance) {
//        this.e = e;
//        this.a = a;
//        this.bInstance = bInstance;
//    }
}
