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

package org.utbot.quickcheck.generator.java.time;

import org.utbot.engine.greyboxfuzzer.util.UtModelGenerator;
import org.utbot.framework.plugin.api.UtModel;
import org.utbot.quickcheck.generator.GenerationStatus;
import org.utbot.quickcheck.generator.Generator;
import org.utbot.quickcheck.generator.InRange;
import org.utbot.quickcheck.random.SourceOfRandomness;

import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.utbot.quickcheck.internal.Reflection.defaultValueOf;

/**
 * Produces values of type {@link ZonedDateTime}.
 */
public class ZonedDateTimeGenerator extends Generator<ZonedDateTime> {
    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    private ZonedDateTime min =
        ZonedDateTime.of(Year.MIN_VALUE, 1, 1, 0, 0, 0, 0, UTC_ZONE_ID);
    private ZonedDateTime max =
        ZonedDateTime.of(
            Year.MAX_VALUE, 12, 31, 23, 59, 59, 999_999_999, UTC_ZONE_ID);

    public ZonedDateTimeGenerator() {
        super(ZonedDateTime.class);
    }

    /**
     * <p>Tells this generator to produce values within a specified
     * {@linkplain InRange#min() minimum} and/or {@linkplain InRange#max()
     * maximum}, inclusive, with uniform distribution, down to the
     * nanosecond.</p>
     *
     * <p>If an endpoint of the range is not specified, the generator will use
     * dates with values of either {@link java.time.Instant#MIN} or
     * {@link java.time.Instant#MAX} and UTC zone as appropriate.</p>
     *
     * <p>{@link InRange#format()} describes
     * {@linkplain DateTimeFormatter#ofPattern(String) how the generator is to
     * interpret the range's endpoints}.</p>
     *
     * @param range annotation that gives the range's constraints
     */
    public void configure(InRange range) {
        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern(range.format());

        if (!defaultValueOf(InRange.class, "min").equals(range.min())) {
            min =
                ZonedDateTime.parse(range.min(), formatter)
                    .withZoneSameInstant(UTC_ZONE_ID);
        }
        if (!defaultValueOf(InRange.class, "max").equals(range.max())) {
            max =
                ZonedDateTime.parse(range.max(), formatter)
                    .withZoneSameInstant(UTC_ZONE_ID);
        }

        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                String.format("bad range, %s > %s", min, max));
        }
    }

    @Override public UtModel generate(
        SourceOfRandomness random,
        GenerationStatus status) {

        // Project the ZonedDateTime to an Instant for easy long-based
        // generation.
        return UtModelGenerator.getUtModelConstructor().construct(ZonedDateTime.ofInstant(
            random.nextInstant(min.toInstant(), max.toInstant()),
            UTC_ZONE_ID), ZonedDateTime.class);
    }
}
