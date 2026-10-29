# Folder wymiany i tryb „do odczytu” (3c-A8, 3c-A9)

Notatki z tabletu oglądane na telefonie, bez sieci w mNotes (aplikacja nie ma uprawnienia INTERNET).

## Jak to działa

1. **Tablet zapisuje.** Ustawienia → „Folder synchronizacji” ([`SyncFolder`](../../app/src/main/java/com/mdev/mnotes/data/SyncFolder.kt)):
   lustro biblioteki jako pliki `.mnotes` w folderze SAF, po wyjściu z aplikacji albo ręcznie.
2. **Zewnętrzna aplikacja przenosi folder** (Syncthing, FolderSync, Autosync, Nextcloud…).
3. **Telefon wczytuje.** Ustawienia → „Folder wymiany: wczytuj z innego urządzenia”
   ([`ExchangeReader`](../../app/src/main/java/com/mdev/mnotes/data/ExchangeReader.kt)). Przy otwarciu aplikacji (i powrocie
   do niej) albo po „Wczytaj teraz” nowsze wersje trafiają do folderu biblioteki „Folder wymiany” (z podfolderami jak na
   tablecie) jako **kopie tylko do odczytu**.

### Zasady wczytywania ([`ExchangeIn`](../../app/src/main/java/com/mdev/mnotes/core/export/ExchangeIn.kt), czysta logika)

- Zeszyt rozpoznajemy po identyfikatorze z `meta.json` archiwum, nie po nazwie pliku. Po zmianie tytułu na tablecie stary
  plik zostaje w folderze (SyncFolder niczego nie kasuje), a nowszy plik tego samego zeszytu podmienia tę samą kopię.
- Podmiana zachowuje identyfikator kopii (karty, przypięte profile, statystyki), jej folder, ulubione i miejsce czytania.
- Kopia otwarta w edytorze czeka do następnego przebiegu („czeka (otwarte)” w raporcie).
- Usunięta kopia wraca dopiero z nowszą wersją pliku.
- Plik, który się nie rozpakował, nie jest pobierany ponownie, dopóki się nie zmieni.
- Własne zeszyty urządzenia (folder wymiany wskazujący własne lustro) są pomijane; kopie nie wracają do lustra
  (`SyncFolder.skip`).
- Stan: `files/exchange_in.json` (`seen`: ścieżka → czas, rozmiar, źródło; `copies`: źródło → kopia, czas).
- „Odłącz” zostawia kopie jako zwykłe zeszyty (bez blokady).

### Dysk Google

Aplikacja Dysku Google często nie jest dostawcą **drzewa** folderów w SAF (nie da się wybrać folderu albo wybrany folder
nie daje się przejrzeć). Ustawienia pokazują wtedy komunikat i wskazówkę: folder w pamięci urządzenia + aplikacja
synchronizująca. To samo jest w przewodniku (lekcja „Telefon: folder wymiany i tryb do odczytu”).

## Tryb „do odczytu” ([`Reader`](../../app/src/main/java/com/mdev/mnotes/core/prefs/Reader.kt))

- Palec i rysik tylko przewijają i powiększają (także przy „Tylko rysik” i przy laserze/wycinku), paski schowane, strona
  dopasowana do szerokości. Kapsuła w rogu: powrót do biblioteki, numer strony, „Edytuj”.
- Włącza się: ustawieniem „Otwieraj zeszyty w trybie do odczytu”, profilem **„Do odczytu”** (klucz `view.reader`), sam na
  telefonie (`smallestScreenWidthDp < 600`, ustawienie `view.reader_auto`, domyślnie wł.) i zawsze dla kopii z folderu.
- „Edytuj” wychodzi z trybu; kopia z folderu zostaje zablokowana (komunikat: zmiany rób na tablecie).
- Wstecz w trybie wraca do biblioteki. Polecenie „Tryb do odczytu” jest też w palecie, menu „Widok” i w „⋮”.
- Profil „Do odczytu” dopisuje się raz do istniejącej listy profili (`profile.builtins_seen`); usunięty nie wraca.

## Testy

- `ExchangeInTest` — nowe/zmienione pliki, podmiana, zmiana nazwy na tablecie, otwarta kopia, usunięta kopia, własne
  zeszyty, uszkodzone pliki, ścieżki.
- `ReaderTest` — kiedy tryb się włącza, stan edytora, profil „Do odczytu”, dopisanie profilu do starej listy, klucze w Settings.

## Do sprawdzenia na urządzeniach

- Telefon: wybór folderu lokalnego zsynchronizowanego np. Syncthingiem; pierwsze wczytanie, potem zmiana na tablecie i
  powrót do aplikacji na telefonie (podmiana, miejsce czytania).
- Dysk Google w systemowym wyborze folderu: czy widać komunikat (anulowanie / folder bez listy).
- Tryb do odczytu: przewijanie jednym palcem, szczypanie, rysik nie pisze; „Edytuj”; wstecz.
- Podmiana dokumentu PDF z folderu, gdy jego strony są w pamięci podręcznej (`PdfPool`) — niesprawdzone.
