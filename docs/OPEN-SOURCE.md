# mNotes jako open source: analiza, plan i decyzje (FAZA 3a)

Stan na 28.10.2026, gałąź `noc/wszystko` @ `d2917b1` (agent A, Opus). **Nic nie zostało opublikowane ani wypchnięte,
`main` jest nietknięty.** To nie jest porada prawna: tam, gdzie chodzi o prawo autorskie, opisuję ryzyko i bezpieczną
drogę, a decyzja należy do Marcina.

## 0. W minutę

- Technicznie podział jest prosty: kod kredy siedzi już osobno w `app/src/lab/`, a wspólny kod widzi go tylko przez
  interfejs `LabFeatures`. Łącze z telefonem (Nearby) ma czystą logikę w `core/nearby/` i transport za interfejsem
  `LinkTransport`, więc da się je przenieść do `main` bez kredy.
- **Blokery są prawne, nie techniczne:**
  1. **Kreda** to port algorytmów z `materialy/kreda/kreda.py` Piotrka (bez pliku licencji, README bez autora i licencji).
     Gałąź `kreda` może być publiczna dopiero po jego **pisemnej zgodzie i wyborze licencji**.
  2. **Łącze Nearby** powstało na wzór kodu z innego projektu Marcina (`SyncCrypto.kt`, `SyncLink.kt`,
     `NearbyTransport.kt`). **Rozstrzygnięte (D8, 28.10):** kod jest w całości Marcina, zgoda na użycie jest; kopia
     wzoru usunięta z drzewa (2e-B2).
  3. **`play-services-nearby` jest niewolna**: F-Droid jej nie przyjmie, a przy GPL potrzebny jest wyjątek. Rekomendacja:
     dwa warianty, `foss` (bez Nearby) i `gms` (z Nearby).
- **Licencjat: czysto.** Porównanie całego kodu mNotes z `materialy/licencjat/` (118 plików py/js/html/css) nie
  znalazło skopiowanego kodu; wspólne są tylko polskie nazwy konstrukcji geometrycznych i jeden wzór szkolny (§3.3).
- **Historia: czysty start jest warunkiem publikacji** (jeden commit, §4), bo w historii jest kod kredy, wzmianki
  o projekcie zewnętrznym wskazanym przez Marcina (do usunięcia, lista poza repozytorium), ścieżki
  `/home/piotr/…`, dziennik nocnej pracy agentów i 55 gałęzi roboczych (§4).
- Lista decyzji z rekomendacjami: §6.2. Stan przygotowań (gałęzie `oss/main`, `oss/kreda`): § „Stan” na końcu.

## 1. Podział kodu

### 1.1 Dziś

Jeden moduł `:app`, wymiar wariantów `wariant` z dwoma wariantami (`app/build.gradle.kts`):

| Zbiór źródeł | Zawartość | Wiersze |
|---|---|---|
| `src/main/` | cała aplikacja; `core/nearby/` (LinkMachine 366, LinkPhoto 167, SyncCrypto 146, SyncLink 326) | — |
| `src/core/` | `app/Flavor.kt`: `lab = null` | 1 plik |
| `src/lab/` | `lab/Lab.kt` 575 (jedna implementacja `LabFeatures`: kreda **i** sekcja Nearby w ustawieniach **i** polecenie „Wyślij do mLabs”), `lab/LabSettings.kt` 92 (ustawienia kredy i przełącznik Nearby w jednym pliku preferencji `mnotes.lab`) | 667 |
| | `lab/nearby/`: NearbyUi 387, NearbyTransport 94, LinkDiagnostics 74, PairStore 60 | 615 |
| | `lab/recognize/`: Recognizer 102 (dziś tylko `NoRecognizer`), SendToMlabs 125 | 227 |
| | `core/image/`: Chalk 459, ChalkVector 431, BoardGeometry 347, ChalkCurves 256, ChalkStrokes 161, BoardFilters 157, Raster 75, DecodePlan 25 | 1 911 |
| | `AndroidManifest.xml`: uprawnienia Bluetooth/Wi‑Fi/lokalizacji, usunięcie INTERNET | — |
| `src/testLab/` | 8 testów kredy (`core/image/*`) i `RecognizerTest` | 9 plików |
| `src/test/` | m.in. `core/nearby/NearbyTest.kt` (logika łącza testowana w obu wariantach) | — |

