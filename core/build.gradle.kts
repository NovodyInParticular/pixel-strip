import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val gdxVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootProject.file("assets")
    testLogging { showStandardStreams = true }
    // the tests read the game data, so a data change must re-run them (not reuse a cached pass)
    inputs.dir(rootProject.file("assets/data"))
    inputs.dir(rootProject.file("assets/cars")).withPropertyName("sprites")
    inputs.file(rootProject.file("assets/paint/ramps.png"))
    inputs.dir(rootProject.file("shared"))
    inputs.file(rootProject.file("car_references.md"))
    inputs.property("regen", System.getenv("REGEN") ?: "")
}
