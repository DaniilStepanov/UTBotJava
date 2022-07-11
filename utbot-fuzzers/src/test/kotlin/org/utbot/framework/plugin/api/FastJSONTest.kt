package org.utbot.framework.plugin.api

import org.junit.jupiter.api.Test
import kotlin.reflect.KFunction

class FastJSONTest {

    @Test
    fun testFastJSON() {
        executeTest(
            "utbot-fuzzers/src/test/resources/testProjects/fastjson/src/main/java/",
            "com.alibaba.fastjson.asm.ByteVector"
        )
    }

    fun executeTest(pathToSource: String, pathToMethod: String) {

    }
}