Jedyna zależność tylko dla lab: `labImplementation(play-services-nearby 19.3.0)`.

Haki w kodzie wspólnym (`Flavor.lab?.…`): `EditorScreen.kt` (panel „Zdjęcie z telefonu”, przycisk w pasku,
przycisk „Oczyść tablicę” w menu zaznaczonych zdjęć, `Overlay`), `EditorDialogs.kt` (polecenia w palecie),
`SettingsScreen.kt` (kategoria „Laboratorium”, wyszukiwanie), `Editor.kt` (`imageInserted`, `onLecturePhoto`),
`GuideMaker.kt` → `Guide.pages(pl, lab)` (dwa zdania o kredzie w przewodniku). Kod wspólny **nie zawiera algorytmów
kredy**: są tylko nazwy haków, ikona `BoardCameraGlyph` (rysowana w kodzie, nasza) i teksty przewodnika.

Zależności między częściami lab (sprawdzone importami): `lab/nearby/*` **nie** importuje niczego z kredy;
`Recognizer` używa tylko `Raster`; całą resztę kredy spina `Lab.kt`.

### 1.2 Docelowo

| Część | `main` | gałąź `kreda` |
|---|---|---|
| Wszystko z `src/main/` (konstrukcje, Geometria, grecki/indeksy, TikZ, statystyki…) | ✔ | ✔ |
| Łącze telefon–tablet: zdjęcie z telefonu, wysyłanie zeszytu, diagnostyka (`lab/nearby/*` + sekcja ustawień z `Lab.kt`) | ✔ w wariancie `gms` | ✔ |
| Kreda: `core/image/*`, panel „Oczyść tablicę”, kreda w trybie wykładu, podpowiedź po wstawieniu zdjęcia, ustawienia `board.*` | ✘ | ✔ |
| „Wyślij do mLabs” + `Recognizer` | ✘ (rekomendacja, decyzja D6) | ✔ |
| Testy kredy (`testLab/core/image/*`), `RecognizerTest` | ✘ | ✔ |
| `docs/KREDA-POMIARY.md` (po usunięciu ścieżek domowych) | ✘ | ✔ |

„Wyślij do mLabs” nie jest kredą, ale bez publicznego mLabs to przycisk, który udostępnia PNG i JSON aplikacji, której
użytkownik nie ma; dlatego rekomenduję trzymać go przy kredzie, dopóki mLabs nie będzie publiczny (D6).

### 1.3 Jak uniknąć rozjeżdżania się gałęzi

Zasada: **gałąź `kreda` dodaje wyłącznie nowe pliki w dwóch katalogach i nie zmienia ani jednego pliku z `main`.**
Wtedy `git merge main` (albo rebase) na `kreda` nie może dać konfliktu; najwyżej błąd kompilacji, gdy zmieni się
interfejs, a ten złapie CI.

Proponowany układ (wdrożony na `oss/main`/`oss/kreda`, patrz „Stan”):

```
app/src/main/…/app/LabFeatures.kt   interfejs rozszerzeń (bez zmian w metodach; wszystkie mają domyślne „nic”)
app/src/main/…/app/Extensions.kt    lista rozszerzeń: łącze z wariantu + kreda, jeśli jest w kompilacji
app/src/foss/…/app/Flavor.kt        wariant bez Usług Google: link = null
app/src/gms/…                       łącze Nearby (dzisiejsze lab/nearby/* i sekcja ustawień), manifest z uprawnieniami
app/src/kreda/java/…                TYLKO w gałęzi kreda: core/image/*, kreda (implementacja LabFeatures), recognize/*
app/src/testKreda/java/…            TYLKO w gałęzi kreda: testy kredy
```

`app/build.gradle.kts` (ten sam plik w obu gałęziach) dołącza `src/kreda/java` i `src/testKreda/java`, gdy katalog
istnieje, a `Extensions` ładuje obiekt kredy przez `Class.forName(…)`, gdy jest na ścieżce klas. Minifikacja jest dziś
wyłączona (`optimization.enable = false`); gdyby ją włączyć, trzeba dodać regułę `-keep` dla tej jednej klasy.

Odrzucone warianty:
- **Osobny moduł Gradle `:kreda`**: kreda używa `Editor`, `MDialog`, `mint`, `T`… z `:app`, a moduł aplikacji nie może
  być zależnością biblioteki. Trzeba by wydzielić `:ui-core` jako bibliotekę: kilka dni przebudowy i duże ryzyko.
