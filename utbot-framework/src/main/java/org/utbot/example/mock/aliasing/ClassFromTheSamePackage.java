package org.utbot.example.mock.aliasing;

import org.utbot.example.mock.aliasing.parent.InterfaceFromAnotherPackage;

public class ClassFromTheSamePackage implements InterfaceFromAnotherPackage {
    @Override
    public int foo(int x) {
        return x;
    }
}
