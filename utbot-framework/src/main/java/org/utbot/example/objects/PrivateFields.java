package org.utbot.example.objects;

public class PrivateFields {
    public boolean accessWithGetter(ClassWithPrivateField foo) {
        return foo.getA() == 1;
    }
}
