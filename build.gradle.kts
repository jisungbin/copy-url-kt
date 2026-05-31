plugins {
    kotlin("jvm") version "2.4.0-RC2"
    application
}

group = "dev.jisungbin"
version = "1.0.0"

dependencies {
    implementation("net.java.dev.jna:jna:5.18.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.jisungbin.copyurl.MainKt"
    // macOS GUI(NSApp 런루프 + Carbon 핫키)는 메인 스레드(thread 0)에서 돌아야 한다.
    applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}
