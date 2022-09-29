package org.utbot.engine.greyboxfuzzer.generator

import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.generator.java.util.LinkedListGenerator
import com.pholser.junit.quickcheck.generator.java.util.StackGenerator
import com.pholser.junit.quickcheck.generator.java.util.VectorGenerator
import com.pholser.junit.quickcheck.internal.generator.GeneratorRepository
import com.pholser.junit.quickcheck.internal.generator.ServiceLoaderGeneratorSource
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.NonTrackingGenerationStatus
import org.utbot.engine.greyboxfuzzer.generator.map.HashMapGenerator
import org.utbot.engine.greyboxfuzzer.generator.map.HashtableGenerator
import org.utbot.engine.greyboxfuzzer.generator.map.LinkedHashMapGenerator
import org.utbot.engine.greyboxfuzzer.generator.set.HashSetGenerator
import org.utbot.engine.greyboxfuzzer.generator.set.LinkedHashSetGenerator
import java.io.Closeable
import java.util.*

object DataGeneratorSettings {

    val sourceOfRandomness = SourceOfRandomness(Random(42))
    val generatorRepository =
        UTGeneratorRepository(sourceOfRandomness).register(ServiceLoaderGeneratorSource()).also {
            it.removeGenerator(Closeable::class.java)
            it.replaceGenerator(
                Map::class.java,
                setOf(HashtableGenerator(), HashMapGenerator(), LinkedHashMapGenerator())
            )
            it.replaceGenerator(
                AbstractMap::class.java,
                setOf(HashtableGenerator(), HashMapGenerator(), LinkedHashMapGenerator())
            )
            it.replaceGenerator(Dictionary::class.java, setOf(HashtableGenerator()))
            it.replaceGenerator(Hashtable::class.java, setOf(HashtableGenerator()))
            it.replaceGenerator(HashMap::class.java, setOf(HashMapGenerator()))
            it.replaceGenerator(LinkedHashMap::class.java, setOf(LinkedHashMapGenerator()))
            it.replaceGenerator(Set::class.java, setOf(HashSetGenerator(), LinkedHashSetGenerator()))
            it.replaceGenerator(AbstractSet::class.java, setOf(HashSetGenerator(), LinkedHashSetGenerator()))
            it.replaceGenerator(HashSet::class.java, setOf(HashSetGenerator()))
            it.replaceGenerator(LinkedHashSet::class.java, setOf(LinkedHashSetGenerator()))
            it.replaceGenerator(BitSet::class.java, setOf(LinkedHashSetGenerator()))
            it.replaceGenerator(
                Collection::class.java,
                setOf(
                    ArrayListGenerator(),
                    LinkedListGenerator(),
                    StackGenerator(),
                    VectorGenerator(),
                    HashSetGenerator(),
                    LinkedHashSetGenerator()
                )
            )
            it.replaceGenerator(
                Iterable::class.java,
                setOf(
                    ArrayListGenerator(),
                    LinkedListGenerator(),
                    StackGenerator(),
                    VectorGenerator(),
                    HashSetGenerator(),
                    LinkedHashSetGenerator()
                )
            )
            it.replaceGenerator(
                AbstractCollection::class.java,
                setOf(
                    ArrayListGenerator(),
                    LinkedListGenerator(),
                    StackGenerator(),
                    VectorGenerator(),
                    HashSetGenerator(),
                    LinkedHashSetGenerator()
                )
            )
        }
    val genStatus = NonTrackingGenerationStatus(sourceOfRandomness)
    const val maxDepthOfGeneration = 4

}