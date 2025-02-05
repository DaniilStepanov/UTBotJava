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
import org.utbot.framework.concrete.UtModelConstructor;
import org.utbot.framework.plugin.api.*;

import org.utbot.framework.plugin.api.util.IdUtilKt;
import org.utbot.quickcheck.generator.*;
import org.utbot.quickcheck.internal.Lists;
import org.utbot.quickcheck.random.SourceOfRandomness;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.utbot.external.api.UtModelFactoryKt.classIdForType;
import static org.utbot.framework.plugin.api.util.IdUtilKt.methodId;
import static org.utbot.quickcheck.internal.Lists.removeFrom;
import static org.utbot.quickcheck.internal.Lists.shrinksOfOneItem;
import static org.utbot.quickcheck.internal.Ranges.Type.INTEGRAL;
import static org.utbot.quickcheck.internal.Ranges.checkRange;
import static org.utbot.quickcheck.internal.Reflection.findConstructor;
import static org.utbot.quickcheck.internal.Reflection.instantiate;
import static org.utbot.quickcheck.internal.Sequences.halving;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * <p>Base class for generators of {@link Collection}s.</p>
 *
 * <p>The generated collection has a number of elements limited by
 * {@link GenerationStatus#size()}, or else by the attributes of a {@link Size}
 * marking. The individual elements will have a type corresponding to the
 * collection's type argument.</p>
 *
 * @param <T> the type of collection generated
 */
public abstract class CollectionGenerator<T extends Collection>
        extends ComponentizedGenerator<T> {

    private Size sizeRange;
    private boolean distinct;

    protected CollectionGenerator(Class<T> type) {
        super(type);
    }

    /**
     * <p>Tells this generator to add elements to the generated collection
     * a number of times within a specified minimum and/or maximum, inclusive,
     * chosen with uniform distribution.</p>
     *
     * <p>Note that some kinds of collections disallow duplicates, so the
     * number of elements added may not be equal to the collection's
     * {@link Collection#size()}.</p>
     *
     * @param size annotation that gives the size constraints
     */
    public void configure(Size size) {
        this.sizeRange = size;
        checkRange(INTEGRAL, size.min(), size.max());
    }

    /**
     * Tells this generator to add elements which are distinct from each other.
     *
     * @param distinct Generated elements will be distinct if this param is
     *                 not null
     */
    public void configure(Distinct distinct) {
        setDistinct(distinct != null);
    }

    protected final void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    @SuppressWarnings("unchecked")
    @Override
    public UtModel generate(
            SourceOfRandomness random,
            GenerationStatus status) {

//        UtCompositeModel res = new UtCompositeModel(
//
//        )
        UtModelConstructor modelConstructor = UtModelGenerator.getUtModelConstructor();
        Class<T> collectionType = types().get(0);
        ClassId collectionClassId = classIdForType(collectionType);
        ExecutableId collectionConstructorId = new ConstructorId(collectionClassId, new ArrayList<>());
        int genId = modelConstructor.computeUnusedIdAndUpdate();
        ArrayList<UtStatementModel> instantiationChain = new ArrayList<>();
        ArrayList<UtStatementModel> modificationChain = new ArrayList<>();

        UtAssembleModel collectionAssembleModel = new UtAssembleModel(
                genId,
                collectionClassId,
                collectionConstructorId.getName() + "#" + genId,
                instantiationChain,
                modificationChain,
                null,
                null
        );
        instantiationChain.add(new UtExecutableCallModel(null, collectionConstructorId, List.of(), collectionAssembleModel));
        int size = size(random, status);

        Generator<?> generator = componentGenerators().get(0);
        Method m;
        try {
            m = Collection.class.getMethod("add", Object.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        MethodId methodId = IdUtilKt.getExecutableId(m);

        //java.util.Collections::class.java.methodCall
        for (int i = 0; i < size; ++i) {
            UtModel item = generator.generate(random, status);
            modificationChain.add(new UtExecutableCallModel(collectionAssembleModel, methodId, List.of(item), null));
        }
        return collectionAssembleModel;
    }

    @Override
    public List<T> doShrink(SourceOfRandomness random, T larger) {
        @SuppressWarnings("unchecked")
        List<Object> asList = new ArrayList<>(larger);

        List<T> shrinks = new ArrayList<>(removals(asList));

        @SuppressWarnings("unchecked")
        Shrink<Object> generator =
                (Shrink<Object>) componentGenerators().get(0);

        Stream<List<Object>> oneItemShrinks =
                shrinksOfOneItem(random, asList, generator)
                        .stream();
        if (distinct)
            oneItemShrinks = oneItemShrinks.filter(Lists::isDistinct);

        shrinks.addAll(
                oneItemShrinks
                        .map(this::convert)
                        .filter(this::inSizeRange)
                        .collect(toList()));

        return shrinks;
    }

    @Override
    public int numberOfNeededComponents() {
        return 1;
    }

    @Override
    public BigDecimal magnitude(Object value) {
        Collection<?> narrowed = narrow(value);

        if (narrowed.isEmpty())
            return ZERO;

        BigDecimal elementsMagnitude =
                narrowed.stream()
                        .map(e -> componentGenerators().get(0).magnitude(e))
                        .reduce(ZERO, BigDecimal::add);
        return BigDecimal.valueOf(narrowed.size()).multiply(elementsMagnitude);
    }

    protected final T empty() {
        return instantiate(findConstructor(types().get(0)));
    }

    private boolean inSizeRange(T items) {
        return sizeRange == null
                || (items.size() >= sizeRange.min()
                && items.size() <= sizeRange.max());
    }

    private int size(SourceOfRandomness random, GenerationStatus status) {
        return sizeRange != null
                ? random.nextInt(sizeRange.min(), sizeRange.max())
                : status.size();
    }

    private List<T> removals(List<?> items) {
        return stream(halving(items.size()).spliterator(), false)
                .map(i -> removeFrom(items, i))
                .flatMap(Collection::stream)
                .map(this::convert)
                .filter(this::inSizeRange)
                .collect(toList());
    }

    @SuppressWarnings("unchecked")
    private T convert(List<?> items) {
        T converted = empty();
        converted.addAll(items);
        return converted;
    }
}
