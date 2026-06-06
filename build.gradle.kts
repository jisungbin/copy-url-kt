plugins {
    kotlin("jvm") version "2.4.0-RC2"
    id("org.jetbrains.compose") version "1.12.0-alpha01"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0-RC2"
    application
}

group = "dev.jisungbin"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna:5.18.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.jisungbin.copyurl.MainKt"
}