- **Warianty `core`/`lab` w obu gałęziach**: w `main` wariant „lab” miałby tylko Nearby, a w `kreda` dwie rzeczy naraz;
  mylące i dalej trzeba by zmieniać `Lab.kt` w obu gałęziach (konflikty).

Pliki preferencji i klucze zostają te same (`mnotes.lab`, `nearby.enabled`, `board.*`, magazyn pary w `PairStore`), więc
po przejściu z dzisiejszego wariantu lab na `gms`/`kreda` tablet nie traci parowania ani ustawień kredy.

## 2. Zależności a open source

| Zależność | Licencja | Uwagi |
|---|---|---|
| AndroidX (core-ktx, activity-compose, fragment, biometric, lifecycle, exifinterface, input-motionprediction, profileinstaller), Compose BOM, material3, material-icons-extended | Apache-2.0 | zgodne z GPL-3, Apache-2, MIT |
| Kotlin, kotlinx-coroutines | Apache-2.0 | j.w. |
| junit 4.13.2 (tylko testy) | EPL-1.0 | nie trafia do APK |
| org.json 20240303 (tylko testy) | domena publiczna (od wersji 20231013) | nie trafia do APK |
| Gradle wrapper (`gradle-wrapper.jar`) | Apache-2.0 | standardowy plik Gradle |
| **play-services-nearby 19.3.0** | licencja Google (niewolna) | tylko wariant z Nearby |

Opcje dla Nearby:

| Opcja | Koszt | Ryzyko | Uwagi |
|---|---|---|---|
| A. Nearby we wszystkich wydaniach | 0 | F-Droid odrzuci; przy GPL-3 niezgodność z niewolną biblioteką (trzeba wyjątku §7) | najprościej, ale wyklucza „czysty” FOSS |
| **B. Warianty `foss` (bez Nearby) i `gms` (z Nearby)** | ~0,5 dnia (warianty już są; wchodzi w podział z §1.3) | niskie | F-Droid buduje `foss`; GitHub Releases daje oba APK |
| C. Własny transport za `LinkTransport` | patrz niżej | średnie–wysokie, wymaga dwóch urządzeń do testów | da łącze także w `foss` |

Do C: `LinkTransport` to 8 metod (`advertise`, `discover`, `request`, `accept`, `send`…), a cała reszta (parowanie
ECDH + 6 cyfr, szyfrowanie ramek, porcjowanie zdjęć, maszyna stanów) jest już w `core/nearby/` z testami, więc nowy
transport to jedna klasa i UI wyboru. Ważne ograniczenie: **każde gniazdo TCP/UDP na Androidzie wymaga uprawnienia
`INTERNET`** (także w sieci lokalnej i przy Wi‑Fi Direct), a mNotes obiecuje, że go nie ma. Nearby to omija, bo radiem
steruje proces Usług Google.

| Transport | Koszt | Bez INTERNET? | Uwagi |
|---|---|---|---|
| Sieć lokalna (NSD/mDNS + TCP) | 2–3 dni | ✘ | wymaga wspólnego Wi‑Fi; łamie obietnicę „bez INTERNET” |
| Wi‑Fi Direct (`WifiP2pManager` + gniazdo) | 4–6 dni | ✘ | kapryśne parowanie grup, różnice producentów |
| **Bluetooth Classic RFCOMM** (`BluetoothSocket`) | 3–5 dni | ✔ | wolny (realnie ~1–2 Mb/s): zdjęcie pomniejszone do ~400 KB w 2–4 s; wystarczy do zdjęć, zeszyty dłużej |
| BLE L2CAP CoC (API 29+) | 4–6 dni | ✔ | szybszy od RFCOMM na części urządzeń, mniej przykładów |

Parowanie kodem QR nie jest potrzebne: dzisiejsze ECDH + porównanie 6 cyfr działa przez każdy transport. QR wymagałby
własnego kodera/dekodera (~500–800 wierszy) albo nowej zależności (np. ZXing, Apache-2.0).

**Rekomendacja:** teraz B (`foss` + `gms`). Po publikacji, jeśli łącze w `foss` będzie potrzebne: transport Bluetooth
RFCOMM za `LinkTransport` (zachowuje „bez INTERNET”), testowany na dwóch urządzeniach.

