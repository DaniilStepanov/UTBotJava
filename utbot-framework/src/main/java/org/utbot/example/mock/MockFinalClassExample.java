package org.utbot.example.mock;

import org.utbot.example.mock.others.FinalClass;

public class MockFinalClassExample {
    FinalClass intProvider;

    int useFinalClass() {
        int x = intProvider.provideInt();
        if (x == 1) {
            return 1;
        } else {
            return 2;
        }
    }

}
