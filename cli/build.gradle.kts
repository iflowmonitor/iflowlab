plugins {
    kotlin("jvm")
    application
}

group = "com.iflowmonitor"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":engine"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("com.iflowmonitor.iflowlab.cli.MainKt")
}