**Werdykt w sprawie transportu wydaje osobna analiza** `plan/NEARBY-OPEN-SOURCE.md` (sesja `noc-nearby`, dopisek planu
z 28.10, 12:00); w chwili pisania (12:10) pliku jeszcze nie ma. Ta sekcja to tylko tło. **Transportu nie zmieniamy, dopóki
Marcin nie odpowie na pytania z tamtej analizy**; podział `foss`/`gms` z 3a-2 transportu nie rusza, tylko odcina Nearby od
wariantu `foss`.

## 3. Prawa i licencje

### 3.1 Licencja projektu (decyzja Marcina, D1)

Materiał: `plan/LICENCJA-NOTATKA.md`. Dziś brak pliku LICENSE oznacza „wszelkie prawa zastrzeżone”. Co dochodzi z tej
analizy:

- **GPL-3.0-or-later**: chroni przed zamkniętymi forkami i pasuje do F-Droid. Wariant `gms` łączy się z niewolną
  biblioteką, więc do LICENSE trzeba dopisać **dodatkowe pozwolenie z §7** („wolno łączyć z Google Play Services”);
  Marcin może je dać jako autor. Kreda Piotrka musiałaby wtedy być na licencji zgodnej z GPL-3 (jego decyzja).
- **Apache-2.0**: bez wyjątków, zgodna z każdą zależnością i z ewentualną zamkniętą wersją „lab” w przyszłości;
  standard w ekosystemie Androida.
- **MIT**: najprostsza, bez klauzuli patentowej.
- **Kod pisany z pomocą AI.** Prawie cały kod powstał z udziałem agentów (Claude). Ochrona prawnoautorska wymaga
  twórczego wkładu człowieka; przy kodzie wygenerowanym w dużej części przez AI jej zakres jest niepewny. Licencja i tak
  działa dla części chronionych, ale siła copyleftu (GPL) opiera się na prawie autorskim; to drobny argument za licencją
  liberalną. Uczciwie jest napisać o udziale AI w README (D4).

**Rekomendacja:** GPL-3.0-or-later z wyjątkiem dla Google Play Services, jeśli celem jest „nikt tego nie zamknie”;
Apache-2.0, jeśli Marcin chce prostoty albo zostawia sobie zamkniętą wersję. Do decyzji: plik `LICENSE-DO-DECYZJI.md`.

Wkład z zewnątrz: zamiast umowy CLA lekki **DCO** (`Signed-off-by` w commitach), opisany w CONTRIBUTING.

### 3.2 Zależności

Wszystkie wolne zależności są Apache-2.0 (zgodne z każdą z trzech licencji); testowe (junit, org.json) nie trafiają do
APK. Jedyny problem to `play-services-nearby` (§2). Do NOTICE: lista zależności z licencjami (szkic w „Stan”).

### 3.3 Praca licencjacka: sprawdzenie „nic nie skopiowane”

Zasada nocy: pomysły z licencjatu („Route Editor”, Django/JS, bez licencji) wolno było przepisać od zera, kodu nie wolno
kopiować. Sprawdzenie skryptem `tools/podobienstwo.py` (28.10 ok. 12:00; powtórka:
`python3 -I tools/podobienstwo.py ../../materialy/licencjat`):

- wspólne linie (≥ 30 znaków po normalizacji): **1**, `from PIL import Image, ImageDraw, ImageFont` w skrypcie
  `skills/zadania-do-mnotes/scripts/analyze.py` (zwykły import biblioteki);
- wspólne literały (≥ 14 znaków): **7**, same polskie nazwy pojęć: „Punkt przecięcia prostych”, „Przecięcie prostej
  i okręgu”, „Przecięcie prostych”, „Odbicie względem prostej”, „Krzywa Béziera”, „application/json” (pojęcia, nie
  utwór);
- wspólne ciągi 8 identyfikatorów: 3 pliki, wszędzie ten sam szkolny wyznacznik pola trójkąta
  `ax(by−cy) + bx(cy−ay) + cx(ay−by)` (`Construct.kt`, `TikzExportTest.kt`, `PageCodecTest.kt`).

