package com.mdev.mnotes.app

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.mdev.mnotes.core.prefs.AppLanguage
import com.mdev.mnotes.data.Settings

/**
 * 3c-A15: język z Ustawień ↔ systemowy „Język aplikacji” (Android 13+, lista języków w `res/xml/locales_config.xml`).
 * Wołane przy starcie, po zmianie w Ustawieniach i po zmianie konfiguracji (wybór w ustawieniach systemu).
 */
object AppLocales {
    fun sync(ctx: Context, s: Settings) {
        if (Build.VERSION.SDK_INT < 33) return
        val lm = ctx.getSystemService(LocaleManager::class.java) ?: return
        val d = AppLanguage.reconcile(s.lang, lm.applicationLocales.toLanguageTags(), s.langSys.takeIf { it != "?" })
        s.langSys = d.last
        if (d.mode != s.lang) s.lang = d.mode
        d.push?.let { lm.applicationLocales = if (it.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(it) }
    }
}
