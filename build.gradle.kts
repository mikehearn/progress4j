import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    // Need the kotlin plugin for Dokka to work.
    kotlin("jvm") version "1.9.20"

    // Dokka for documentation site generation.
    id("org.jetbrains.dokka") version "1.9.10"
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

tasks.withType<DokkaMultiModuleTask>().configureEach {
    includes.from("module.md")
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
    // Need to apply the Kotlin plugin for Dokka to run correctly.
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets {
            configureEach {
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
