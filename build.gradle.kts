
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    // Need the kotlin plugin for Dokka to work.
    kotlin("jvm") version "1.9.20"

    // Dokka for documentation site generation.
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "dev.progress4j"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

tasks.dokkaHtmlMultiModule {
    moduleName.set("progress4j")
}

// Generate Javadoc for API module
project(":progress4j-api") {
    tasks.register<Javadoc>("generateApiJavadoc") {
        source = sourceSets["main"].allJava
        classpath = sourceSets["main"].compileClasspath
        setDestinationDir(file("${rootProject.projectDir}/docs/docs/javadoc"))
    }
}

// Task to copy Dokka output to docs directory
tasks.register<Copy>("copyDokkaToDocs") {
    dependsOn("dokkaHtmlMultiModule")
    from("build/dokka/htmlMultiModule")
    into("docs/docs/dokka")
}

// Task to prepare all documentation
tasks.register("prepareAllDocs") {
    dependsOn(":progress4j-api:generateApiJavadoc")
    dependsOn("copyDokkaToDocs")
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
    // Need to apply the Kotlin plugin for Dokka to run correctly.
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets {
            configureEach {
                includes.from("module.md")

                // Suppress a package
                perPackageOption {
                    matchingRegex.set(""".*\.impl.*""")
                    suppress.set(true)
                }

                documentedVisibilities.set(listOf(DokkaConfiguration.Visibility.PUBLIC, DokkaConfiguration.Visibility.PROTECTED))

                jdkVersion.set(17)

                skipDeprecated.set(true)
            }
        }
    }
}
