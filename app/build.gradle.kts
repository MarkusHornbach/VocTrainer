import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    jacoco
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Release signing data lives in the gitignored local.properties (release.storeFile,
// release.storePassword, release.keyAlias, release.keyPassword). Without it the release
// build stays unsigned, so the project also builds on machines without the keystore.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProperty(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
// Version from Git tags (see docs/ARCHITECTURE.md, "Versions", and buildSrc/VersionName.kt):
// on a tag "1.0.0" or "1.0.0-rc1", after it "1.0.0+3 (abc1234)", with local changes "... dirty";
// version code 1000099 for 1.0.0, 1000001 for 1.0.0-rc1 (the last tag's code also after it), 1 without a tag.
data class AppVersion(val name: String, val code: Int)

fun git(vararg args: String): String? = try {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
} catch (e: Exception) {
    null
}

fun appVersion(): AppVersion {
    val count = git("rev-list", "--count", "HEAD")?.toIntOrNull()
    if (count == null) {
        logger.warn("VocTrainer: version could not be read from Git, using \"unbekannt\"")
        return AppVersion("unbekannt", 1)
    }
    val describe = git("describe", "--tags", "--match", "v[0-9]*", "--long", "--dirty")
    val name = describe?.let(VersionName::fromDescribe)
        // No release tag yet.
        ?: VersionName.withoutTag(count, git("describe", "--always", "--dirty").orEmpty())
    // The code follows from the last tag's version, so every clone with that tag gets the same code.
    return AppVersion(name, describe?.let(VersionName::codeFromDescribe) ?: 1)
}

val appVersion = appVersion()

// Coverage is collected only with -Pcoverage (see docs/ARCHITECTURE.md, "Build and test"). Without it
// the unit tests run without an agent and the debug APK is not instrumented.
val coverageEnabled = providers.gradleProperty("coverage").isPresent

// Read when the build runs, not while configuring, so the configuration cache never freezes it.
abstract class BuildTimeSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}
val buildTime: Provider<Long> = providers.of(BuildTimeSource::class.java) {}

val releaseSigning = listOf("release.storeFile", "release.storePassword", "release.keyAlias", "release.keyPassword")
    .associateWith { localProperty(it) }
    .takeIf { values -> values.values.all { it != null } }

android {
    namespace = "io.github.markushornbach.voctrainer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.markushornbach.voctrainer"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersion.code
        versionName = appVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { values ->
            create("release") {
                storeFile = file(values.getValue("release.storeFile")!!)
                storePassword = values.getValue("release.storePassword")
                keyAlias = values.getValue("release.keyAlias")
                keyPassword = values.getValue("release.keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds are a separate test app ("VocTrainer Test", see src/debug) with their own
        // data, so they can be installed next to the release app without touching its progress.
        debug {
            applicationIdSuffix = ".debug"
            // JaCoCo coverage of connectedDebugAndroidTest, only with -Pcoverage.
            enableAndroidTestCoverage = coverageEnabled
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }
}

// Coverage (report only, no minimum): Kover for the unit tests, JaCoCo for the instrumented
// tests, and jacocoCombinedReport for both together. Kover uses the JaCoCo agent, so the
// combined report can read its results.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}
// Only Kover attaches its agent to the unit tests; the jacoco plugin is needed for the combined report.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> { isEnabled = false }
}
kover {
    useJacoco(libs.versions.jacoco.get())
    // Kover instruments every Test task by default; only do it with -Pcoverage.
    currentProject {
        instrumentation {
            disabledForAll = !coverageEnabled
        }
    }
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*ComposableSingletons*", "*_Impl*", "*.R", "*.R$*")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

val coverageExcludes = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*ComposableSingletons*.*", "**/*_Impl*.*",
)
tasks.register<JacocoReport>("jacocoCombinedReport") {
    group = "verification"
    description = "Coverage of unit and instrumented tests together. Run testDebugUnitTest and " +
        "connectedDebugAndroidTest with -Pcoverage before, the report reads their results."
    val classes = layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    classDirectories.setFrom(fileTree(classes) { exclude(coverageExcludes) })
    sourceDirectories.setFrom(files("src/main/java", "src/debug/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("kover/bin-reports/*.exec", "outputs/code_coverage/debugAndroidTest/connected/**/*.ec")
        }
    )
    reports {
        html.required = true
        xml.required = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "BUILD_TIME",
            buildTime.map { BuildConfigField("long", "${it}L", "Build date and time (epoch millis)") }
        )
    }
}
