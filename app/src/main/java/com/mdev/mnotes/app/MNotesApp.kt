package com.mdev.mnotes.app

import android.app.Application
import android.content.Context
import com.mdev.mnotes.data.Repository
import com.mdev.mnotes.data.SearchIndex
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.data.SyncFolder
import com.mdev.mnotes.render.Assets
import com.mdev.mnotes.render.PdfPool
import com.mdev.mnotes.render.Thumbs

/** Singletony aplikacji: repozytorium, ustawienia i wspólne cache grafik (bez DI — wystarczy prosty kontener). */
class MNotesApp : Application() {
    lateinit var repo: Repository
    lateinit var settings: Settings
    lateinit var assets: Assets
    lateinit var pdf: PdfPool
    lateinit var thumbs: Thumbs
    lateinit var search: SearchIndex
    lateinit var sync: SyncFolder
    /** Zapytanie z biblioteki, które edytor ma od razu wpisać w pasek szukania (jednorazowo). */
    @Volatile var pendingSearch: String? = null
    /** Zeszyty otwarte teraz w którymkolwiek oknie — jeden zeszyt nie może być edytowany w dwóch oknach naraz. */
    val openIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    /** Stanowisko do odtworzenia, gdy otworzy się jego pierwszy zeszyt (przełączanie stanowisk, 2b-A7). */
    @Volatile var pendingStation: com.mdev.mnotes.core.layout.Workbench.Station? = null

    override fun onCreate() {
        super.onCreate()
        // Pole `assets` niżej to cache grafik; czcionki biorą AssetManager z zasobów.
        com.mdev.mnotes.render.AppFonts.init(resources.assets)
        repo = Repository(this)
        settings = Settings(this)
        runCatching { settings.addNewBuiltInProfiles() }
        assets = Assets(repo)
        pdf = PdfPool(repo)
        thumbs = Thumbs(repo, assets, pdf)
        search = SearchIndex(repo)
        sync = SyncFolder(this, repo, settings, assets, pdf)
        // F20: przy pierwszym uruchomieniu (pusta biblioteka) powstaje zeszyt-przewodnik.
        repo.load { com.mdev.mnotes.data.GuideMaker.offerOnFirstRun(repo, settings) }
    }
}

val Context.app: MNotesApp get() = applicationContext as MNotesApp
