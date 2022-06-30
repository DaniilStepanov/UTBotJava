package org.utbot.example;

public class A<T> {
    int a = 1;
    final int b = 1;
    static int c = 2;
    int d;
    T e;

    public static final A<Number> numberA = new A<Number>(1, 3L);
    public static final A<Integer> intA = new A<Integer>(1, 2);
    public static final A<String> strA = new A<String>(1, "a");

    public static A<Object> getInstance() {
        return new A<Object>(1, null);
    }

    public static A<Integer> getIntegerInstance() {
        return new A<Integer>(1, 3);
    }

    private A() {}
    private A(int a, T e) {
        this.e = e;
        this.a = a;
    }
}
