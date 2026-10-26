plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val natives: Configuration by configurations.creating

android {
    namespace = "io.github.novodyinparticular.pixelstrip"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.novodyinparticular.pixelstrip"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("../assets")
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Release signing: the keystore lives outside the repo; ~/.gradle/gradle.properties names it with
    // pixelstripKeystore=/path/to/pixel-strip-release.keystore, and its password sits beside it in <keystore>.pass.
    // Every update must be signed with this same key, or phones won't install it over the old version.
    val releaseKeystore = providers.gradleProperty("pixelstripKeystore").orNull?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            val pass = file("${releaseKeystore.path}.pass").readText().trim()
            storeFile = releaseKeystore
            storePassword = pass
            keyAlias = "pixelstrip"
            keyPassword = pass
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
}

// libGDX ships its Android .so files inside jars; unpack them into libs/<abi>/ before packaging.
val copyAndroidNatives by tasks.registering {
    inputs.files(natives)
    outputs.dir("libs")
    doFirst {
        natives.files.forEach { jar ->
            val abi = jar.name.substringAfter("natives-").substringBefore(".jar")
            val outDir = file("libs/$abi")
            outDir.mkdirs()
            project.copy {
                from(zipTree(jar))
                into(outDir)
                include("*.so")
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
