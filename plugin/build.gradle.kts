import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "io.nightfish.lightnovelreader.plugin.linovelib"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.nightfish.lightnovelreader.plugin.linovelib"
        minSdk = 24
        targetSdk = 36
        versionCode = 25
        versionName = "1.1.2"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val outputImpl = output as com.android.build.api.variant.impl.VariantOutputImpl
            outputImpl.outputFileName = outputImpl.outputFileName.get().replace(".apk", ".apk.lnrp")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.manifests.addStaticManifestFile(
            layout.buildDirectory.file("generated/ksp/${variant.name}/resources/auto_register_manifest.xml").get().toString()
        )
    }
}

afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        tasks.findByName("process${variant}MainManifest")?.dependsOn("ksp${variant}Kotlin")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

fun registerHostAbiCheck(variant: String) = tasks.register(
    "verify${variant.replaceFirstChar(Char::uppercase)}HostAbi"
) {
    val variantTitle = variant.replaceFirstChar(Char::uppercase)
    dependsOn("package$variantTitle")
    doLast {
        val forbiddenMarkers = listOf(
            "kotlinx/coroutines/sync",
            "java/util/Base64",
            "j\$/util/Base64",
            "withoutPadding"
        )
        val offenders = fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/$variant/compile${variantTitle}Kotlin/classes"
            )
        )
            .matching { include("**/*.class") }
            .files
            .filter { file ->
                val bytecode = String(file.readBytes(), Charsets.ISO_8859_1)
                forbiddenMarkers.any(bytecode::contains)
            }
        check(offenders.isEmpty()) {
            "Release classes link APIs unavailable in the host app: " +
                offenders.joinToString { it.name }
        }
        val webDataSourceClass = fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/$variant/compile${variantTitle}Kotlin/classes"
            )
        ).matching { include("**/LinovelibWebDataSource.class") }.singleFile
        val webDataSourceBytecode = String(webDataSourceClass.readBytes(), Charsets.ISO_8859_1)
        check("io/nightfish/lightnovelreader/api/Route" !in webDataSourceBytecode) {
            "LinovelibWebDataSource directly links Route, which is unavailable in LightNovelReader 1.2.0"
        }

        val apk = fileTree(layout.buildDirectory.dir("outputs/apk/$variant")) {
            include("*.apk", "*.apk.lnrp")
        }.files.maxByOrNull(File::lastModified)
            ?: error("No packaged $variant APK found for host ABI verification")
        val dexContents = mutableMapOf<String, String>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.matches(Regex("classes\\d*\\.dex"))) {
                    dexContents[entry.name] = zip.getInputStream(entry).use { input ->
                        String(input.readBytes(), Charsets.ISO_8859_1)
                    }
                }
            }
        }
        val dexChecks = mapOf(
            "LinovelibImageStore" to listOf("java/util/Base64", "j\$/util/Base64", "withoutPadding")
        )
        dexChecks.forEach { (classMarker, forbiddenForClass) ->
            val matchingDex = dexContents.filterValues { classMarker in it }
            check(matchingDex.isNotEmpty()) { "$classMarker was not found in packaged DEX files" }
            val badDex = matchingDex.filterValues { dex -> forbiddenForClass.any(dex::contains) }
            check(badDex.isEmpty()) {
                "$classMarker shares packaged DEX with unavailable host APIs: ${badDex.keys}"
            }
        }
    }
}

val verifyDebugHostAbi = registerHostAbiCheck("debug")
val verifyReleaseHostAbi = registerHostAbiCheck("release")
tasks.configureEach {
    when (name) {
        "assembleDebug" -> dependsOn(verifyDebugHostAbi)
        "assembleRelease" -> dependsOn(verifyReleaseHostAbi)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.foundation.layout)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.cxhttp)
    implementation(libs.kotlin.result)

    compileOnly(project(":api"))
    compileOnly(libs.androidx.navigation.runtime.ktx)
    ksp(libs.lightnovelreader.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.jsoup)
    testImplementation(project(":api"))
}
