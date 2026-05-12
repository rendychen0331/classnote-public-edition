import java.util.zip.ZipFile
import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.rendy.classnote.feature.google"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }
    }
}

dependencies {
    implementation(project(":feature-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.api.services.gmail)
    implementation(libs.google.api.services.classroom)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.api.services.tasks)
    implementation(libs.google.http.client.gson)
}

tasks.register("bundleFeatureDex") {
    dependsOn("assembleRelease")

    val outputDex = layout.buildDirectory.file("outputs/feature-dex/${project.name}.dex")
    outputs.file(outputDex)

    doLast {
        val sdkDir = android.sdkDirectory
        val d8 = sdkDir.resolve("build-tools/${android.buildToolsVersion}/d8")
        val tempDir = layout.buildDirectory.dir("tmp/dex-merge").get().asFile.also { it.mkdirs() }
        val outDir = outputDex.get().asFile.parentFile.also { it.mkdirs() }

        val classesJar = layout.buildDirectory.file(
            "intermediates/aar_main_jar/release/syncReleaseLibJars/classes.jar"
        ).get().asFile

        val jarFiles = mutableListOf<String>()
        if (classesJar.exists()) jarFiles.add(classesJar.absolutePath)

        configurations["releaseRuntimeClasspath"]
            .resolvedConfiguration.lenientConfiguration
            .getArtifacts { dep -> dep !is ProjectDependency }
            .filter { a ->
                val group = a.moduleVersion.id.group
                a.file.exists() &&
                !a.file.absolutePath.contains("android.jar") &&
                group !in setOf(
                    "com.google.errorprone",
                    "org.checkerframework",
                    "com.google.j2objc",
                    "com.google.code.findbugs"
                )
            }
            .forEach { artifact ->
                when (artifact.extension) {
                    "jar" -> jarFiles.add(artifact.file.absolutePath)
                    "aar" -> {
                        val extracted = File(tempDir, "${artifact.name}-${artifact.moduleVersion.id.version}-classes.jar")
                        ZipFile(artifact.file).use { zip ->
                            zip.getEntry("classes.jar")?.let { entry ->
                                zip.getInputStream(entry).use { inp -> extracted.outputStream().use { inp.copyTo(it) } }
                            }
                        }
                        if (extracted.exists()) jarFiles.add(extracted.absolutePath)
                    }
                }
            }

        exec {
            commandLine(d8.absolutePath, "--release", "--min-api", "35",
                "--output", outDir.absolutePath, *jarFiles.toTypedArray())
        }
        File(outDir, "classes.dex").takeIf { it.exists() }?.renameTo(outputDex.get().asFile)
    }
}
