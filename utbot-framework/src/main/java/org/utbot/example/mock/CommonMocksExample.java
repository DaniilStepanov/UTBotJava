package org.utbot.example.mock;

import org.utbot.example.mock.service.impl.ExampleClass;
import org.utbot.example.objects.ObjectWithFinalStatic;
import org.utbot.example.objects.RecursiveTypeClass;

public class CommonMocksExample {
    public Object mockInterfaceWithoutImplementors(InterfaceWithoutImplementors value) {
        return value.visit(this);
    }

    public int doNotMockHashCode(ExampleClass exampleClass) {
        return exampleClass.hashCode();
    }

    public boolean doNotMockEquals(ExampleClass fst, ExampleClass snd) {
        return fst.equals(snd);
    }

    public RecursiveTypeClass nextValue(RecursiveTypeClass node) {
        if (node.next == node) {
            return node;
        }

        node.next.value = node.value + 1;

        return node;
    }

    // We should not mock clinit section.
    public int clinitMockExample() {
        if (ObjectWithFinalStatic.keyValue == 0) {
            return ObjectWithFinalStatic.keyValue;
        } else {
            return -ObjectWithFinalStatic.keyValue;
        }
    }
}
