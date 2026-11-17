plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
}

allprojects {
    group = "de.jepfa.anotherpass"
    version = "0.1.0-SNAPSHOT"
}