Wniosek: **nie znaleziono skopiowanego kodu.** Eksport TikZ (FB13, 28.10) agent A napisał bez otwierania eksportera
z licencjatu (`views.py`; wie o nim tylko z `plan/ANALIZA-LICENCJAT.md`). Ograniczenie metody: skrypt porównuje tokeny,
więc tłumaczenie z Pythona na Kotlina linia po linii mogłoby mu umknąć; zasadą nocy było pisanie od zera z własnymi
testami, a kod mNotes ma inną budowę (Kotlin, model strony mNotes, inne API).

Podziękowanie: w README/NOTICE „Dziękuję autorce pracy licencjackiej, z której pochodzą pomysły konstrukcji
geometrycznych…” **bez nazwiska**, dopóki nie zgodzi się na jego podanie (D9).

### 3.4 Kreda (Piotrek)

- Kod `src/lab/core/image/*` (≈ 1 900 wierszy) i testy to port algorytmów z `materialy/kreda/kreda.py` (kolor do alfy,
  prostowanie tablicy, wyrównanie oświetlenia, mediana zdjęć, gumka); dalsze kroki (wektoryzacja, Béziery, listwy)
  dopisał agent B. Porównanie tokenów Python↔Kotlin nic nie pokaże, ale pochodzenie jest udokumentowane w planie i
  komentarzach, więc traktujemy to jako utwór zależny.
- `materialy/kreda/` nie ma pliku licencji, a README nie podaje autora ani licencji.
- Potrzebne przed publikacją gałęzi `kreda`: **pisemna zgoda Piotrka** (wystarczy wiadomość) i **wybór licencji**
  dla tej części (najprościej: ta sama co projekt). Bez zgody gałąź `kreda` zostaje prywatna; `main` jest od niej
  niezależny.
- Zdjęcia tablic (`materialy/kreda/blackboard/`) **nigdy nie trafiły do repo** (testy czytają je ze zmiennej
  `KREDA_DIR`) i nie powinny: mogą pokazywać wykładowcę i salę.

Wzór wiadomości do Piotrka:
> Chcę opublikować mNotes jako open source na licencji X. Gałąź „kreda” zawiera algorytmy przeniesione z Twojego
> `kreda.py` do Kotlina. Czy zgadzasz się na ich publikację na licencji X i jak mam Cię podpisać (imię i nazwisko,
> pseudonim, bez podpisu)?

### 3.5 Łącze Nearby

Łącze (`SyncCrypto.kt`, `SyncLink.kt`, `NearbyTransport.kt`) powstało na wzór kodu z innego projektu Marcina —
automatyczne porównanie pokazało wspólne fragmenty i komunikaty błędów. **D8 zamknięte (Marcin, 28.10):** kod jest
w całości jego, zgoda na użycie w mNotes jest. Kopia wzoru (dawne `docs/nearby/kod`, `docs/nearby/testy`) usunięta
z drzewa (2e-B2); w repozytorium nie ma wzmianek o tamtym projekcie, a historia z nimi nie trafia do publicznej
wersji (§4, warunek twardy).

### 3.6 Ikony, czcionki, obrazy, pliki testowe

| Element | Pochodzenie | Publikować? |
|---|---|---|
| Ikona aplikacji (`res/drawable/ic_launcher_*.xml`) | własny wektor (kilka ścieżek pióra), nie szablon | ✔ |
| Ikony w UI | Material Icons z `material-icons-extended` (Apache-2.0) + własne glify rysowane w kodzie (`BoardCameraGlyph`, gumka, lasso, pióra) | ✔ |
| Czcionki | brak własnych plików czcionek; systemowe | ✔ |
| `docs/podglad/*.png` (5 plików) | rendery z testów (`tools/PreviewHarness.kt`), nie zdjęcia | ✔ |
| `docs/podglad/tikz-przyklad.tex` | wygenerowany testem `TikzExportTest` | ✔ |
| `app/src/test/resources/samsung/*` | syntetyczne: `skills/generuj-fixture-samsung.py` z `synth_sdocx.sample_pages()`, bez prywatnych danych | ✔ |
| `app/src/test/resources/zadania/*` | syntetyczne: `skills/generuj-fixture.py` (strony i obrazy „x”, „y”) | ✔ |
| Import Samsung Notes / GoodNotes | czytniki formatów napisane od zera dla zgodności; nazwy marek tylko opisowo | ✔ (nie używać marek w nazwie ani ikonie) |
| `skills/` (narzędzia Python do importu) | nasze; format „skill” dla Claude | do decyzji D10 |

## 4. Historia git

Skan wszystkich gałęzi (`git log --all -p`), 28.10:

