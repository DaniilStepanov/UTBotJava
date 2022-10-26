package org.utbot.engine.greyboxfuzzer.generator


import PropertiesGenerator
import org.utbot.quickcheck.NonTrackingGenerationStatus
import org.utbot.quickcheck.generator.*
import org.utbot.quickcheck.generator.java.time.*
import org.utbot.quickcheck.generator.java.util.*
import org.utbot.quickcheck.generator.java.lang.*
import org.utbot.quickcheck.generator.java.math.*
import org.utbot.quickcheck.generator.java.nio.charset.CharsetGenerator
import org.utbot.quickcheck.generator.java.util.concurrent.CallableGenerator
import org.utbot.quickcheck.generator.java.util.function.*
import org.utbot.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import org.utbot.quickcheck.random.SourceOfRandomness
import java.util.*

object DataGeneratorSettings {

    val sourceOfRandomness = SourceOfRandomness(Random(42))
    val generatorRepository =
        UTGeneratorRepository(sourceOfRandomness).also {
            it.register(DurationGenerator())
            it.register(MonthDayGenerator())
            it.register(LocalDateTimeGenerator())
            it.register(YearMonthGenerator())
            it.register(ClockGenerator())
            it.register(ZonedDateTimeGenerator())
            it.register(LocalDateGenerator())
            it.register(ZoneIdGenerator())
            it.register(YearGenerator())
            it.register(OffsetTimeGenerator())
            it.register(InstantGenerator())
            it.register(ZoneOffsetGenerator())
            it.register(LocalTimeGenerator())
            it.register(OffsetDateTimeGenerator())
            it.register(PeriodGenerator())
            it.register(BigDecimalGenerator())
            it.register(BigIntegerGenerator())
            it.register(CharsetGenerator())
            it.register(ShortGenerator())
            it.register(BooleanGenerator())
            it.register(IntegerGenerator())
            it.register(Encoded())
            it.register(ByteGenerator())
            it.register(StringGenerator())
            it.register(LongGenerator())
            it.register(DoubleGenerator())
            it.register(CharacterGenerator())
            it.register(FloatGenerator())
            it.register(OptionalIntGenerator())
            it.register(OptionalDoubleGenerator())
            it.register(LinkedListGenerator())
            it.register(LinkedHashSetGenerator())
            it.register(HashMapGenerator())
            it.register(LocaleGenerator())
            it.register(BitSetGenerator())
            it.register(TimeZoneGenerator())
            it.register(HashSetGenerator())
            it.register(ArrayListGenerator())
            it.register(VectorGenerator())
            it.register(LinkedHashMapGenerator())
            it.register(HashtableGenerator())
            it.register(OptionalLongGenerator())
            it.register(PropertiesGenerator())
            it.register(OptionalGenerator())
            //TODO fix lambdas
//            it.register(PredicateGenerator())
//            it.register(SupplierGenerator())
//            it.register(ToLongBiFunctionGenerator())
//            it.register(IntFunctionGenerator())
//            it.register(ToDoubleBiFunctionGenerator())
//            it.register(LongFunctionGenerator())
//            it.register(FunctionGenerator())
//            it.register(BiPredicateGenerator())
//            it.register(ToDoubleFunctionGenerator())
//            it.register(DoubleFunctionGenerator())
//            it.register(ToIntBiFunctionGenerator())
//            it.register(ToLongFunctionGenerator())
//            it.register(BiFunctionGenerator())
//            it.register(BinaryOperatorGenerator())
//            it.register(UnaryOperatorGenerator())
//            it.register(ToIntFunctionGenerator())
//            it.register(CallableGenerator())
            it.register(DateGenerator())
            it.register(StackGenerator())
            it.register(VoidGenerator())
        }

    //.register(ServiceLoaderGeneratorSource()).also {
//            it.removeGenerator(Closeable::class.java)
//            it.replaceGenerator(
//                Map::class.java,
//                setOf(HashtableGenerator(), HashMapGenerator(), LinkedHashMapGenerator())
//            )
//            it.replaceGenerator(
//                AbstractMap::class.java,
//                setOf(HashtableGenerator(), HashMapGenerator(), LinkedHashMapGenerator())
//            )
//            it.replaceGenerator(Dictionary::class.java, setOf(HashtableGenerator()))
//            it.replaceGenerator(Hashtable::class.java, setOf(HashtableGenerator()))
//            it.replaceGenerator(HashMap::class.java, setOf(HashMapGenerator()))
//            it.replaceGenerator(LinkedHashMap::class.java, setOf(LinkedHashMapGenerator()))
//            it.replaceGenerator(Set::class.java, setOf(HashSetGenerator(), LinkedHashSetGenerator()))
//            it.replaceGenerator(AbstractSet::class.java, setOf(HashSetGenerator(), LinkedHashSetGenerator()))
//            it.replaceGenerator(HashSet::class.java, setOf(HashSetGenerator()))
//            it.replaceGenerator(LinkedHashSet::class.java, setOf(LinkedHashSetGenerator()))
//            it.replaceGenerator(BitSet::class.java, setOf(LinkedHashSetGenerator()))
//            it.replaceGenerator(
//                Collection::class.java,
//                setOf(
//                    ArrayListGenerator(),
//                    LinkedListGenerator(),
//                    StackGenerator(),
//                    VectorGenerator(),
//                    HashSetGenerator(),
//                    LinkedHashSetGenerator()
//                )
//            )
//            it.replaceGenerator(
//                Iterable::class.java,
//                setOf(
//                    ArrayListGenerator(),
//                    LinkedListGenerator(),
//                    StackGenerator(),
//                    VectorGenerator(),
//                    HashSetGenerator(),
//                    LinkedHashSetGenerator()
//                )
//            )
//            it.replaceGenerator(
//                AbstractCollection::class.java,
//                setOf(
//                    ArrayListGenerator(),
//                    LinkedListGenerator(),
//                    StackGenerator(),
//                    VectorGenerator(),
//                    HashSetGenerator(),
//                    LinkedHashSetGenerator()
//                )
//            )
//        }
    val genStatus = NonTrackingGenerationStatus(sourceOfRandomness)
    const val maxDepthOfGeneration = 4

}