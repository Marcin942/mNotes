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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.mdev.mnotes.data.ImportSource
import com.mdev.mnotes.data.SamsungSession
import com.mdev.mnotes.ui.LocalSamsungStart
import com.mdev.mnotes.ui.SamsungImportScreen
import com.mdev.mnotes.ui.importUris
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.ui.CoverPalette
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.Chip
import com.mdev.mnotes.core.nav.Tabs
import com.mdev.mnotes.ui.EditorScreen
import com.mdev.mnotes.ui.TabsCtl
import com.mdev.mnotes.ui.LibState
import com.mdev.mnotes.ui.LibraryScreen
import com.mdev.mnotes.ui.MNotesTheme
import com.mdev.mnotes.ui.COLORS_CAT
import com.mdev.mnotes.ui.EDITOR_CAT
import com.mdev.mnotes.ui.LocalBarSettings
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

    /** Otwarte okno importu Samsung Notes (podgląd, tryb, raport); null = brak. Żyje w modelu, więc obrót ekranu go nie gubi. */
    var samsung by mutableStateOf<SamsungSession?>(null)
        private set

    /** Dokłada pliki do otwartego okna importu albo otwiera nowe (folder docelowy ustala pierwsze wywołanie). */
    fun startSamsung(sources: List<ImportSource>, folderId: String?) {
        if (sources.isEmpty()) return
        val s = samsung ?: SamsungSession(app, viewModelScope, folderId).also { samsung = it }
        s.add(sources)
    }

    fun closeSamsung() {
        samsung?.cancel()
        samsung = null
    }

    /** Koniec zapisu: przy samych sukcesach okno się zamyka, a jedyny nowy zeszyt od razu się otwiera. */
    fun samsungFinished(failed: Int) {
        val s = samsung ?: return
        if (failed > 0) return
        val only = s.made.singleOrNull()
        samsung = null
        if (only != null) open(only.id)
    }

    /** Otwarte karty (tylko gdy włączone ustawienie „Otwieraj jako karty”). Edytor istnieje wyłącznie dla aktywnej. */
    var tabs by mutableStateOf(Tabs())
        private set

    fun open(id: String) {
        if (app.repo.meta(id) == null) return
        val useTabs = app.settings.tabs
        if (!useTabs && tabs.size > 0) dropTabs(keep = editor?.nbId)
        if (editor?.nbId == id) { if (useTabs) tabs = tabs.open(id); screen = Screen.EDITOR; return }
        // Karta z tego okna jest już „nasza” w openIds; obcy zeszyt (inne okno) odpada.
        if (!(tabs.has(id) || app.openIds.add(id))) {
            android.widget.Toast.makeText(app, T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window"), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (useTabs) {
            var t = tabs.retain { app.repo.meta(it) != null }
            editor?.nbId?.let { if (!t.has(it)) t = t.open(it) }
            val next = t.open(id)
            (t.ids - next.ids.toSet()).forEach { app.openIds.remove(it) }
            tabs = next
        }
        editor?.let { it.close(); if (!tabs.has(it.nbId)) app.openIds.remove(it.nbId) }
        app.repo.update(id) { it.copy(opened = System.currentTimeMillis()) }
        editor = Editor(app, id)
        screen = Screen.EDITOR
    }

    /** Zamyka edytor i wszystkie karty — powrót do biblioteki bez niczego w tle. */
    fun closeEditor() {
        editor?.let { app.openIds.remove(it.nbId) }
        editor?.close()
        editor = null
        dropTabs(keep = null)
        screen = Screen.LIBRARY
    }

    /** Wyjście z edytora do biblioteki: w trybie kart karty zostają (biblioteka jest centrum), inaczej pełne zamknięcie. */
    fun leave() {
        if (app.settings.tabs && tabs.size > 0) {
            editor?.close()
            editor = null
            screen = Screen.LIBRARY
        } else closeEditor()
    }

    /** Zamyka kartę [id]; jeśli była aktywna w edytorze, przechodzi do sąsiedniej albo do biblioteki. */
    fun closeTab(id: String) {
        if (!tabs.has(id)) return
        val wasActive = editor?.nbId == id
        tabs = tabs.close(id)
        app.openIds.remove(id)
        if (wasActive) {
            editor?.close()
            editor = null
            val next = tabs.activeId
            if (next != null) open(next) else screen = Screen.LIBRARY
        }
    }

    /** Następna / poprzednia karta (Ctrl+Tab). */
    fun stepTab(delta: Int) {
        val to = tabs.step(delta).activeId ?: return
        if (to != editor?.nbId) open(to)
    }

    fun openTabNumber(n: Int) {
        val to = tabs.activateNumber(n).activeId ?: return
        if (to != editor?.nbId) open(to)
    }

    /** Zwalnia wszystkie karty poza [keep] (ten zeszyt zostaje otwarty w edytorze). */
    private fun dropTabs(keep: String?) {
        tabs.ids.forEach { if (it != keep) app.openIds.remove(it) }
        tabs = Tabs()
    }

    /** Nowy zeszyt z domyślnymi ustawieniami (intencja CREATE_NOTE). */
    fun quickNote() {
        val s = app.settings
        val spec = PageSpec.of(PageFormat.fromId(s.newFormat) ?: PageFormat.A4, s.newLandscape, Template.fromCode(s.newTemplate), s.newPaper)
        val n = app.repo.createNotebook(T.t("Nowa notatka", "New note"), spec, CoverPalette.default, CoverPalette.DEFAULT_STYLE, null)
        open(n.id)
    }

    override fun onCleared() { samsung?.cancel(); editor?.let { app.openIds.remove(it.nbId) }; tabs.ids.forEach { app.openIds.remove(it) }; editor?.close() }
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

    // Plik otwarty przy zablokowanej aplikacji czeka na odblokowanie, zamiast po cichu przepaść.
    private var pending: Intent? = null

    private fun handle(i: Intent?) {
        if (i == null) return
        if (ui.locked) {
            if (i.action == Intent.ACTION_VIEW || i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE) pending = i
            return
        }
        i.getStringExtra(EXTRA_NOTEBOOK)?.let { i.removeExtra(EXTRA_NOTEBOOK); ui.open(it) }
        when (i.action) {
            Intent.ACTION_CREATE_NOTE -> ui.quickNote()
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                // Pliki z innych aplikacji („Otwórz w mNotes”, „Udostępnij”) stają się nowymi zeszytami.
                val uris = incomingUris(i)
                // Chwilowo (BACKLOG LIB-017): pokazuje typ MIME od aplikacji źródłowej, żeby zawęzić filtr „Udostępnij”.
                if (i.action != Intent.ACTION_VIEW) android.widget.Toast.makeText(this, T.t("Typ udostępnionego pliku: ${i.type}", "Shared file type: ${i.type}"), android.widget.Toast.LENGTH_LONG).show()
                if (uris.isEmpty()) return
                lifecycleScope.launch {
                    var handed = false
                    val made = importUris(app, uris, null, i.type, onSamsung = { handed = true; ui.startSamsung(it, null) })
                    if (made.size == 1 && !handed) ui.open(made[0].id)
                }
            }
        }
    }

    /** Adresy plików z intencji: VIEW (`data`), SEND (jeden strumień), SEND_MULTIPLE (lista); awaryjnie `ClipData`. */
    private fun incomingUris(i: Intent): List<android.net.Uri> {
        val out = LinkedHashSet<android.net.Uri>()
        i.data?.let { out.add(it) }
        val tiramisu = android.os.Build.VERSION.SDK_INT >= 33
        if (i.action == Intent.ACTION_SEND) {
            (if (tiramisu) i.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))?.let { out.add(it) }
        } else if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            (if (tiramisu) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM))?.let { out.addAll(it) }
        }
        // Część aplikacji podaje pliki tylko w ClipData (zwłaszcza z uprawnieniem do odczytu przypiętym do niej).
        i.clipData?.let { c -> for (k in 0 until c.itemCount) c.getItemAt(k).uri?.let { out.add(it) } }
        return out.toList()
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
            LockScreen(onUnlock = { ui.locked = false; pending?.let { p -> pending = null; handle(p) } }, prompt = ::prompt)
            return
        }
        val tabsCtl = TabsCtl(
            ui.tabs, app.settings.tabs, ui::open, ui::closeTab, ui::stepTab, ui::leave,
            { id -> app.repo.meta(id)?.title ?: "" },
        )
        CompositionLocalProvider(LocalSamsungStart provides ui::startSamsung) {
            Box(Modifier.fillMaxSize()) {
                when (ui.screen) {
                    Screen.LIBRARY -> LibraryScreen(ui.lib, onOpen = ui::open, tabs = tabsCtl.takeIf { it.on }, onSettings = { ui.settingsCat = 0; ui.screen = Screen.SETTINGS })
                    Screen.SETTINGS -> SettingsScreen(onBack = { ui.screen = if (ui.editor != null) Screen.EDITOR else Screen.LIBRARY }, startCat = ui.settingsCat)
                    Screen.EDITOR -> {
                        val ed = ui.editor
                        if (ed == null) { ui.screen = Screen.LIBRARY } else key(ed.nbId) {
                            CompositionLocalProvider(LocalBarSettings provides { ui.settingsCat = EDITOR_CAT; ui.screen = Screen.SETTINGS }) {
                                EditorScreen(ed, tabsCtl, onExit = ui::leave, onSettings = { ui.settingsCat = 0; ui.screen = Screen.SETTINGS }, onShortcuts = { ui.settingsCat = SHORTCUTS_CAT; ui.screen = Screen.SETTINGS }, onColorSets = { ui.settingsCat = COLORS_CAT; ui.screen = Screen.SETTINGS })
                            }
                        }
                    }
                }
                // Z ustawień wracamy do poprzedniego ekranu (biblioteki lub edytora).
                if (ui.screen == Screen.SETTINGS) BackHandler { ui.screen = if (ui.editor != null) Screen.EDITOR else Screen.LIBRARY }
                // Okno importu leży nad wszystkim (także nad edytorem, gdy plik przyszedł z innej aplikacji).
                ui.samsung?.let { s -> SamsungImportScreen(s, onClose = ui::closeSamsung, onFinished = { _, failed -> ui.samsungFinished(failed) }) }
            }
        }
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
