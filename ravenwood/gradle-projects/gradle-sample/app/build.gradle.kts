/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.ScopedArtifacts
import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Paths

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Boolean indicating whether to run host tests on Ravenwood or not.
 * To enable Ravenwood:
 * - Add `enableRavenwood=true` to gradle.properties. (e.g. $HOME/.gradle/gradle.properties)
 * - Or, ./gradlew :app:testDebugUnitTest --info -PenableRavenwood=false
 */
val enableRavenwood = project.findProperty("enableRavenwood")?.toString()?.toBoolean() ?: false
logger.lifecycle("Ravenwood: enableRavenwood=$enableRavenwood")


// Do not use the following `config*` Paths directly, use the functions below so that we can easily
// change how we set them later.

val configAndroidBuildTop = Paths.get("../../../../../..")!!
val configAndroidHostOut = configAndroidBuildTop.resolve("out/host/linux-x86")!!
val configRavenwoodRuntimePath = androidHostOut().resolve("testcases/ravenwood-runtime")!!
val configRavenwoodUtilsPath = androidHostOut().resolve("testcases/ravenwood-utils")!!
val configRavenizerJar = androidHostOut().resolve("framework/ravenizer.jar")!!
val configRavenwoodPropFile = Paths.get("../ravenwood.properties")!!

fun androidBuildTop() = configAndroidBuildTop
fun androidHostOut() = configAndroidHostOut

fun ravenwoodRuntimePath() = configRavenwoodRuntimePath
fun ravenwoodUtilsPath() = configRavenwoodUtilsPath
fun ravenizerJar() = configRavenizerJar

fun ravenwoodPropFile() = configRavenwoodPropFile

/** Jar files in ravenwood-runtime that need to be added before the test jars. */
// TODO: Get this list from ravenwood-runtime/ravenwood-data/ravenwood-classpath.txt
fun ravenwoodRuntimeJarsPre() = listOf(
    "ravenwood-junit-impl.jar",
    "hoststubgen-helper-runtime.jar",
    "ravenwood-framework.jar",
    "mockito-ravenwood-prebuilt.jar",
    "framework-minus-apex.ravenwood.jar",
    "kxml2-android.jar",
    "json-prebuilt.jar",
    "ext-ravenwood.jar",
    "services.core.ravenwood-jarjar.jar",
    "framework-updatable.ravenwood.jar",
    "icu4j-icudata-jarjar.jar",
    "icu4j-icutzdata-jarjar.jar",
)

/** Jar files in ravenwood-runtime that need to be added after the test jars. */
// TODO: Get this list from ravenwood-runtime/ravenwood-data/ravenwood-classpath.txt
fun ravenwoodRuntimeJarsPost() = listOf(
    "framework-updatable-stubs-module_libs_api.jar",
    "all-modules-system-stubs.jar",
)

fun ravenwoodUtilsJars() = listOf(
    "ravenwood-junit.jar",
    "ravenwood-framework.jar",
    "mockito-ravenwood-prebuilt.jar",
)

fun ravenwoodJvmArgs() = listOf(
    "--add-modules=jdk.compiler",
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
)

fun ravenwoodJavaProps() = mapOf(
    "android.ravenwood.version" to "1",
    "android.ravenwood.runtime_path" to "${ravenwoodRuntimePath()}/",
    "android.ravenwood.prop_file" to ravenwoodPropFile().toString(),
    "java.library.path" to "${ravenwoodRuntimePath()}/lib64/",
    // "android.ravenwood.artifacts_path" to "...", // Optional parameter, no need to set.
)

fun ravenwoodEnvVars() = mapOf(
    "LANG" to "C",
    "LC_ALL" to "C",
    "RAVENWOOD_RUNTIME_PATH" to "${ravenwoodRuntimePath()}",

    // TODO: Need to add the test's self JNI path, if a test uses any.
    "LD_LIBRARY_PATH" to "${ravenwoodRuntimePath()}/lib64/"
)

fun ravenizerOptionalArgs() = listOf(
    "--strip-mockito"
)

val configureRavenwoodTestIfNeeded: (Test) -> Unit = configureRavenwoodTestIfNeeded@ { test ->
    if (!enableRavenwood) return@configureRavenwoodTestIfNeeded
    logger.lifecycle("Ravenwood: configureRavenwoodTestIfNeeded(Test={${test.name}})")

    test.jvmArgs(ravenwoodJvmArgs())
    ravenwoodEnvVars().forEach { test.environment(it.key, it.value) }
    ravenwoodJavaProps().forEach { test.systemProperty(it.key, it.value) }

    if (logger.isInfoEnabled) {
        logger.info(
            "Ravenwood: configureRavenwoodTestIfNeeded: original classpath=\n{}\n",
            test.classpath.files.map { "    $it" }.joinToString("\n")
        )
    }
}

/**
 * Task to run Ravenizer.
 */
