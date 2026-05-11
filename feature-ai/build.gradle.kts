import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.rendy.classnote.feature.ai"
    compileSdk = 35

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
}

dependencies {
    implementation(project(":feature-api"))
    implementation(libs.kotlinx.coroutines.android)
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
            .getArtifacts { true }
            .filter { a ->
                a.file.exists() &&
                !a.file.absolutePath.contains("android.jar") &&
                a.moduleVersion.id.group != "com.rendy.classnote"
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