| Co | Wynik |
|---|---|
| Commity / gałęzie | 141 commitów, 55 gałęzi (`main`, `noc/*`, `wip/*`); pierwszy `e2bac7b` 26.10 „Stan wyjściowy paczki na noc” |
| Autor | wszystkie: `agent-noc <agent-noc@localhost>`; 138 z `Co-Authored-By: Claude … <noreply@anthropic.com>` |
| Klucze i hasła | brak (trafienia to tylko nazwy klas `SecretKeySpec` i wpis `secrets.properties` w `.gitignore`) |
| `local.properties`, `*.keystore`, `*.jks`, APK | nigdy nie zacommitowane |
| Adresy e-mail | tylko `noreply@anthropic.com` i sztuczne nazwy plików w testach |
| Ścieżki domowe | 13 wystąpień `/home/piotr/…` w historii; w bieżącym drzewie 2 pliki (`docs/KREDA-POMIARY.md`, `docs/NOTATKI-NOC.md`) |
| Imiona | „Marcin” w 12 plikach (5 w kodzie i testach: komentarze „w zeszycie Marcina”, nazwa urządzenia „Tablet Marcina” w teście) |
| Wzmianki o agentach/Claude/Fable | 33 pliki (komentarze z numerami zadań nocy, `CLAUDE.md`, `skills/`) |
| Duże pliki binarne | brak; największe to rendery PNG w `docs/podglad/` (do ~0,5 MB); żaden plik binarny nie został usunięty z historii |
| Kod spoza projektu w historii | kreda (`src/lab/core/image/*`), kopia wzoru łącza z projektu zewnętrznego (dawne `docs/nearby/kod`) i wzmianki o nim w plikach i komunikatach commitów |
| Dokumenty wewnętrzne | `CLAUDE.md`, `docs/NOTATKI-NOC.md`, `docs/STAN-PRACY.md`, `docs/INTEGRACJA-MLABS.md`, `docs/NEARBY-PRZEBUDOWA.md`, `docs/KREDA-POMIARY.md` |

Opcje:
- **Pełna historia**: przejrzystość, ale w historii `main` zostaje kod kredy i wzmianki o projekcie zewnętrznym (nawet po usunięciu z drzewa),
  ścieżki domowe, imiona i dziennik nocy. Przepisanie historii (`git filter-repo`) jest możliwe, ale przy 55 gałęziach i
  scaleniach pracochłonne i łatwo coś przeoczyć.
- **Czysty start (rekomendacja)**: publiczna gałąź zaczyna się od jednego commita z drzewem `oss/main`
  („mNotes 0.1.0: pierwsza publiczna wersja”), autor wybrany przez Marcina; `kreda` = ten commit + jeden commit z
  katalogami kredy. Pełna historia zostaje prywatnie w tym repo (archiwum, nic nie ginie).

**Warunek twardy publikacji (Marcin, 28.10 12:45):** publiczne repo startuje wyłącznie od czystej historii (nowy commit
początkowy), bez wzmianek o projekcie zewnętrznym wskazanym przez Marcina — w plikach i w komunikatach commitów. Lista
nazw do sprawdzenia jest poza repozytorium (`plan/`); przed publikacją `git grep -i` tych nazw na publikowanym drzewie
i `git log --all --format=%B` publicznej gałęzi muszą być puste.

## 5. Higiena repozytorium

