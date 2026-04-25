plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Resolve backend URL + queue endpoint. Precedence: env var > server-config.yaml > empty.
// See .env.template at repo root for the full list of env vars.
val serverConfigFile = file("server-config.yaml")
val yamlConfig = mutableMapOf<String, String>()
if (serverConfigFile.exists()) {
    serverConfigFile.readLines().forEach { line ->
        Regex("""^\s*backend_url:\s*"(.+)"""").find(line)?.let { yamlConfig["backend_url"] = it.groupValues[1] }
        Regex("""^\s*queue_host:\s*"(.+)"""").find(line)?.let { yamlConfig["queue_host"] = it.groupValues[1] }
        Regex("""^\s*queue_port:\s*(\d+)\s*$""").find(line)?.let { yamlConfig["queue_port"] = it.groupValues[1] }
    }
}
val backendUrl: String = System.getenv("BACKEND_URL")?.takeIf { it.isNotBlank() } ?: yamlConfig["backend_url"] ?: ""
val queueHost: String = System.getenv("QUEUE_HOST")?.takeIf { it.isNotBlank() } ?: yamlConfig["queue_host"] ?: ""
val queuePort: Int = (System.getenv("QUEUE_PORT")?.takeIf { it.isNotBlank() } ?: yamlConfig["queue_port"] ?: "0").toInt()

android {
    namespace = "pl.czak.learnlauncher.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "pl.czak.imageviewer.app7"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BACKEND_URL", "\"${backendUrl}\"")
        buildConfigField("String", "QUEUE_HOST", "\"${queueHost}\"")
        buildConfigField("int", "QUEUE_PORT", "${queuePort}")
    }

    // Signing pulled from env vars (see .env.template). Defaults below are placeholders;
    // the release/debug keystore files in this folder are text stubs until regenerated.
    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getenv("ANDROID_DEBUG_KEYSTORE_PATH") ?: "debug-learnlauncher.keystore")
            storePassword = System.getenv("ANDROID_DEBUG_STORE_PASSWORD") ?: "PLACEHOLDER"
            keyAlias = System.getenv("ANDROID_DEBUG_KEY_ALIAS") ?: "learnlauncher"
            keyPassword = System.getenv("ANDROID_DEBUG_KEY_PASSWORD") ?: "PLACEHOLDER"
        }
        create("release") {
            storeFile = file(System.getenv("ANDROID_RELEASE_KEYSTORE_PATH") ?: "release-learnlauncher.keystore")
            storePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD") ?: "PLACEHOLDER"
            keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS") ?: "learnlauncher_release"
            keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD") ?: "PLACEHOLDER"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.auth)
}
