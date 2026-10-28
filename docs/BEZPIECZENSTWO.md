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
| S-05 | średnie | `core/nearby/LinkMachine.kt`, `SyncLink.kt` (`ChunkJoiner`) | Tablet ogłasza nazwę, którą słychać w eterze; obce urządzenie łączy się pod nią i przed `hello` (sprawdzeniem klucza pary) wysyła porcje do 24 MB, które tablet skleja w pamięci — zacięcia i brak pamięci, powtarzalnie. | naprawione `b12ce5d` (16 KB przed uwierzytelnieniem; test `LinkMachineTest.strangerCannotBuffer…`) |
| S-06 | średnie | `core/goodnotes/PdfDocument.kt` (strumień xref) | PDF kilku KB z `/W [0 0 0]` i `/Index [0 2147483647 …]` udostępniony do mNotes: pętla 2³¹ obrotów na parę, wątek importu zajęty na minuty–godziny, każde kolejne udostępnienie dokłada wątek. | naprawione `36a6806` (test `PdfHostileTest`) |
| S-07 | średnie | `core/goodnotes/` (`PathMath.cubic`, `PageConv`, predyktor, ASCII85) | Kilkadziesiąt KB treści PDF z GoodNotes: krzywe o współrzędnych 1e9 dzielą się do 2¹⁶ punktów każda (test: 122 mln punktów z 2000 krzywych), wiersz predyktora 64 MB z 1 bajtu, tysiące obrysów porównywanych każdy z każdym. | naprawione `18fd71a`, `4d0cb6b` (budżet 8 mln współrzędnych na stronę, ≤ 2000 obrysów, wiersz ≤ dane; test `PdfHostileTest`) |
| S-08 | średnie | `data/Repository.kt` (`importAsset`), `data/Transfer.kt`, `core/codec/MnotesArchive.kt` | Aplikacja udostępnia mNotes „PDF” ze swojego dostawcy, którego strumień nie ma końca: kopiowanie bez limitu zapełnia dysk, a katalog bez `meta.json` zostaje na zawsze; ZIP z milionem pustych wpisów to milion plików; `"lock": true` w obcym `.mnotes` po cichu wyłącza zeszyt z wyszukiwania i folderu wymiany. | naprawione `ce45cec` (limit 2 GB na plik, sprzątanie po błędzie, ≤ 100 000 wpisów, `lock` zerowany; test `HostileInputTest`) |
| S-09 | niskie | `app/MainActivity.kt` (`incomingUris`) | Inna aplikacja wysyła „Otwórz/Udostępnij” z `file:///data/user/0/com.mdev.mnotes/…` albo `content://com.mdev.mnotes.files/…`: mNotes swoimi prawami importuje własny prywatny plik (np. stary eksport z `cache/share`), a przy włączonym folderze wymiany kopia ląduje w folderze wspólnym. | naprawione `dc614b1` (`IncomingUris`; test `HostileInputTest`) |
| S-10 | niskie | `app/MainActivity.kt` (`EXTRA_NOTEBOOK`) | Wyeksportowana aktywność główna przyjmowała identyfikator zeszytu od każdej aplikacji (otwarcie bez wiedzy użytkownika; identyfikator trudny do zgadnięcia). Przy okazji: przy blokadzie nowe okno gubiło zeszyt. | naprawione `dc614b1` (tylko własne okno; czeka na odblokowanie) |
| S-11 | średnie | `app/MainActivity.kt` | Przy włączonej blokadzie aplikacji podgląd w „ostatnich” pokazywał otwartą stronę (zrzut przed `onStop`): kto weźmie odblokowany telefon, czyta notatkę bez odblokowania mNotes. | naprawione `9cc9f46` na Androidzie 13+; starsze — do decyzji (tylko `FLAG_SECURE`) |
| S-02 | niskie | manifest wersji debug (`PreviewActivity` z narzędzi Compose) | Wyeksportowana aktywność podglądu Compose w wersji debug: inna aplikacja może ją uruchomić z nazwą funkcji `@Composable`, która wykona się w procesie mNotes poza blokadą. | naprawione `9cc9f46` (`app/src/debug/AndroidManifest.xml`, `tools:node="remove"`) |
| S-12 | niskie | `core/nearby/LinkJournal.kt` (`LinkScrub`), „Kopiuj raport” w oknie łącza | Raport diagnostyki łącza, który użytkownik wkleja komuś do pomocy, zawierał nazwy urządzeń i adresy MAC; czyszczenie dziennika przepuszczało ścieżki ze spacją, MAC zamaskowany przez Androida, adresy IP i nazwy Wi-Fi Direct. | naprawione `1917384` (test `LinkJournalTest`, `LinkMachineTest.copiedReport…`) |
| S-13 | niskie | `core/nearby/SyncLink.kt` (`SyncMsg.decode`), `LinkPhoto.kt` (`PhotoReceiver`) | Połączone urządzenie: 9 bajtów deklarujących 24 MB wymuszało przydział 24 MB na wiadomość; duża pierwsza część zdjęcia i ostatnia o wysokim numerze dawały zapis pod przesunięciem ~2 GB (rzadki plik w cache tabletu). | naprawione `b773338` (test `LinkPhotoTest`) |
| S-14 | niskie | `gms/.../NearbyTransport.kt`, `core/nearby/Rfcomm.kt` | Łącze Nearby używa tylko ładunków BYTES, a ładunek plik/strumień od połączonego urządzenia (jeszcze przed `hello`) był przyjmowany przez Nearby bez limitu; adres pary zapisywany z ostatniego gniazda przychodzącego, także odrzuconego (wąskie okno przed zamknięciem nasłuchu). | naprawione `c8db69f` (ładunki inne niż BYTES anulowane; adres z przyjętego połączenia; bez testu JVM dla Nearby) |
| S-15 | niskie | `core/goodnotes/PdfDocument.kt` (`obj`), `core/codec/ByteIO.kt` (`ByteReader.need`) | PDF z łańcuchem 50 000 odwołań `/Length` przepełniał stos (`StackOverflowError` omija `catch (Exception)`); ujemna długość w kodeku cofała odczyt. | naprawione `92732ec` (testy `PdfHostileTest`, `HostileInputTest`) |
| S-16 | niskie | `core/model/PageSpec.kt` (`fromJson`) | Strona z `"w": "NaN"` we wrogim `.mnotes`: `coerceIn` przepuszcza NaN, bitmapa 0×0 i wyjątek przy każdym rysowaniu strony. | naprawione `b28c8c1` (test pilnuje wyniku; na JVM `org.json` czyta „NaN” inaczej niż Android) |
| S-17 | informacyjne | `editor/Editor.kt` (schowek) | Przy blokadzie aplikacji Android 13+ pokazywał skopiowany kod TikZ/obraz w podglądzie schowka; obrazy ze schowka zostawały w `cache/share` do następnego eksportu. | naprawione `a0bab67`, `8f171d2` (`EXTRA_IS_SENSITIVE`, PNG starsze niż doba usuwane) |

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
- Łącze: ECDH P-256 z kodem 6 cyfr (zobowiązanie przed odsłonięciem), AES-256-GCM z kluczem pary, klucz pary w
  magazynie kluczy urządzenia; obce urządzenie bez klucza nie przejdzie `hello`; rozmiar przed uwierzytelnieniem ograniczony (S-05).
