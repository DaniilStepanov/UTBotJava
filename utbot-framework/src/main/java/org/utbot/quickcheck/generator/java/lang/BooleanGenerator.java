/*
 The MIT License

 Copyright (c) 2010-2021 Paul R. Holser, Jr.

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.utbot.quickcheck.generator.java.lang;

import org.utbot.engine.greyboxfuzzer.util.UtModelGenerator;
import org.utbot.framework.plugin.api.UtModel;
import org.utbot.quickcheck.generator.GenerationStatus;
import org.utbot.quickcheck.generator.Generator;
import org.utbot.quickcheck.random.SourceOfRandomness;

import java.math.BigDecimal;
import java.util.List;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Produces values of type {@code boolean} or {@link Boolean}.
 */
public class BooleanGenerator extends Generator<Boolean> {
    public BooleanGenerator() {
        super(singletonList(Boolean.class));
    }

    @Override public UtModel generate(
        SourceOfRandomness random,
        GenerationStatus status) {

        return UtModelGenerator.getUtModelConstructor().construct(random.nextBoolean(), Boolean.class);
    }

    @Override public List<Boolean> doShrink(
        SourceOfRandomness random,
        Boolean larger) {

        return larger ? singletonList(false) : emptyList();
    }

    @Override public BigDecimal magnitude(Object value) {
        return narrow(value) ? ONE : ZERO;
    }
}
