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

package org.utbot.quickcheck.generator.java.util;
import org.utbot.engine.greyboxfuzzer.util.UtModelGenerator;
import org.utbot.framework.plugin.api.UtModel;

import org.utbot.framework.plugin.api.UtNullModel;
import org.utbot.quickcheck.generator.GenerationStatus;
import org.utbot.quickcheck.generator.Generator;
import org.utbot.quickcheck.generator.InRange;
import org.utbot.quickcheck.generator.java.lang.IntegerGenerator;
import org.utbot.quickcheck.random.SourceOfRandomness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toList;
import static org.utbot.external.api.UtModelFactoryKt.classIdForType;

/**
 * Produces values of type {@link OptionalInt}.
 */
public class OptionalIntGenerator extends Generator<OptionalInt> {
    private final IntegerGenerator integers = new IntegerGenerator();

    public OptionalIntGenerator() {
        super(OptionalInt.class);
    }

    /**
     * Tells this generator to produce values, when
     * {@link OptionalInt#isPresent() present}, within a specified minimum
     * and/or maximum, inclusive, with uniform distribution.
     *
     * {@link InRange#min} and {@link InRange#max} take precedence over
     * {@link InRange#minInt()} and {@link InRange#maxInt()}, if non-empty.
     *
     * @param range annotation that gives the range's constraints
     */
    public void configure(InRange range) {
        integers.configure(range);
    }

    @Override public UtModel generate(
        SourceOfRandomness random,
        GenerationStatus status) {

        double trial = random.nextDouble();
        final OptionalInt generated = trial < 0.25 ?
                OptionalInt.empty()
                : OptionalInt.of(integers.generateValue(random, status));

        return UtModelGenerator.getUtModelConstructor().construct(generated, OptionalInt.class);
    }

    @Override public List<OptionalInt> doShrink(
        SourceOfRandomness random,
        OptionalInt larger) {

        if (!larger.isPresent())
            return new ArrayList<>();

        List<OptionalInt> shrinks = new ArrayList<>();
        shrinks.add(OptionalInt.empty());
        shrinks.addAll(
            integers.shrink(random, larger.getAsInt())
                .stream()
                .map(OptionalInt::of)
                .collect(toList()));
        return shrinks;
    }

    @Override public BigDecimal magnitude(Object value) {
        OptionalInt narrowed = narrow(value);

        return narrowed.isPresent()
            ? BigDecimal.valueOf(narrowed.getAsInt())
            : ZERO;
    }
}
