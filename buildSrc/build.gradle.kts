// The version-name logic of app/build.gradle.kts (see VersionName.kt), with its own unit tests:
// ./gradlew -p buildSrc test
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
