package org.utbot.framework.plugin.api

import org.utbot.api.mock.UtMock
import org.utbot.common.FileUtil
import org.utbot.engine.UtNativeStringWrapper
import org.utbot.engine.jimpleBody
import org.utbot.engine.overrides.Boolean
import org.utbot.engine.overrides.Byte
import org.utbot.engine.overrides.Character
import org.utbot.engine.overrides.Class
import org.utbot.engine.overrides.Integer
import org.utbot.engine.overrides.Long
import org.utbot.engine.overrides.PrintStream
import org.utbot.engine.overrides.Short
import org.utbot.engine.overrides.System
import org.utbot.engine.overrides.UtArrayMock
import org.utbot.engine.overrides.UtLogicMock
import org.utbot.engine.overrides.strings.UtString
import org.utbot.engine.overrides.strings.UtStringBuffer
import org.utbot.engine.overrides.strings.UtStringBuilder
import org.utbot.engine.overrides.collections.AssociativeArray
import org.utbot.engine.overrides.collections.RangeModifiableUnlimitedArray
import org.utbot.engine.overrides.collections.UtArrayList
import org.utbot.engine.overrides.collections.UtGenericAssociative
import org.utbot.engine.overrides.collections.UtHashMap
import org.utbot.engine.overrides.collections.UtHashSet
import org.utbot.engine.overrides.collections.UtLinkedList
import org.utbot.engine.overrides.UtOverrideMock
import org.utbot.engine.overrides.collections.Collection
import org.utbot.engine.overrides.collections.List
import org.utbot.engine.overrides.collections.UtGenericStorage
import org.utbot.engine.overrides.collections.UtOptional
import org.utbot.engine.overrides.collections.UtOptionalDouble
import org.utbot.engine.overrides.collections.UtOptionalInt
import org.utbot.engine.overrides.collections.UtOptionalLong
import org.utbot.engine.overrides.stream.Arrays
import org.utbot.engine.overrides.stream.Stream
import org.utbot.engine.overrides.stream.UtStream
import org.utbot.engine.pureJavaSignature
import org.utbot.framework.plugin.api.UtMethod
import org.utbot.framework.plugin.api.util.signature
import soot.G
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.jimple.JimpleBody
import soot.options.Options
import soot.toolkits.graph.ExceptionalUnitGraph
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import kotlin.io.path.absolutePathString
import kotlin.reflect.KClass
import kotlin.streams.toList

/**
Convert code to Jimple
 */
object SootUtils {
    fun runSoot(buildDir: Path, classpath: String?) {
        G.reset()
        val options = Options.v()

        options.apply {
            set_prepend_classpath(true)
            // set true to debug. Disabled because of a bug when two different variables
            // from the source code have the same name in the jimple body.
            setPhaseOption("jb", "use-original-names:false")
            set_soot_classpath(
                FileUtil.isolateClassFiles(*classesToLoad).absolutePath
                        + if (!classpath.isNullOrEmpty()) File.pathSeparator + "$classpath" else ""
            )
            set_src_prec(Options.src_prec_only_class)
            set_process_dir(listOf("$buildDir"))
            set_keep_line_number(true)
            set_ignore_classpath_errors(true) // gradle/build/resources/main does not exists, but it's not a problem
            set_output_format(Options.output_format_jimple)
            /**
             * In case of Java8, set_full_resolver(true) fails with "soot.SootResolver$SootClassNotFoundException:
             * couldn't find class: javax.crypto.BadPaddingException (is your soot-class-path set properly?)".
             * To cover that, set_allow_phantom_refs(true) is required
             */
            set_allow_phantom_refs(true) // Java8 related
            set_full_resolver(true)
        }

        addBasicClasses(*classesToLoad)
        addLibraryClasses(libraryClassesToLoad)
        loadJavaStdLibClasses()

        Scene.v().loadNecessaryClasses()
        PackManager.v().runPacks()
        // we need this to create hierarchy of classes
        Scene.v().classes.forEach {
            if (it.resolvingLevel() < SootClass.HIERARCHY)
                it.setResolvingLevel(SootClass.HIERARCHY)
        }
    }

    private fun addBasicClasses(vararg classes: KClass<*>) {
        classes.forEach {
            Scene.v().addBasicClass(it.qualifiedName, SootClass.BODIES)
        }
    }

    private fun addLibraryClasses(classesNames: kotlin.collections.List<String>) {
        classesNames.forEach {
            Scene.v().addBasicClass(it, SootClass.BODIES)
        }
    }

    private fun loadJavaStdLibClasses() {
        val fs = FileSystems.getFileSystem(URI.create("jrt:/"))
        val javaUtilsClasses = Files.walk(fs.getPath("modules", "java.base", "java/util")).toList()
        val javaLangClasses = Files.walk(fs.getPath("modules", "java.base", "java/lang")).toList()
        val classesToLoad =
            (javaUtilsClasses + javaLangClasses)
                .filter { it.absolutePathString().endsWith(".class") }
                .filterNot { it.absolutePathString().contains("$") }
        classesToLoad
            .map { it.absolutePathString().removePrefix("/modules/java.base/").replace('/', '.') }
            .forEach {
                Scene.v().addBasicClass(it, SootClass.BODIES)
            }
    }

    val libraryClassesToLoad = mutableListOf<String>()

    private val classesToLoad = arrayOf(
        UtMock::class,
        UtOverrideMock::class,
        UtLogicMock::class,
        UtArrayMock::class,
        Boolean::class,
        Byte::class,
        Character::class,
        Class::class,
        Integer::class,
        Long::class,
        Short::class,
        System::class,
        UtOptional::class,
        UtOptionalInt::class,
        UtOptionalLong::class,
        UtOptionalDouble::class,
        UtArrayList::class,
        UtArrayList.UtArrayListIterator::class,
        UtLinkedList::class,
        UtLinkedList.UtLinkedListIterator::class,
        UtLinkedList.ReverseIteratorWrapper::class,
        UtHashSet::class,
        UtHashSet.UtHashSetIterator::class,
        UtHashMap::class,
        UtHashMap.Entry::class,
        UtHashMap.LinkedEntryIterator::class,
        UtHashMap.LinkedEntrySet::class,
        UtHashMap.LinkedHashIterator::class,
        UtHashMap.LinkedKeyIterator::class,
        UtHashMap.LinkedKeySet::class,
        UtHashMap.LinkedValueIterator::class,
        UtHashMap.LinkedValues::class,
        RangeModifiableUnlimitedArray::class,
        AssociativeArray::class,
        UtGenericStorage::class,
        UtGenericAssociative::class,
        PrintStream::class,
        UtNativeStringWrapper::class,
        UtString::class,
        UtStringBuilder::class,
        UtStringBuffer::class,
        Stream::class,
        Arrays::class,
        Collection::class,
        List::class,
        UtStream::class,
        UtStream.UtStreamIterator::class
    )
}

fun JimpleBody.graph() = ExceptionalUnitGraph(this)

fun jimpleBody(method: UtMethod<*>): JimpleBody {
    val clazz = Scene.v().classes.single { it.name == method.clazz.java.name }
    val signature = method.callable.signature
    val sootMethod = clazz.methods.single { it.pureJavaSignature == signature }

    return sootMethod.jimpleBody()
}
