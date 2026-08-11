import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.4.10"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // Better test output on the command line.
    id("com.adarshr.test-logger") version "4.0.0"

    // Viewing the task graph.
    id("com.dorongold.task-tree") version "2.1.0"

    `maven-publish`
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(project(":progress4j-api"))
    implementation(project(":progress4j-utils"))

    // Colourful terminal output.
    api(libs.com.github.ajalt.mordant)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    targetCompatibility = "17"
    sourceCompatibility = "17"
}

// Make Kotlin use the same JDK as Java.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    testlogger {
        setTheme("mocha-parallel")
        showStandardStreams = false
        showFailedStandardStreams = true
        logLevel = LogLevel.QUIET
        showStackTraces = true
        showFullStackTraces = true
        showExceptions = true
    }
}

// TODO: Figure out publishing to Maven Central.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "dev.progress4j"
            afterEvaluate {
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }

    repositories {
        maven {
            url = rootProject.projectDir.resolve("build/repo").toURI()
            name = "hydraulic"
        }
    }
}
