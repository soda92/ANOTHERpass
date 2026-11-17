import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage"))
    implementation(project(":service"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "de.jepfa.yapm.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "anotherpass"
            packageVersion = "0.1.0"
            description = "ANOTHERpass desktop"
        }
    }
}

// Fast packaging distribution for system-JRE installs. Unlike
// createDistributable (which runs jpackage and builds a runtime image), this
// only copies the runtime classpath jars plus the Skiko native library.
val runtimeClasspath = configurations.named("runtimeClasspath")
val skikoRuntimeJar = runtimeClasspath.get()
    .first { it.isFile && it.name.startsWith("skiko-awt-runtime-") }

val stageSystemDistribution by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages a system-JRE distribution (jars + Skiko natives, no runtime image)"
    dependsOn(tasks.named("jar"))

    into(layout.buildDirectory.dir("system-dist"))

    into("lib") {
        from(tasks.named("jar"))
        from(runtimeClasspath) {
            include("*.jar")
            exclude("skiko-awt-runtime-*.jar")
        }
    }

    into("bin") {
        from(rootProject.file("packaging/anotherpass.sh"))
        rename { "anotherpass" }
    }

    doLast {
        // Extract the Skiko native library and checksum sidecar from the fat
        // runtime jar. Done explicitly because Skiko refuses to load a library
        // whose checksum sidecar is missing or does not match.
        val libDir = layout.buildDirectory.dir("system-dist/lib").get().asFile
        libDir.mkdirs()
        ZipFile(skikoRuntimeJar).use { zip ->
            zip.entries().asSequence()
                .filter { entry ->
                    if (entry.isDirectory) return@filter false
                    val name = entry.name.substringAfterLast('/')
                    name.startsWith("libskiko-") &&
                        (name.endsWith(".so") || name.endsWith(".so.sha256"))
                }
                .forEach { entry ->
                    val target = libDir.resolve(entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
        }

        layout.buildDirectory.file("system-dist/bin/anotherpass")
            .get().asFile.setExecutable(true)
    }
}
