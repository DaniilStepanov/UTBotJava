package org.utbot.example.objects;

public class ObjectWithThrowableConstructor {
    public int x;

    public ObjectWithThrowableConstructor(int numerator, int denominator) {
        x = numerator / denominator;
    }
}
