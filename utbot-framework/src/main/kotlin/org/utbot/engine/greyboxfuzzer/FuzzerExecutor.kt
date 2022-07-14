package org.utbot.engine.greyboxfuzzer

import org.jgrapht.graph.SimpleDirectedGraph
import org.utbot.common.FileUtil
import org.utbot.example.CalendarLogic
import org.utbot.example.GraphAlgorithms
import org.utbot.example.PrimitiveFields
import org.utbot.example.jdk.DateFormatterTest
import org.utbot.example.jgrapht.CyclesTest
import org.utbot.external.api.TestMethodInfo
import org.utbot.external.api.UtBotJavaApi.fuzzingTestCases
import org.utbot.external.api.UtBotJavaApi.stopConcreteExecutorOnExit
import org.utbot.external.api.UtModelFactory
import org.utbot.external.api.classIdForType
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.UtContext
import org.utbot.framework.plugin.api.util.UtContext.Companion.setUtContext
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors

class FuzzerExecutor {

    private val context: AutoCloseable
    private val modelFactory: UtModelFactory

    init {
        SootUtils.runSoot(PrimitiveFields::class.java)
        context = setUtContext(UtContext(PrimitiveFields::class.java.classLoader))
        modelFactory = UtModelFactory()
    }

    fun testSimpleFuzzing(clazz: Class<*>, funName: String) {
        stopConcreteExecutorOnExit = false
        val classpath: String = getClassPath(clazz)
        val dependencyClassPath: String = getDependencyClassPath()
        val classUnderTestModel: UtCompositeModel = modelFactory.produceCompositeModel(
            classIdForType(clazz)
        )
        val methodUnderTest = PredefinedGeneratorParameters.getMethodByName(
            clazz, funName
        )
        val models: IdentityHashMap<UtModel, UtModel> = modelFactory.produceAssembleModel(
            methodUnderTest,
            clazz, listOf(classUnderTestModel)
        )
        val methodState = EnvironmentModels(
            models[classUnderTestModel],
            Arrays.asList(UtPrimitiveModel("initial model"), UtPrimitiveModel(-10), UtPrimitiveModel(0)), emptyMap()
        )
        val methodInfo = TestMethodInfo(
            methodUnderTest,
            methodState
        )
        val utTestCases1: List<UtTestCase> = fuzzingTestCases(
            listOf(
                methodInfo
            ),
            clazz,
            classpath,
            dependencyClassPath,
            MockStrategyApi.OTHER_PACKAGES,
            100000L
        ) { type: Class<*> ->
            if (Int::class.javaPrimitiveType == type || Int::class.java == type) {
                return@fuzzingTestCases Arrays.asList<Any>(
                    0,
                    Int.MIN_VALUE,
                    Int.MAX_VALUE
                )
            }
            null
        }
//        generate(
//            listOf(methodInfo),
//            utTestCases1,
//            PredefinedGeneratorParameters.destinationClassName,
//            classpath,
//            dependencyClassPath,
//            clazz
//        )
//        val snippet2 = Snippet(CodegenLanguage.JAVA, generate)
//        compileClassFile(PredefinedGeneratorParameters.destinationClassName, snippet2)
    }

    private fun getClassPath(clazz: Class<*>): String {
        return clazz.protectionDomain.codeSource.location.path
    }

    private fun getDependencyClassPath(): String {
        return Arrays.stream((Thread.currentThread().contextClassLoader as URLClassLoader).urLs).map { url: URL ->
            return@map File(url.toURI()).toString()
        }.collect(Collectors.joining(File.pathSeparator))
    }
}

internal object PredefinedGeneratorParameters {
    var destinationClassName = "GeneratedTest"
    fun getMethodByName(clazz: Class<*>, name: String): Method {
        return clazz.declaredMethods.first { it.name == name }
    }
}


object SootUtils {
    @JvmStatic
    fun runSoot(clazz: Class<*>) {
        val buildDir = FileUtil.locateClassPath(clazz.kotlin) ?: FileUtil.isolateClassFiles(clazz.kotlin)
        val buildDirPath = buildDir.toPath()

        if (buildDirPath != previousBuildDir) {
            runSoot(buildDirPath, null)
            previousBuildDir = buildDirPath
        }
    }

    private var previousBuildDir: Path? = null
}

fun fields(
    classId: ClassId,
    vararg fields: Pair<String, Any>
): MutableMap<FieldId, UtModel> {
    return fields
        .associate {
            val fieldId = FieldId(classId, it.first)
            val fieldValue = when (val value = it.second) {
                is UtModel -> value
                else -> UtPrimitiveModel(value)
            }
            fieldId to fieldValue
        }
        .toMutableMap()
}

fun main() {
    FuzzerExecutor().testSimpleFuzzing(GraphAlgorithms::class.java, "bfs")
}