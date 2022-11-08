package org.utbot.engine.zestfuzzer.util

import org.utbot.engine.zestfuzzer.util.kcheck.nextInRange
import java.util.*


fun kotlin.random.Random.getTrue(prob: Int) = Random().nextInRange(0, 100) < prob