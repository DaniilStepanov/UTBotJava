package org.utbot.framework.plugin.api

import org.utbot.api.mock.UtMock
import org.utbot.common.FileUtil
import org.utbot.engine.UtNativeStringWrapper
import org.utbot.engine.overrides.*
import org.utbot.engine.overrides.Boolean
import org.utbot.engine.overrides.Byte
import org.utbot.engine.overrides.Long
import org.utbot.engine.overrides.Short
import org.utbot.engine.overrides.collections.*
import org.utbot.engine.overrides.strings.UtString
import org.utbot.engine.overrides.strings.UtStringBuffer
import org.utbot.engine.overrides.strings.UtStringBuilder
import soot.G
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.options.Options
import java.io.File
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import kotlin.reflect.KClass

/**
Convert code to Jimple
 */
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
        //val classPathAsList = classpath?.split(":") ?: emptyList()
        //set_process_dir((classPathAsList + listOf("$buildDir")).toSet().toList())
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

private fun loadJavaStdLibClasses() {
    val libraryClasses = mutableListOf<String>()
    val jars = File("/usr/lib/jvm/java-8-openjdk/jre/lib/").listFiles()
        .toList()
        .filter { it.path.endsWith(".jar") }
    for (jar in jars) {
        val inputStream = JarInputStream(jar.inputStream())
        var entry = inputStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".class")) {
                // This ZipEntry represents a class. Now, what class does it represent?
                val className: String = entry.name.replace('/', '.') // including ".class"
                libraryClasses.add(className)
            }
            entry = inputStream.nextEntry
        }
    }
    libraryClasses.forEach {
        Scene.v().addBasicClass(it, SootClass.BODIES)
    }
}

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
    UtStringBuffer::class
)