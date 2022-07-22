package org.utbot.example.mock;

import org.utbot.example.mock.others.Random;

public class MockStaticMethodExample {
    public int useStaticMethod() {
        int value = Random.nextRandomInt();
        if (value > 50) {
            return 100;
        }

        return 0;
    }
}
