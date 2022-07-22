package org.utbot.example.mock.aliasing;

import org.utbot.api.mock.UtMock;
import org.utbot.example.mock.aliasing.parent.InterfaceFromAnotherPackage;

public class AliasingInParamsExample {
    int example(InterfaceFromAnotherPackage fst, ClassFromTheSamePackage snd, int x) {
        UtMock.assume(fst != null && snd != null);
        if (fst == snd) {
            return fst.foo(x); // unreachable with package based mock approach
        } else {
            return snd.foo(x);
        }
    }
}
