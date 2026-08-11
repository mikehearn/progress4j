plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":progress4j-api"))
    implementation(project(":progress4j-utils"))
    implementation(project(":progress4j-terminal"))
}

application {
    mainClass.set("ProgressTrackingDemo")
}
