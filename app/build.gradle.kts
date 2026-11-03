plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Wersja z ostatniego commitu (jak w mChochliku, D-018): versionCode to minuty od 2026-01-01 UTC.
// providers.exec, bo configuration cache nie pozwala uruchamiać procesów wprost.
val gitHead = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct %h")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.orElse("")
val gitParts = gitHead.get().split(" ").filter { it.isNotEmpty() }
val naturalVersionCode = gitParts.getOrNull(0)?.toLongOrNull()
    ?.let { ((it - 1_767_225_600L) / 60).toInt().coerceAtLeast(1) } ?: 1
// Build ratunkowy demona (D-039) dostaje podbity kod, żeby system przyjął go jako aktualizację.
// Ręcznie podbity kod blokuje telefon (incydent 99999999), więc większy skok przechodzi tylko z flagą demona.
val appVersionCode = providers.gradleProperty("chochlik.versionCode").orNull?.toIntOrNull() ?: naturalVersionCode
check(providers.gradleProperty("chochlik.rescue").isPresent || appVersionCode <= naturalVersionCode + 1_000_000) {
    "chochlik.versionCode=$appVersionCode przekracza kod naturalny o ponad 1 000 000 (WERSJE.md)"
}
// D-034: wersja z ostatniego tagu wydania; po nim `+N.sha` (snapshot), na samym tagu `+sha`.
val gitDescribe = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--long", "--abbrev=7")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.orElse("")
val appVersionName = Regex("""^v(.+)-(\d+)-g([0-9a-f]+)$""").matchEntire(gitDescribe.get())?.let { m ->
    val (tag, ahead, sha) = m.destructured
    if (ahead == "0") "$tag+$sha" else "$tag+$ahead.$sha"
} ?: ("0.0.0" + (gitParts.getOrNull(1)?.let { "+$it" } ?: ""))

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
        versionCode = appVersionCode
        versionName = appVersionName
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