| Plik / temat | Stan | Plan |
|---|---|---|
| README | krótki, wewnętrzny | nowy: cel (nauka i dydaktyka, offline), funkcje, zrzuty z `docs/podglad/`, budowanie, warianty, licencja, udział AI, podziękowania; po polsku z krótką sekcją EN (D12) |
| CONTRIBUTING | brak | szkic: budowanie, testy JVM, konwencje (kod po angielsku, komentarze po polsku, teksty w `strings.xml`), DCO, zasady „offline, bez INTERNET, bez telemetrii” |
| CODE_OF_CONDUCT | brak | Contributor Covenant 2.1 (odnośnik, kontakt do ustalenia) |
| LICENSE | brak | po decyzji D1; do tego czasu `LICENSE-DO-DECYZJI.md` |
| NOTICE | brak | szkic: zależności i licencje, podziękowania (autorka licencjatu bez nazwiska, Piotrek po zgodzie) |
| `.gitignore` | dobry (local.properties, keystore, jks, apk, aab, secrets.properties) | dopisać `keystore.properties` |
| Budowanie bez narzędzi Piotrka | dziś przez `narzedzia/toolchain/env.sh` | wystarczy JDK 21 i Android SDK z platformą 36.1 (`compileSdk = release(36) { minorApiLevel = 1 }`); Gradle 9.5.0 przez wrapper, AGP 9.3.3, Kotlin 2.2.10; opisać w README |
| CI | brak | GitHub Actions: `setup-java` 21 + SDK, `./gradlew :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:assembleFossDebug`; na `kreda` dodatkowo testy kredy i sprawdzenie, że gałąź zmienia tylko `app/src/kreda*`/`app/src/testKreda*` |
| Wydania | APK debug | klucz release u Marcina, poza repo; `keystore.properties` (ignorowany) czytany przez Gradle, gdy istnieje. Uwaga: nowy podpis wymaga odinstalowania wersji debug z tabletu, więc **przed zmianą eksport wszystkich zeszytów** |
| Zgłaszanie błędów | — | GitHub Issues z szablonem (urządzenie, Android, wariant, kroki); błędy bezpieczeństwa (szyfrowanie łącza) przez prywatne zgłoszenie GitHub, nie publiczne issue |
| `applicationId`, wersja | `com.mdev.mnotes`, 1 / 0.1.0 | bez zmian bez Marcina; przed Google Play sprawdzić, czy identyfikator jest wolny |
| Dokumenty wewnętrzne | w repo | poza publicznym drzewem: `CLAUDE.md`, `docs/NOTATKI-NOC.md`, `docs/STAN-PRACY.md`, `docs/INTEGRACJA-MLABS.md`, `docs/NEARBY-PRZEBUDOWA.md`, `docs/nearby/`; `docs/features/` przejrzeć (specyfikacje mogą zostać) |
| Komentarze z numerami zadań nocy („2b-A2”, „uwagi Marcina”) | w kodzie | mogą zostać (nieszkodliwe); imiona w 5 plikach kodu zamienić na neutralne („użytkownik”) |

## 6. Plan i decyzje

### 6.1 Plan krok po kroku

1. **Agent (3a-2, odwracalne, bez publikacji):** gałąź `oss/main` z podziałem z §1.3 (warianty `foss`/`gms`, łącze w
   `src/gms`, bez kredy i bez „Wyślij do mLabs”, bez dokumentów wewnętrznych), testy obu wariantów zielone; gałąź
   `oss/kreda` = `oss/main` + `app/src/kreda*`; szkice README, CONTRIBUTING, NOTICE, `LICENSE-DO-DECYZJI.md`,
   `.github/workflows/ci.yml`.
2. **Marcin:** decyzje D1–D14 (poniżej; D8 zamknięte); wiadomość do Piotrka (§3.4).
3. **Agent albo Marcin:** LICENSE i nagłówek licencji wg D1; README z udziałem AI (D4) i podziękowaniami (D9);
   zamiana imion w komentarzach.
4. **Marcin, na tablecie:** sprawdzić APK `gms` z `oss/main` (zdjęcie z telefonu, wysyłanie zeszytu) i `foss` (brak
   przycisku telefonu, reszta działa); APK z `oss/kreda` (kreda jak dziś).
5. **Marcin:** czysty commit publiczny (D2), np.:
   ```
   cd /home/piotr/Projects/Marcin/projekty/mNotes
   git switch --orphan publiczne/main
   git checkout oss/main -- .
   git commit -m "mNotes 0.1.0: pierwsza publiczna wersja"
   ```
   i po zgodzie Piotrka:
   ```
   git switch -c publiczne/kreda
   git checkout oss/kreda -- app/src/kreda app/src/testKreda
   git commit -m "Kreda: oczyszczanie zdjęć tablicy i wektoryzacja"
   ```
6. **Marcin:** repozytorium na GitHubie, wypchnięcie `publiczne/main` jako `main` (i `publiczne/kreda` jako `kreda`
   dopiero po zgodzie Piotrka); włączenie prywatnych zgłoszeń bezpieczeństwa; pierwsze wydanie z kluczem release.
7. Później: F-Droid (`foss`), opcjonalnie transport Bluetooth dla `foss` (§2).

### 6.2 Decyzje dla Marcina

