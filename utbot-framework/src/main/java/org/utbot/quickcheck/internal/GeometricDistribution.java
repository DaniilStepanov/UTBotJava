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

package org.utbot.quickcheck.internal;

import org.utbot.quickcheck.random.SourceOfRandomness;

import static java.lang.Math.ceil;
import static java.lang.Math.log;

public class GeometricDistribution {
    public int sampleWithMean(double mean, SourceOfRandomness random) {
        return sample(probabilityOfMean(mean), random);
    }

    int sample(double p, SourceOfRandomness random) {
        ensureProbability(p);

        if (p == 1)
            return 0;

        double uniform = random.nextDouble();
        return (int) ceil(log(1 - uniform) / log(1 - p));
    }

    double probabilityOfMean(double mean) {
        if (mean <= 0) {
            throw new IllegalArgumentException(
                "Need a positive mean, got " + mean);
        }

        return 1 / mean;
    }

    private void ensureProbability(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException(
                "Need a probability in (0, 1], got " + p);
        }
    }
}
