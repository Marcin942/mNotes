plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mdev.mnotes"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mdev.mnotes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Dwa warianty z jednego kodu: `core` to otwarta aplikacja bez AI i bez przetwarzania zdjęć tablicy, `lab` dokłada
    // kredę, rozpoznawanie i Nearby z własnego katalogu `src/lab/`. Ten sam applicationId: to jedna aplikacja w dwóch
    // odmianach, nie dwie instalacje obok siebie.
    flavorDimensions += "wariant"
    productFlavors {
        create("core") { dimension = "wariant" }
        create("lab") { dimension = "wariant" }
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
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs.useLegacyPackaging = false
    }
    testOptions {
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.input.motionprediction)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    // Jedyna nowa zależność nocy (uzgodniony wyjątek, powód w docs/NOTATKI-NOC.md): łącze Nearby tylko w wariancie lab,
    // za przełącznikiem w ustawieniach; wariant core jej nie ma.
    "labImplementation"(libs.play.services.nearby)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