| # | Decyzja | Rekomendacja |
|---|---|---|
| D1 | Licencja: GPL-3.0-or-later / Apache-2.0 / MIT | GPL-3.0-or-later + wyjątek dla Google Play Services; Apache-2.0, jeśli chcesz prostoty albo zamkniętej wersji w przyszłości |
| D2 | Historia: pełna czy czysty start | czysty start (jeden commit), pełna historia prywatnie |
| D3 | Autor w publicznym repo (imię i nazwisko / pseudonim, e-mail) | Twoje imię i nazwisko, e-mail `…@users.noreply.github.com` |
| D4 | Wzmianka o udziale AI | tak, jedno zdanie w README |
| D5 | Nearby: warianty `foss` + `gms` | tak; własny transport (Bluetooth) później, jeśli potrzebny |
| D6 | „Wyślij do mLabs” i `Recognizer` | przy kredzie, do czasu publikacji mLabs |
| D7 | Kreda publicznie | tylko po pisemnej zgodzie Piotrka i jego wyborze licencji (najlepiej tej samej) |
| D8 | Wzór łącza z innego projektu Marcina | **zamknięte 28.10:** zgoda jest; wzmianki usunięte z drzewa (2e-B2), publikacja tylko z czystą historią |
| D9 | Podziękowanie autorce licencjatu | bez nazwiska, chyba że się zgodzi |
| D10 | Narzędzia `skills/` (import Samsung Notes, zadania z PDF) | publikować jako `narzedzia/` z README; format „skill” zostaje |
| D11 | `applicationId` `com.mdev.mnotes` | zostaje; przed Google Play sprawdzić, czy wolny |
| D12 | Język repo | README po polsku + krótka sekcja EN; komentarze zostają po polsku |
| D13 | Klucz wydań | nowy klucz release u Ciebie, poza repo; przed zmianą podpisu eksport zeszytów z tabletu |
| D14 | Kanały wydań | GitHub Releases (oba warianty), potem F-Droid (`foss`); Google Play później |

## Stan

**28.10, 12:00:** analiza (3a-1) gotowa.

**28.10, 12:10: 3a-2 w toku** (przerwane, bo FAZA 2c ma pierwszeństwo; wraca po niej):

| Gałąź | Commit | Co jest | Testy |
|---|---|---|---|
| `oss/main` | `b1eea12` (od `noc/wszystko` @ `de6d5e1`) | warianty `foss`/`gms`; łącze (`lab/nearby/*`, nowy `lab/PhoneLink.kt` z sekcją ustawień i `LinkSettings`) w `src/gms/`; `LabFeatures` z domyślnymi metodami i `board`; `app/Extensions.kt` skleja łącze i kredę (ładowaną po nazwie klasy); bez kredy i bez „Wyślij do mLabs” | foss 817, gms 817, 0 porażek; oba APK |
| `oss/kreda` | `f38c3c7` = `oss/main` + 1 commit | tylko katalogi `app/src/kreda/` (kreda, `Lab.kt` bez części Nearby, `LabSettings` bez przełącznika łącza, `recognize/*`) i `app/src/testKreda/` | foss 888, gms 888, 0 porażek (5 pominiętych: zdjęcia bez `KREDA_DIR`); oba APK |

Sprawdzone w APK z `oss/main`: `foss` nie ma w dex klas `com/google/android/gms/nearby` ani uprawnienia `BLUETOOTH_SCAN`;
`gms` ma oba; żaden nie ma klasy kredy `com.mdev.mnotes.lab.Lab`. Ustawienia zostają w `mnotes.lab` pod tymi samymi
kluczami (`nearby.enabled`, `board.*`).

Jeszcze nie zrobione: usunięcie dokumentów wewnętrznych i `docs/nearby/` z `oss/main`, szkice README/CONTRIBUTING/NOTICE,
`LICENSE-DO-DECYZJI.md`, `.github/workflows/ci.yml`, zamiana imion w komentarzach, nazwa kategorii „Laboratorium”
(w `oss/main` jest w niej tylko łącze), `docs/KREDA-POMIARY.md` bez ścieżek domowych na `oss/kreda`. Nie sprawdzone na
urządzeniu: niczego z 3a-2 nie uruchamiałem (bez tabletu); przejście z dzisiejszego APK `lab` na `gms`/`kreda` powinno
zachować parowanie i ustawienia, bo pliki preferencji i klucze są te same.
