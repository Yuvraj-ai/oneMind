import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.onemind.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onemind.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * Release signing.
     *
     * Credentials come from `keystore.properties` (git-ignored) or, in CI, from
     * environment variables. Neither the keystore nor its passwords are ever in the
     * repository: anyone holding them can ship an update that Android will install
     * over a user's existing oneMind, so they are the single most sensitive artifact
     * in the project.
     *
     * When no credentials are present the block is simply absent and `assembleRelease`
     * produces an unsigned APK. That is deliberate — it lets a contributor verify a
     * release build compiles without needing the signing key, and it fails visibly at
     * install time rather than silently shipping something unverifiable.
     *
     * See RELEASING.md.
     */
    signingConfigs {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = Properties().apply {
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { stream -> load(stream) }
            }
        }

        val storePath = keystoreProperties.getProperty("storeFile")
            ?: System.getenv("ONEMIND_KEYSTORE_PATH")

        if (storePath != null && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("ONEMIND_KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("ONEMIND_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("ONEMIND_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only when credentials were found. Without this the release APK is
            // unsigned, which is the honest outcome of building without the key.
            signingConfig = signingConfigs.findByName("release")

            ndk {
                /*
                 * Ship ARM only.
                 *
                 * MediaPipe and ML Kit contribute large native libraries — around
                 * 45MB per ABI — so a universal APK came to 152MB, of which roughly
                 * 60MB was x86 and x86_64. Those exist for emulators and a handful of
                 * Chromebooks, not for the phones this app targets, and every user
                 * was downloading all four architectures to run one.
                 *
                 * Debug builds keep every ABI, so the x86_64 emulator still runs the
                 * instrumented suite. This filter applies to release only.
                 */
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    packaging {
        jniLibs {
            /*
             * Drop the LLM inference runtime.
             *
             * `tasks-text` depends on `tasks-core`, which is one AAR carrying the
             * native library for every MediaPipe task type — including
             * `libmediapipe_tasks_textgenai_jni.so`, about 14MB per ABI, which
             * implements on-device LLM inference. oneMind uses `TextEmbedder` only,
             * and local generative inference is deferred (ADR-0002), so nothing in
             * the app can reach that runtime.
             *
             * Excluded rather than tolerated because 28MB of the two shipped ABIs is
             * a quarter of the download for code that cannot execute. If a future
             * change revives local inference, this exclusion has to go with it, and
             * the symptom of forgetting would be an UnsatisfiedLinkError on first
             * use rather than a build failure — hence the note.
             */
            excludes += "**/libmediapipe_tasks_textgenai_jni.so"
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
    }

    packaging {
        resources {
            // mockk-android pulls in JUnit 5 transitively, and several of those
            // jars each ship their own META-INF licence files, which collide when
            // merged into the test APK.
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/LICENSE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

/**
 * Export Room schemas so migrations can be verified against them. Ticket #10
 * introduces the first migration, and a migration test needs the schema of the
 * version it migrates from.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * MigrationTestHelper reads the exported schemas from the test APK's assets, so
 * the schema directory has to be on the androidTest asset path.
 */
android.sourceSets.getByName("androidTest") {
    assets.srcDir("$projectDir/schemas")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // On-device OCR
    implementation(libs.mlkit.text.recognition)

    // On-device text embeddings
    implementation(libs.mediapipe.tasks.text)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // Real org.json for JVM tests; Android's own is a stub that throws.
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Android Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.work.testing)
}
