package org.utbot.example.objects;

public class SemiImmutableClass {
    public final int a;
    public int b;

    public SemiImmutableClass(int a, int b) {
        this.a = a;
        this.b = b;
    }
}
