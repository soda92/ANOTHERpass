import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

val stageSystemDistribution by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages a system-JRE distribution (jars + Skiko natives, no runtime image)"
    dependsOn(tasks.named("jar"))
    // Downloads/unpacks the platform Skiko native library into build/compose/tmp/skiko.
    dependsOn(tasks.named("unpackDefaultComposeDesktopJvmApplicationResources"))

    into(layout.buildDirectory.dir("system-dist"))

    into("lib") {
        from(tasks.named("jar"))
        from(runtimeClasspath) {
            include("*.jar")
            exclude("skiko-awt-runtime-*.jar")
        }
        // Skiko verifies the .so against the .sha256 sidecar, so keep both.
        from(layout.buildDirectory.dir("compose/tmp/skiko")) {
            include("libskiko-*.so", "libskiko-*.so.sha256")
        }
    }

    into("bin") {
        from(rootProject.file("packaging/anotherpass.sh"))
        rename { "anotherpass" }
    }

    doLast {
        layout.buildDirectory.file("system-dist/bin/anotherpass")
            .get().asFile.setExecutable(true)
    }
}
