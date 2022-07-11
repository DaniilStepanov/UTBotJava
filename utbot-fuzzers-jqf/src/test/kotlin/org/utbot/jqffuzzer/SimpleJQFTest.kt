package org.utbot.jqffuzzer

import org.junit.jupiter.api.Test

class SimpleJQFTest {

    @Test
    fun fuzzBoyerMoore() {
        JQFDriver.executeJQF("../utbot-sample/src/main/java/org/utbot/examples/strings/BoyerMoore.java")
        return
    }
}