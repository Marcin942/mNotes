package com.mdev.mnotes.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mdev.mnotes.ui.importUris
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.data.CoverStyle
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.Chip
import com.mdev.mnotes.ui.EditorScreen
import com.mdev.mnotes.ui.LibState
import com.mdev.mnotes.ui.LibraryScreen
import com.mdev.mnotes.ui.MNotesTheme
import com.mdev.mnotes.ui.SHORTCUTS_CAT
import com.mdev.mnotes.ui.SettingsScreen
import com.mdev.mnotes.ui.mint

enum class Screen { LIBRARY, EDITOR, SETTINGS }

/** Stan nawigacji przeżywa zmiany konfiguracji; edytor jest tworzony tylko dla otwartego zeszytu. */
class UiModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<MNotesApp>()
    val lib = LibState()
    var screen by mutableStateOf(Screen.LIBRARY)
    var settingsCat by mutableIntStateOf(0)
    var editor by mutableStateOf<Editor?>(null)
    var locked by mutableStateOf(app.settings.appLock)

    fun open(id: String) {
        if (app.repo.meta(id) == null) return
        if (editor?.nbId == id) { screen = Screen.EDITOR; return }
        if (!app.openIds.add(id)) {
            android.widget.Toast.makeText(app, T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window"), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        editor?.let { app.openIds.remove(it.nbId); it.close() }
        app.repo.update(id) { it.copy(opened = System.currentTimeMillis()) }
        editor = Editor(app, id)
        screen = Screen.EDITOR
    }

    fun closeEditor() {
        editor?.let { app.openIds.remove(it.nbId) }
        editor?.close()
        editor = null
        screen = Screen.LIBRARY
    }

    /** Nowy zeszyt z domyślnymi ustawieniami (intencja CREATE_NOTE). */
    fun quickNote() {
        val s = app.settings
        val spec = PageSpec.of(PageFormat.fromId(s.newFormat) ?: PageFormat.A4, s.newLandscape, Template.fromCode(s.newTemplate), s.newPaper)
        val n = app.repo.createNotebook(T.t("Nowa notatka", "New note"), spec, 0xFF4C9A6A.toInt(), CoverStyle.STRIPES, null)
        open(n.id)
    }

    override fun onCleared() { editor?.let { app.openIds.remove(it.nbId) }; editor?.close() }
}

open class MainActivity : FragmentActivity() {
    private val ui: UiModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        val settings = app.settings
        app.repo.notice = { msg -> runOnUiThread { android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show() } }
        setContent {
            MNotesTheme(settings) {
                // FLAG_SECURE na żądanie: blokuje zrzuty ekranu i podgląd w ostatnich.
                LaunchedEffect(Unit) {
                    snapshotFlow { settings.secureFlag }.collect { on ->
                        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                Root(ui)
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null || ui.locked) return
        i.getStringExtra(EXTRA_NOTEBOOK)?.let { i.removeExtra(EXTRA_NOTEBOOK); ui.open(it) }
        when (i.action) {
            Intent.ACTION_CREATE_NOTE -> ui.quickNote()
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                // Pliki z innych aplikacji („Otwórz w mNotes”, „Udostępnij”) stają się nowymi zeszytami.
                val uris = if (i.action == Intent.ACTION_VIEW) listOfNotNull(i.data)
                else listOfNotNull(if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))
                if (uris.isEmpty()) return
                lifecycleScope.launch {
                    val made = importUris(app, uris, null, i.type)
                    if (made.size == 1) ui.open(made[0].id)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        ui.editor?.doc?.flush()
        if (!isChangingConfigurations) app.sync.auto()
        if (!isChangingConfigurations && app.settings.appLock) ui.locked = true
    }

    @Composable
    private fun Root(ui: UiModel) {
        val m = mint
        // Pasek systemowy dopasowany do motywu: ikony jasne na ciemnym tle.
        DisposableEffect(m.dark) {
            val style = if (m.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            enableEdgeToEdge(style, style)
            onDispose { }
        }
        if (ui.locked && app.settings.appLock) {
            LockScreen(onUnlock = { ui.locked = false }, prompt = ::prompt)
            return
        }
        when (ui.screen) {
            Screen.LIBRARY -> LibraryScreen(ui.lib, onOpen = ui::open, onSettings = { ui.settingsCat = 0; ui.screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(onBack = { ui.screen = if (ui.editor != null) Screen.EDITOR else Screen.LIBRARY }, startCat = ui.settingsCat)
            Screen.EDITOR -> {
                val ed = ui.editor
                if (ed == null) { ui.screen = Screen.LIBRARY } else EditorScreen(ed, onExit = ui::closeEditor, onSettings = { ui.settingsCat = 0; ui.screen = Screen.SETTINGS }, onShortcuts = { ui.settingsCat = SHORTCUTS_CAT; ui.screen = Screen.SETTINGS })
            }
        }
        // Z ustawień wracamy do poprzedniego ekranu (biblioteki lub edytora).
        if (ui.screen == Screen.SETTINGS) BackHandler { ui.screen = if (ui.editor != null) Screen.EDITOR else Screen.LIBRARY }
    }

    @Composable
    private fun LockScreen(onUnlock: () -> Unit, prompt: (() -> Unit, (String) -> Unit) -> Unit) {
        var error by remember { mutableStateOf("") }
        val m = mint
        LaunchedEffect(Unit) { prompt(onUnlock) { error = it } }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = m.accent)
            Text(T.t("mNotes jest zablokowane", "mNotes is locked"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            if (error.isNotEmpty()) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            Chip(T.t("Odblokuj", "Unlock"), true, { prompt(onUnlock) { error = it } }, modifier = Modifier.padding(top = 12.dp))
        }
    }

    private fun prompt(ok: () -> Unit, fail: (String) -> Unit) {
        val auth = LOCK_AUTH
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Brak zabezpieczenia ekranu na urządzeniu: nie zamykamy użytkownika poza własnymi notatkami.
            fail(T.t("Ustaw blokadę ekranu w systemie, aby użyć blokady aplikacji.", "Set a screen lock in system settings to use app lock."))
            app.settings.appLock = false
            ok()
            return
        }
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = ok()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = fail(errString.toString())
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("mNotes").setSubtitle(T.t("Potwierdź tożsamość", "Confirm your identity")).setAllowedAuthenticators(auth).build(),
        )
    }

    companion object {
        const val EXTRA_NOTEBOOK = "com.mdev.mnotes.NOTEBOOK"
        const val LOCK_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /** Czy urządzenie ma ustawioną blokadę ekranu / biometrię (potrzebne, by włączyć blokadę aplikacji). */
        fun canLock(a: FragmentActivity) = BiometricManager.from(a).canAuthenticate(LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/**
 * Dodatkowe okno aplikacji (NAV-008): osobne zadanie obok pierwszego (podział ekranu, okno swobodne, DeX).
 * Różni się od [MainActivity] tylko trybem uruchamiania — każde wywołanie tworzy nową instancję.
 */
class NoteWindowActivity : MainActivity()

/** Otwiera [nbId] (lub bibliotekę, gdy null) w nowym oknie obok bieżącego. */
fun android.content.Context.openNewWindow(nbId: String?) {
    val i = Intent(this, NoteWindowActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    if (nbId != null) i.putExtra(MainActivity.EXTRA_NOTEBOOK, nbId)
    startActivity(i)
}
