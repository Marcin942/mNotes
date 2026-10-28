# Bezpieczeństwo mNotes — audyt

## Model zagrożeń

mNotes działa w pełni offline (bez uprawnienia INTERNET), więc główne drogi ataku to: **inna aplikacja na tym samym
urządzeniu** (intencje do wyeksportowanej aktywności, podsunięte adresy `content://`/`file://`, pliki w folderach
wspólnych), **wrogi plik** otwarty lub udostępniony przez użytkownika (zeszyt `.mnotes`, Samsung Notes `.sdocx`, PDF,
obraz), **obce urządzenie w pobliżu** przy łączu telefon–tablet (Nearby, Bluetooth) oraz **osoba z fizycznym dostępem**
do odblokowanego lub podłączonego urządzenia (zablokowane zeszyty, podgląd w ostatnich aplikacjach, debugowanie USB).
Cele atakującego: odczyt notatek, podrzucenie treści, awaria aplikacji albo utrata zeszytu, wyczerpanie pamięci.

## Znaleziska

| ID | Waga | Gdzie | Scenariusz | Stan |
|----|------|-------|------------|------|
| S-01 | średnie | paczka instalacyjna (wersja debug) | Instalowany APK jest w wersji debug (`debuggable=true`): z włączonym debugowaniem USB `run-as` daje pełny odczyt danych aplikacji, także zeszytów zablokowanych biometrią. | do decyzji (wymaga klucza wydania, D13/D14) |
| S-03 | średnie | `core/codec/PageCodec.kt` (`inflate`) | Strona we wrogim zeszycie (`.mnotes`) ma ~20 bajtów skompresowanych i deklaruje 256 MB po rozpakowaniu; dekoder przydzielał bufor z góry → brak pamięci przy otwieraniu. | naprawione `ebe9629` (limit z ilorazu deflate; test `HostileInputTest`) |
| S-04 | średnie | `data/Repository.kt` (`pageFile`, `assetFile`), `data/StickerStore.kt` | Importowany zeszyt z identyfikatorem strony albo nazwą obrazu typu `../../../shared_prefs/…`: edycja strony zapisywała plik `.mnp` poza katalogiem zeszytu, obraz czytał dowolny plik aplikacji (np. klucze pary łącza), a zapis takiego elementu jako naklejki kopiował plik na samego siebie, czyli usuwał go (np. indeks biblioteki). | naprawione `a8b1f5d` (`LocalName`; test `HostileInputTest`) |
| S-02 | niskie | manifest wersji debug (`PreviewActivity` z narzędzi Compose) | Wyeksportowana aktywność podglądu Compose w wersji debug: inna aplikacja może ją uruchomić z nazwą funkcji `@Composable`. | w analizie |

## Sprawdzone bez problemów

- `allowBackup="false"` w manifeście głównym; INTERNET usuwany także z bibliotek Usług Google (`tools:node="remove"`).
- `FileProvider` (`${applicationId}.files`): `exported=false`, zakres tylko `cache/share/`.
- `NoteWindowActivity`: `exported=false`.
- Komponenty bibliotek w scalonym manifeście: `WakeUpService` (Usługi Google) chroniony uprawnieniem podpisu Usług
  Google, `ProfileInstallReceiver` chroniony `android.permission.DUMP`, `InitializationProvider` niewyeksportowany.
- Kreda: dekodowanie zdjęć najpierw czyta wymiary, potem próbkuje (`DecodePlan`), więc dłuższy bok ograniczony niezależnie
  od wymiarów z nagłówka; wyjątki i brak pamięci łapane (`runCatching`). „Wyślij do mLabs” udostępnia tylko PNG strony
  i JSON (tytuł, numer strony, data, tekst wpisany ręcznie) przez systemowy wybór aplikacji — bez wskazanego pakietu;
  nazwa pliku z tytułu zeszytu przechodzi przez `fileStem` (tylko `a-z0-9-`), więc tytuł nie wyjdzie poza katalog.

## Stan

- 29.10: szkielet raportu; manifesty obu wariantów i kreda przejrzane; przegląd plików z zewnątrz, łącza i danych
  w spoczynku skończony. Naprawione: S-03, S-04, S-05.
- Kolejka (do weryfikacji i poprawki, od najpoważniejszych): PDF `/W [0 0 0]` w strumieniu xref (pętla ~2³¹);
  kopiowanie strumienia bez limitu + pozostałości katalogu po nieudanym imporcie; `file://`/własny FileProvider
  przyjmowane jako import; podgląd w ostatnich aplikacjach przy blokadzie (API 33+); `lock` z importowanego pliku;
  `EXTRA_NOTEBOOK` tylko dla okna wewnętrznego; `PreviewActivity` usunąć z manifestu debug; łącze: czyszczenie raportu
  diagnostyki i luki `LinkScrub`, ładunki FILE/STREAM Nearby, `lastPeer`, długości w `SyncMsg`/`LinkPhoto`;
  PDF: predyktor/ASCII85/krzywe bez limitu, głębokość `obj()`; `ByteReader.need` przepełnienie; NaN w rozmiarze strony;
  limit liczby wpisów `.mnotes`; folder wymiany nadpisuje cudzy plik o tej samej nazwie; schowek bez „poufne”.
- Do decyzji: wersja debug (S-01), FLAG_SECURE <API 33, `dataExtractionRules` (przenoszenie na nowy telefon),
  blokada pojedynczych zeszytów (pół-funkcja), EXIF/GPS w zdjęciach z telefonu, wyłączanie blokady bez blokady ekranu.
