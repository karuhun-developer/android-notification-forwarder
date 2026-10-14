plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.karuhundeveloper.notification"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.karuhundeveloper.notification"
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("releaseVersionName").getOrElse("1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Tells Room's KSP processor where to write the schema JSON files.
        // These files should be committed to source control to enable migration testing.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.workingDir = projectDir }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)           // Coroutines / Flow extensions
    ksp(libs.room.compiler)                 // Annotation processor (replaces kapt)
    androidTestImplementation(libs.room.testing)

    // ── JSON serialization (used by TypeConverters & NotificationPusher) ─────
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation(libs.gson)

    // ── OkHttp ───────────────────────────────────────────────────────────────
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)   // logs request/response; debug builds only

    // ── Jetpack Compose ───────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.work:work-testing:2.11.0")
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
