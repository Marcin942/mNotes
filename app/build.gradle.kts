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

    // Dwa warianty z jednego kodu: `foss` bez żadnej niewolnej biblioteki (F-Droid), `gms` dokłada łącze z telefonem
    // przez Nearby z Usług Google Play (`src/gms/`). Ten sam applicationId: to jedna aplikacja w dwóch odmianach.
    flavorDimensions += "wariant"
    productFlavors {
        create("foss") { dimension = "wariant" }
        create("gms") { dimension = "wariant" }
    }

    // Gałąź `kreda` dokłada wyłącznie katalogi `src/kreda` i `src/testKreda` (żadnego pliku z main nie zmienia, więc
    // scalanie main → kreda nie ma konfliktów); bez nich aplikacja kompiluje się bez kredy.
    sourceSets {
        getByName("main") { if (file("src/kreda/java").isDirectory) kotlin.srcDir("src/kreda/java") }
        getByName("test") { if (file("src/testKreda/java").isDirectory) kotlin.srcDir("src/testKreda/java") }
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
    // Jedyna niewolna zależność (licencje w NOTICE.md): łącze Nearby tylko w wariancie gms, za przełącznikiem
    // w ustawieniach; wariant foss jej nie ma.
    "gmsImplementation"(libs.play.services.nearby)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Demon Chochlik i mStore znają jedno zadanie testów (`:app:testDebugUnitTest`) i jeden plik APK
// (`app/build/outputs/apk/debug/app-debug.apk`), a dwa warianty dają po dwa tych samych. Zadanie zbiorcze nosi dokładnie tę
// nazwę (Gradle nie szuka wtedy skrótów), a instalowanym snapshotem jest wariant gms, czyli aplikacja z łączem Nearby.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Testy jednostkowe obu wariantów (foss i gms)."
    dependsOn("testFossDebugUnitTest", "testGmsDebugUnitTest")
}

val kopiujApkDebug = tasks.register<Copy>("kopiujApkDebug") {
    description = "Kopiuje APK wariantu gms pod nazwę, której szukają demon i mStore."
    from(layout.buildDirectory.file("outputs/apk/gms/debug/app-gms-debug.apk"))
    into(layout.buildDirectory.dir("outputs/apk/debug"))
    rename { "app-debug.apk" }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(kopiujApkDebug) }
