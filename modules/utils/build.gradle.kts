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

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Useful annotations.
    compileOnly("org.jetbrains:annotations:24.1.0")

    // UNIT TESTING
    // Hamkrest matchers for better error messages in both tests and prod code.
    testImplementation("com.natpryce:hamkrest:1.8.0.1")

    // Kotlin extras also needed in tests.
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use JUnit 5 for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")

    // JUnit Pioneer: Useful extensions and utilities for tests.
    testImplementation("org.junit-pioneer:junit-pioneer:2.2.0")
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
