plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // Better test output on the command line.
    id("com.adarshr.test-logger") version "3.0.0"

    // Viewing the task graph.
    id("com.dorongold.task-tree") version "2.1.0"
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
    // Useful annotations.
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    targetCompatibility = "17"
    sourceCompatibility = "17"
}
