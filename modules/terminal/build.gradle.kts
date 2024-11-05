import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "1.9.20"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // Better test output on the command line.
    id("com.adarshr.test-logger") version "3.0.0"

    // Viewing the task graph.
    id("com.dorongold.task-tree") version "2.1.0"

    `maven-publish`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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
    kotlinOptions {
        jvmTarget = "17"
        languageVersion = "1.7"
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn", "-Xjvm-default=all")
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
