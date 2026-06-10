plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.iflowmonitor"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Routing engine — pinned to the 9.9.1 line (latest patch) to mirror the tenant's
    // Saxon-EE 9.9.1.6 routing behaviour. See routing-mvp-JOURNAL.md "Version decisions".
    implementation("net.sf.saxon:Saxon-HE:9.9.1-8")
    // YAML manifest parsing (untyped Map traversal → precise AC error messages).
    implementation("org.yaml:snakeyaml:2.6")

    testImplementation(platform("org.junit:junit-bom:5.14.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("com.iflowmonitor.iflowlab.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
