package org.utbot.examples.stream;

import org.utbot.api.mock.UtMock;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"IfStatementWithIdenticalBranches", "RedundantOperationOnEmptyContainer"})
public class BaseStreamExample {
    Stream<Integer> returningStreamExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream();
        } else {
            return list.stream();
        }
    }

    @SuppressWarnings("Convert2MethodRef")
    boolean filterExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        int prevSize = list.size();

        int newSize = list.stream().filter(value -> value != null).toArray().length;

        return prevSize != newSize;
    }

    Integer[] mapExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        return list.stream().map(value -> value * 2).toArray(Integer[]::new);
    }

    // TODO mapToInt, etc

    // TODO flatMap

    boolean distinctExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        int prevSize = list.size();

        int newSize = list.stream().distinct().toArray().length;

        return prevSize != newSize;
    }

    Integer[] sortedExample(List<Integer> list) {
        UtMock.assume(list != null && list.size() >= 2);

        Integer first = list.get(0);

        int lastIndex = list.size() - 1;
        Integer last = list.get(lastIndex);

        UtMock.assume(last < first);

        return list.stream().sorted().toArray(Integer[]::new);
    }

    // TODO sorted with custom Comparator

    static int x = 0;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    int peekExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        int beforeStaticValue = x;

        list.stream().peek(value -> x += value);

        return beforeStaticValue;
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    Integer[] limitExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        if (list.size() <= 5) {
            return list.stream().limit(5).toArray(Integer[]::new);
        } else {
            return list.stream().limit(5).toArray(Integer[]::new);
        }
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    Integer[] skipExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        if (list.size() > 5) {
            return list.stream().skip(5).toArray(Integer[]::new);
        } else {
            return list.stream().skip(5).toArray(Integer[]::new);
        }
    }

    @SuppressWarnings("SimplifyStreamApiCallChains")
    int forEachExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        int beforeStaticValue = x;

        list.stream().forEach(value -> x += value);

        return beforeStaticValue;
    }

    @SuppressWarnings("SimplifyStreamApiCallChains")
    Object[] toArrayExample(List<Integer> list) {
        UtMock.assume(list != null);

        int size = list.size();
        if (size <= 1) {
            return list.stream().toArray();
        } else {
            return list.stream().toArray(Integer[]::new);
        }
    }

    Integer reduceExample(List<Integer> list) {
        UtMock.assume(list != null);

        return list.stream().reduce(42, this::nullableSum);
    }

    Optional<Integer> optionalReduceExample(List<Integer> list) {
        UtMock.assume(list != null);

        int size = list.size();

        if (size == 0) {
            return list.stream().min(this::nullableCompareTo);
        }

        if (size == 1 && list.get(0) == null) {
            return list.stream().reduce(this::nullableSum);
        }

        return list.stream().reduce(this::nullableSum);
    }

    Double complexReduceExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().reduce(42.0, (Double a, Integer b) -> a + b.doubleValue(), Double::sum);
        }

        return list.stream().reduce(
                42.0,
                (Double a, Integer b) -> a + (b != null ? b.doubleValue() : 0.0),
                Double::sum
        );
    }

    Integer collectExample(List<Integer> list) {
        UtMock.assume(list != null);

        return list.stream().collect(IntWrapper::new, IntWrapper::plus, IntWrapper::plus).value;
    }

    @SuppressWarnings("SimplifyStreamApiCallChains")
    Set<Integer> collectorExample(List<Integer> list) {
        UtMock.assume(list != null);

        return list.stream().collect(Collectors.toSet());
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    Optional<Integer> minExample(List<Integer> list) {
        UtMock.assume(list != null);

        int size = list.size();

        if (size == 0) {
            return list.stream().min(this::nullableCompareTo);
        }

        if (size == 1 && list.get(0) == null) {
            return list.stream().min(this::nullableCompareTo);
        }

        return list.stream().min(this::nullableCompareTo);
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    Optional<Integer> maxExample(List<Integer> list) {
        UtMock.assume(list != null);

        int size = list.size();

        if (size == 0) {
            return list.stream().max(this::nullableCompareTo);
        }

        if (size == 1 && list.get(0) == null) {
            return list.stream().max(this::nullableCompareTo);
        }

        return list.stream().max(this::nullableCompareTo);
    }

    @SuppressWarnings({"ReplaceInefficientStreamCount", "ConstantConditions"})
    long countExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().count();
        } else {
            return list.stream().count();
        }
    }

    @SuppressWarnings({"Convert2MethodRef", "ConstantConditions", "RedundantOperationOnEmptyContainer"})
    boolean anyMatchExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().anyMatch(value -> value == null);
        }

        UtMock.assume(list.size() == 2);

        Integer first = list.get(0);
        Integer second = list.get(1);

        if (first == null && second == null) {
            return list.stream().anyMatch(value -> value == null);
        }

        if (first == null) {
            return list.stream().anyMatch(value -> value == null);
        }

        if (second == null) {
            return list.stream().anyMatch(value -> value == null);
        }

        return list.stream().anyMatch(value -> value == null);
    }

    @SuppressWarnings({"Convert2MethodRef", "ConstantConditions", "RedundantOperationOnEmptyContainer"})
    boolean allMatchExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().allMatch(value -> value == null);
        }

        UtMock.assume(list.size() == 2);

        Integer first = list.get(0);
        Integer second = list.get(1);

        if (first == null && second == null) {
            return list.stream().allMatch(value -> value == null);
        }

        if (first == null) {
            return list.stream().allMatch(value -> value == null);
        }

        if (second == null) {
            return list.stream().allMatch(value -> value == null);
        }

        return list.stream().allMatch(value -> value == null);
    }

    @SuppressWarnings({"Convert2MethodRef", "ConstantConditions", "RedundantOperationOnEmptyContainer"})
    boolean noneMatchExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().noneMatch(value -> value == null);
        }

        UtMock.assume(list.size() == 2);

        Integer first = list.get(0);
        Integer second = list.get(1);

        if (first == null && second == null) {
            return list.stream().noneMatch(value -> value == null);
        }

        if (first == null) {
            return list.stream().noneMatch(value -> value == null);
        }

        if (second == null) {
            return list.stream().noneMatch(value -> value == null);
        }

        return list.stream().noneMatch(value -> value == null);
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    Optional<Integer> findFirstExample(List<Integer> list) {
        UtMock.assume(list != null);

        if (list.isEmpty()) {
            return list.stream().findFirst();
        }

        if (list.get(0) == null) {
            return list.stream().findFirst();
        } else {
            return list.stream().findFirst();
        }
    }

    Integer iteratorSumExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        int sum = 0;
        Iterator<Integer> streamIterator = list.stream().iterator();

        if (list.isEmpty()) {
            while (streamIterator.hasNext()) {
                Integer value = streamIterator.next();
                sum += value;
            }
        } else {
            while (streamIterator.hasNext()) {
                Integer value = streamIterator.next();
                sum += value;
            }
        }

        return sum;
    }

    Stream<Integer> streamOfExample(Integer[] values) {
        UtMock.assume(values != null);

        if (values.length == 0) {
            return Stream.empty();
        } else {
            return Stream.of(values);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    long closedStreamExample(List<Integer> values) {
        UtMock.assume(values != null);

        Stream<Integer> stream = values.stream();
        stream.count();

        return stream.count();
    }

    Integer[] generateExample() {
        return Stream.generate(() -> 42).limit(10).toArray(Integer[]::new);
    }

    Integer[] iterateExample() {
        return Stream.iterate(42, x -> x + 1).limit(10).toArray(Integer[]::new);
    }

    Integer[] concatExample() {
        Stream<Integer> first = Stream.generate(() -> 42).limit(10);
        Stream<Integer> second = Stream.iterate(42, x -> x + 1).limit(10);

        return Stream.concat(first, second).toArray(Integer[]::new);
    }

    // TODO tests for generate, concat?

    long complexExample(List<Integer> list) {
        UtMock.assume(list != null && !list.isEmpty());

        return list
                .stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0 && value < 125)
                .map(value -> value * 2)
                .distinct()
                .count();
    }

    // avoid NPE
    private int nullableSum(Integer a, Integer b) {
        if (b == null) {
            return a;
        }

        return a + b;
    }

    // avoid NPE
    private int nullableCompareTo(Integer a, Integer b) {
        if (b == null) {
            return 1;
        }

        return a.compareTo(b);
    }

    private static class IntWrapper {
        int value = 0;

        void plus(int other) {
            value += other;
        }

        void plus(IntWrapper other) {
            value += other.value;
        }
    }
}