abstract class RavenizeClassesTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    /** This contains all the dependency jars */
    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    /** The directories that contain test classes */
    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    /** Ravenized output jar */
    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** Path to ravenizer.jar */
    // Tasks can't refer to top-level vals because that'd make it non-static. So we need to
    // pass them as properties.
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    /** Optional arguments to Ravenizer */
    @get:Input
    abstract val ravenizerArgs: ListProperty<String>

    @TaskAction
    fun execute() {
        val outJarFile = outputJar.get().asFile
        outJarFile.parentFile.mkdirs()

        if (logger.isInfoEnabled) {
            logger.info(
                "Ravenwood: Ravenizer:\n  out={}\n  in-jars={}\n  in-dirs={}\n",
                outputJar.get().asFile,
                inputJars.get().map { "    $it" }.joinToString("\n"),
                inputDirectories.get().map { "    $it" }.joinToString("\n"),
            )
        }

        // To avoid the arguments from getting too long, pass input file paths from an @ file.
        val atFile = File(temporaryDir, "ravenizer-input.txt")
        BufferedWriter(FileWriter(atFile)).use { file ->
            inputJars.get().forEach {
                file.write("--in-jar\n")
                file.write(it.asFile.absolutePath)
                file.write("\n")
            }
            inputDirectories.get().forEach {
                file.write("--in-dir\n")
                file.write(it.asFile.absolutePath)
                file.write("\n")
            }
        }

        // Use injected execOperations instead of the project object
        execOperations.javaexec {
            classpath = toolClasspath
            mainClass.set("com.android.platform.test.ravenwood.ravenizer.RavenizerMain")

            val argsList = mutableListOf<String>()
            argsList.add("--out-jar")
            argsList.add(outJarFile.absolutePath)

            argsList.add("@$atFile")

            argsList.addAll(ravenizerArgs.get())
            args(argsList)
        }
    }
}

android {
    namespace = "com.android.ravenwoodtest.gradlesample"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.android.ravenwoodtest.gradlesample"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        // Let host tests use resources.
        unitTests {
            isIncludeAndroidResources = true
        }

        unitTests.all { test ->
            configureRavenwoodTestIfNeeded(test)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.monitor)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Build the test classes with ravenwood-utils.jar.
    // We need to do it even if enableRavenwood is false because the test source code uses them.
    testCompileOnly(files(ravenwoodUtilsJars().map {"${ravenwoodUtilsPath()}/$it"}))
}

/** Register ravenizer task if enableRavenwood is true. */
androidComponents {
    if (!enableRavenwood) return@androidComponents

    val targetVariant = selector().withName("debug")

    onVariants(targetVariant) { variant ->
        val unitTestComponent = variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]
            ?: return@onVariants

        val taskName = "ravenizer${variant.name.replaceFirstChar { it.uppercase() }}Classes"

        val ravenizerTaskProvider = project.tasks.register<RavenizeClassesTask>(taskName) {
            toolClasspath.from(project.file(ravenizerJar()))
            ravenizerArgs.set(ravenizerOptionalArgs())
        }

        unitTestComponent.artifacts
            .forScope(ScopedArtifacts.Scope.ALL)
            .use(ravenizerTaskProvider)
            .toTransform(
                com.android.build.api.artifact.ScopedArtifact.CLASSES,
                RavenizeClassesTask::inputJars,
                RavenizeClassesTask::inputDirectories,
                RavenizeClassesTask::outputJar
            )
    }
}

/**
 * Run Ravenizer if Ravenwood is enabled, and also update the test's classpath. We also add
 * ravenwood-runtime jar files to the classpath too.
 *
 * - This method first runs Ravenizer on the test classes and all the dependencies. Ravenizer
 *   modifies the bytecode as needed, and merge them into a single jar file. We remove the
 *   original jars and classes from the class path and replace with this jar file.
 *
 * - We also add ravenwood-runtime jars to the classpath. [ravenwoodRuntimeJarsPre] needs
 *   to be put before the ravenized jar, and [ravenwoodRuntimeJarsPre] after.
 *
 */
tasks.withType<Test>().configureEach {
    if (!enableRavenwood) return@configureEach

    // Run Ravenizer if Ravenwood is enabled.
    if (name == "testDebugUnitTest") {

        // Set up dependency on Ravenizer
        val ravenizerTask = project.tasks.named<RavenizeClassesTask>("ravenizerDebugClasses")
        dependsOn(ravenizerTask)

        // Output jar file
        val ravenizedJar = project.files(ravenizerTask.flatMap { it.outputJar })

        // Tell JUnit to look for the @Test methods inside the ravenized jar,
        // not the default uninstrumented directories!
        testClassesDirs = ravenizedJar

        fun logClassPath(label: String) {
            if (logger.isInfoEnabled) {
                logger.info(
                    "Ravenwood: Updating classpath: {}=\n{}\n",
                    label,
                    classpath.files.map { "    $it" }.joinToString("\n")
                )
            }
        }

        // Update the class path.
        doFirst {
            logClassPath("original")

            classpath =
                // Ravenwood runtime jars, first half.
                files(ravenwoodRuntimeJarsPre().map { "${ravenwoodRuntimePath()}/$it" }) +

                // Ravenized test classes and dependencies.
                ravenizedJar +

                // Ravenwood runtime jars, second half.
                files(ravenwoodRuntimeJarsPost().map { "${ravenwoodRuntimePath()}/$it" })

            logClassPath("updated")
        }
    }
}
