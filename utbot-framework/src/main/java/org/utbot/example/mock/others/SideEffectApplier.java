package org.utbot.example.mock.others;

import org.utbot.example.mock.service.impl.ExampleClass;

public class SideEffectApplier {
    public void applySideEffect(ExampleClass a) {
        a.field += 1;
    }
}
