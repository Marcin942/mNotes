package com.mdev.mnotes.core.image

import java.io.File
import java.util.Properties

/**
 * `KREDA_DIR`, `KREDA_OUT`, `KREDA_EXTRA`: najpierw zmienna środowiska, potem `app/build/kreda.properties`
 * (np. `KREDA_DIR=/ścieżka/do/blackboard`), gdy zmiennych nie da się przekazać przez Gradle. `build/` jest poza
 * repozytorium, więc lokalne ścieżki tam nie trafiają.
 */
internal fun kredaEnv(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }

private val localProps: Properties by lazy {
    Properties().apply { File("build/kreda.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
}
