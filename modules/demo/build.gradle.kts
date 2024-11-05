plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":progress4j-api"))
    implementation(project(":progress4j-utils"))
    implementation(project(":progress4j-terminal"))
}

application {
    mainClass.set("ProgressTrackingDemo")
}