- Kod: brak WebView, `ObjectInputStream`, `PendingIntent`, `Log.*`; `Class.forName` tylko ze stałą; brak wyrażeń
  regularnych z danych użytkownika; `SecureRandom` w łączu; archiwum `.mnotes` chronione przed wyjściem poza katalog (zip slip).

## Stan

- 29.10: przegląd skończony (manifesty, pliki z zewnątrz, łącze, dane w spoczynku, kod, kreda). Naprawione S-02…S-17;
  testy foss i gms bez porażek.
- Świadomie bez zmian (ryzyko przyjęte): sparowane urządzenie może wysłać zeszyt w oknie zdjęć (jest uwierzytelnione);
  stałe identyfikatory usługi łącza; odbiornik Bluetooth `RECEIVER_EXPORTED` dla chronionego `ACTION_FOUND`; kopiowanie
  przy wklejaniu/przeciąganiu na wątku zapisu (zaczyna je użytkownik).
- Do decyzji (odbierają coś użytkownikowi albo wymagają klucza wydania): wersja debug (S-01); `FLAG_SECURE` dla Androida
  < 13; `dataExtractionRules` (przenoszenie danych na nowy telefon kopiuje zeszyty i dane pary); blokada pojedynczych
  zeszytów (pół-funkcja); EXIF/GPS w zdjęciach wysyłanych z telefonu; blokada wyłączana, gdy urządzenie nie ma blokady
  ekranu; `BIOMETRIC_WEAK`; folder wymiany nadpisuje plik o tej samej nazwie, którego mNotes nie zapisało.
