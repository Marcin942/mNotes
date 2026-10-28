package com.mdev.mnotes.render

import android.content.res.AssetManager
import android.graphics.Typeface
import com.mdev.mnotes.core.model.TextBox

/**
 * Czcionki dołączone do aplikacji (3c-A11, `assets/fonts/`, SIL OFL, licencje w `assets/fonts/licencje/`). Rodziny
 * 0–3 ([TextBox.FAMILY_SANS] … [TextBox.FAMILY_HAND]) zostają przy czcionkach systemowych, więc stare zeszyty wyglądają
 * jak dotąd; dołączone mają własne numery od [TextBox.FAMILY_LITERATA]. Pliki zmienne (oś `wght`) dostają wagę 400 albo
 * 700 przez [Typeface.Builder.setFontVariationSettings], więc pogrubienie jest prawdziwe, nie dorysowane; kursywa jest
 * pochylana przez system (bez osobnych plików kursywy, żeby nie powiększać APK). Brakujące znaki (np. greckie w
 * Caveat) Android bierze z czcionki systemowej.
 */
object AppFonts {
    private var assets: AssetManager? = null
    private val cache = HashMap<Int, Typeface?>()

    /** Wywoływane raz przy starcie aplikacji; bez tego (testy JVM, podglądy) dołączone rodziny są systemowe. */
    fun init(am: AssetManager) { assets = am }

    /** Plik i czy jest zmienny (oś `wght`); osobny plik pogrubienia dla rodzin bez osi. */
    private class Face(val file: String, val variable: Boolean, val boldFile: String? = null)

    private val faces = mapOf(
        TextBox.FAMILY_LITERATA to Face("literata.ttf", true),
        TextBox.FAMILY_INTER to Face("inter.ttf", true),
        TextBox.FAMILY_ATKINSON to Face("atkinson.ttf", false, "atkinson-bold.ttf"),
        TextBox.FAMILY_JETBRAINS to Face("jetbrainsmono.ttf", true),
        TextBox.FAMILY_CAVEAT to Face("caveat.ttf", true),
    )

    /** Krój dołączonej rodziny [family] (pogrubiony przy [bold]); null, gdy to rodzina systemowa albo pliku brak. */
    fun typeface(family: Int, bold: Boolean): Typeface? {
        val face = faces[family] ?: return null
        val key = family * 2 + if (bold) 1 else 0
        return synchronized(cache) { cache.getOrPut(key) { load(face, bold) } }
    }

    private fun load(face: Face, bold: Boolean): Typeface? {
        val am = assets ?: return null
        return runCatching {
            when {
                face.variable -> Typeface.Builder(am, "fonts/${face.file}").setFontVariationSettings("'wght' ${if (bold) 700 else 400}").build()
                bold && face.boldFile != null -> Typeface.Builder(am, "fonts/${face.boldFile}").build()
                else -> Typeface.Builder(am, "fonts/${face.file}").build()
            }
        }.getOrNull()
    }
}
