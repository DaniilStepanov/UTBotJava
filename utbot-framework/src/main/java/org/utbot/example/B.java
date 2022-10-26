package org.utbot.example;

import java.util.ArrayList;

public class B<T extends Number> implements A<T> {

    public T a;
    public int b;
    public ArrayList<T> c;

    public B(T a, int b, ArrayList<T> c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public int lol(T a, ArrayList<T> arr) {
        return 0;
    }
}
