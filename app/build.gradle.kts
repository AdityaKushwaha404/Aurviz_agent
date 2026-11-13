import java.util.Properties

// Optional build-time Gemini key (kept in the git-ignored local.properties) so an APK handed to
// evaluators works out of the box. Never commit the key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val bundledGeminiKey: String = (localProperties.getProperty("gemini.api.key") ?: "").trim()
// Reviewer test account shown inside the app. The password lives only in the git-ignored local.properties.
val testAccountEmail: String = (localProperties.getProperty("test.account.email") ?: "adityatesting404@gmail.com").trim()
val testAccountPassword: String = (localProperties.getProperty("test.account.password") ?: "").trim()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aurviz.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aurviz.agent"
        buildConfigField("String", "GEMINI_API_KEY_DEFAULT", "\"$bundledGeminiKey\"")
        buildConfigField("String", "TEST_ACCOUNT_EMAIL", "\"$testAccountEmail\"")
        buildConfigField("String", "TEST_ACCOUNT_PASSWORD", "\"${testAccountPassword.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "API_BASE_URL", "\"https://aurviz-backend.onrender.com\"")
        // Stable Gemini model used by every AI request. Update here only.
        buildConfigField("String", "GEMINI_MODEL", "\"gemini-3.5-flash-lite\"")
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    buildTypes {
        release {
            resValue("string", "shortcut_package", "com.aurviz.agent")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "shortcut_package", "com.aurviz.agent.debug")
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // OkHttp (for direct REST API and web automation DOM scraping / HTTP fetching)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Encrypted Preferences (Keystore-backed security)
    implementation(libs.androidx.security.crypto)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json implementation for JVM unit tests (android.jar only exposes stubs).
    testImplementation(libs.org.json)

    // Debugging
    debugImplementation(libs.androidx.ui.tooling)
}
