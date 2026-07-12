import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
        versionCode = 21
        versionName = "1.0.20"
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

val verifyNoCoroutineSyncLinkage = tasks.register("verifyNoCoroutineSyncLinkage") {
    dependsOn("compileReleaseKotlin")
    doLast {
        val marker = "kotlinx/coroutines/sync"
        val offenders = fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes"
            )
        )
            .matching { include("**/*.class") }
            .files
            .filter { String(it.readBytes(), Charsets.ISO_8859_1).contains(marker) }
        check(offenders.isEmpty()) {
            "Release classes link coroutine synchronization APIs unavailable in the host app: " +
                offenders.joinToString { it.name }
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease") dependsOn(verifyNoCoroutineSyncLinkage)
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
    ksp(libs.lightnovelreader.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.jsoup)
    testImplementation(project(":api"))
}
