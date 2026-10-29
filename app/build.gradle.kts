import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.safeher.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.safeher.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val placesApiKey: String = (project.findProperty("PLACES_API_KEY") as? String)
            ?: run {
                val localProps = Properties()
                val localPropsFile = rootProject.file("local.properties")
                if (localPropsFile.exists()) {
                    localPropsFile.inputStream().use { stream ->
                        localProps.load(stream)
                    }
                }
                localProps.getProperty("PLACES_API_KEY", "") ?: ""
            }
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
        val webClientId: String = (project.findProperty("FIREBASE_WEB_CLIENT_ID") as? String)
            ?: run {
                val localProps = Properties()
                val localPropsFile = rootProject.file("local.properties")
                if (localPropsFile.exists()) {
                    localPropsFile.inputStream().use { stream ->
                        localProps.load(stream)
                    }
                }
                localProps.getProperty("FIREBASE_WEB_CLIENT_ID", "") ?: ""
            }
        buildConfigField("String", "FIREBASE_WEB_CLIENT_ID", "\"$webClientId\"")
        buildConfigField("Boolean", "IS_DEMO_DEFAULT", "false")
    }

    flavorDimensions += "mode"
    productFlavors {
        create("demo") {
            dimension = "mode"
            versionNameSuffix = "-demo"
            buildConfigField("Boolean", "IS_DEMO_DEFAULT", "true")
        }
        create("prod") {
            dimension = "mode"
            buildConfigField("Boolean", "IS_DEMO_DEFAULT", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)
    implementation(libs.libphonenumber)
    implementation(libs.places)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // Android Credential Manager for Google Sign-In
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.vectordrawable") {
            useVersion("1.2.0")
            because("Prevent duplicate androidx.vectordrawable namespace conflicts")
        }
    }
}

tasks.register("testDebugUnitTest") {
    dependsOn("testDemoDebugUnitTest")
}

