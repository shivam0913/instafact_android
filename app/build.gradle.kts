plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase config is tracked in git (it holds no secret - the API key in it is
// restricted server-side), so every build has it. Applied unconditionally: the plugin
// used to be skipped when the file was absent, which produced a perfectly valid APK
// with push notifications and analytics silently switched off.
apply(plugin = "com.google.gms.google-services")

// Belt and braces for release builds. Without the file the google-services plugin fails
// with a message about a missing resource; this says what is actually wrong and where to
// get the file, and fails before anything is compiled.
tasks.register("verifyFirebaseConfig") {
    val configFile = file("google-services.json")
    doLast {
        if (!configFile.exists()) {
            throw GradleException(
                "app/google-services.json is missing. Download it from the Firebase " +
                    "console (Project settings -> Your apps -> co.instafact) and commit " +
                    "it. Without it the build produces an app with no push notifications " +
                    "and no analytics.",
            )
        }
    }
}

// preBuild is the common ancestor of every variant, so this runs before any compilation
// for assembleDebug, assembleRelease and bundleRelease alike.
tasks.named("preBuild") { dependsOn("verifyFirebaseConfig") }

// ---------------------------------------------------------------------------
// Version. Single source of truth is gradle.properties; -PVERSION_CODE and
// -PVERSION_NAME override it for CI.
//
// VERSION_CODE must increase for every upload - Play rejects a bundle that reuses one,
// and it tells you at upload time, which is a bad moment to find out. verifyReleaseConfig
// prints what is about to be shipped so a release is never built blind.
// ---------------------------------------------------------------------------
val appVersionCode = (project.findProperty("VERSION_CODE") as String?)?.trim()?.toIntOrNull()
val appVersionName = (project.findProperty("VERSION_NAME") as String?)?.trim()

// ---------------------------------------------------------------------------
// API base URL. The fallback exists so a fresh clone can build and run against a local
// server; it is deliberately NOT good enough for a release, which verifyReleaseConfig
// enforces rather than letting a store-ready bundle point at an emulator address.
// ---------------------------------------------------------------------------
val apiBaseUrlProperty = (project.findProperty("API_BASE_URL") as String?)?.trim()?.takeIf { it.isNotEmpty() }
val apiBaseUrl = apiBaseUrlProperty ?: "http://10.0.2.2:8000/"

tasks.register("verifyReleaseConfig") {
    val baseUrl = apiBaseUrlProperty
    val versionCode = appVersionCode
    val versionName = appVersionName
    doLast {
        val problems = mutableListOf<String>()

        when {
            baseUrl == null ->
                problems += "API_BASE_URL is not set. Add it to gradle.properties or pass " +
                    "-PAPI_BASE_URL=https://api.instafact.co/ . Without it the build falls " +
                    "back to http://10.0.2.2:8000/, an address that only exists inside an emulator."
            !baseUrl.startsWith("https://") ->
                problems += "API_BASE_URL is \"$baseUrl\". A release must use https:// - the " +
                    "network security config blocks cleartext, so this build would fail every " +
                    "request at runtime."
            !baseUrl.endsWith("/") ->
                problems += "API_BASE_URL is \"$baseUrl\". Retrofit requires a base URL ending " +
                    "in \"/\" and throws on startup otherwise."
        }

        if (versionCode == null) {
            problems += "VERSION_CODE is not set or is not a whole number. Set it in gradle.properties."
        }
        if (versionName.isNullOrBlank()) {
            problems += "VERSION_NAME is not set. Set it in gradle.properties."
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release configuration is not shippable:\n\n" +
                    problems.joinToString("\n\n") { "  - $it" },
            )
        }

        logger.lifecycle("")
        logger.lifecycle("  Release build -> $versionName ($versionCode) against $baseUrl")
        logger.lifecycle("  Play rejects a repeated VERSION_CODE. Bump it in gradle.properties before uploading.")
        logger.lifecycle("")
    }
}

// Release variants only - a debug build is allowed to point at a local server.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyReleaseConfig")
}

android {
    namespace = "com.instafact.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.instafact"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode ?: 1
        versionName = appVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // java.time is API 26+, but minSdk is 24. Desugaring back-ports it; without this
        // every screen that renders a timestamp dies with NoClassDefFoundError on
        // Android 7.0/7.1.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("io.coil-kt:coil:2.7.0")

    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
