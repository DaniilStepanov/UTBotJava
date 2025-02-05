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

package org.utbot.quickcheck.internal.generator;

import org.utbot.framework.plugin.api.UtModel;
import org.utbot.quickcheck.generator.GenerationStatus;
import org.utbot.quickcheck.generator.Generator;
import org.utbot.quickcheck.generator.GeneratorConfigurationException;
import org.utbot.quickcheck.generator.Generators;
import org.utbot.quickcheck.internal.Items;
import org.utbot.quickcheck.internal.Weighted;
import org.utbot.quickcheck.random.SourceOfRandomness;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class CompositeGenerator extends Generator<Object> {
    private final List<Weighted<Generator<?>>> composed;

    CompositeGenerator(List<Weighted<Generator<?>>> composed) {
        super(Object.class);

        this.composed = new ArrayList<>(composed);
    }

    @Override public UtModel generate(
        SourceOfRandomness random,
        GenerationStatus status) {

        Generator<?> choice = Items.chooseWeighted(composed, random);
        return choice.generate(random, status);
    }

    @Override public boolean canShrink(Object larger) {
        return composed.stream()
            .map(w -> w.item)
            .anyMatch(g -> g.canShrink(larger));
    }

    @Override public List<Object> doShrink(
        SourceOfRandomness random,
        Object larger) {

        List<Weighted<Generator<?>>> shrinkers =
            composed.stream()
                .filter(w -> w.item.canShrink(larger))
                .collect(toList());

        Generator<?> choice = Items.chooseWeighted(shrinkers, random);
        return new ArrayList<>(choice.shrink(random, larger));
    }

    Generator<?> composed(int index) {
        return composed.get(index).item;
    }

    int numberOfComposedGenerators() {
        return composed.size();
    }

    @Override public void provide(Generators provided) {
        super.provide(provided);

        for (Weighted<Generator<?>> each : composed)
            each.item.provide(provided);
    }

    @Override public BigDecimal magnitude(Object value) {
        List<Weighted<Generator<?>>> shrinkers =
            composed.stream()
                .filter(w -> w.item.canShrink(value))
                .collect(toList());

        return shrinkers.get(0).item.magnitude(value);
    }

    @Override public void configure(AnnotatedType annotatedType) {
        List<Weighted<Generator<?>>> candidates = new ArrayList<>(composed);

        for (Iterator<Weighted<Generator<?>>> it = candidates.iterator();
            it.hasNext();) {

            try {
                it.next().item.configure(annotatedType);
            } catch (GeneratorConfigurationException e) {
                it.remove();
            }
        }

        installCandidates(candidates, annotatedType);
    }

    @Override public void configure(AnnotatedElement element) {
        List<Weighted<Generator<?>>> candidates = new ArrayList<>(composed);

        for (Iterator<Weighted<Generator<?>>> it = candidates.iterator();
            it.hasNext();) {

            try {
                it.next().item.configure(element);
            } catch (GeneratorConfigurationException e) {
                it.remove();
            }
        }

        installCandidates(candidates, element);
    }

    @Override public void addComponentGenerators(List<Generator<?>> newComponents) {
        for (Weighted<Generator<?>> each : composed) {
            each.item.addComponentGenerators(newComponents);
        }
    }

    private void installCandidates(
        List<Weighted<Generator<?>>> candidates,
        AnnotatedElement element) {

        if (candidates.isEmpty()) {
            throw new GeneratorConfigurationException(
                String.format(
                    "None of the candidate generators %s"
                        + " understands all of the configuration annotations %s",
                    candidateGeneratorDescriptions(),
                    configurationAnnotationNames(element)));
        }

        composed.clear();
        composed.addAll(candidates);
    }

    private String candidateGeneratorDescriptions() {
        return composed.stream()
            .map(w -> w.item.getClass().getName())
            .collect(toList())
            .toString();
    }

    private static List<String> configurationAnnotationNames(
        AnnotatedElement element) {

        return configurationAnnotationsOn(element).stream()
            .map(a -> a.annotationType().getName())
            .collect(toList());
    }
}
