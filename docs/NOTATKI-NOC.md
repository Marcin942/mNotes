# Notatki nocy

Wpisuj tu na bieżąco: decyzje, pomysły spoza zakresu, miejsca, gdzie utknąłeś, i na koniec co Marcin ma sprawdzić.

## Agent A (mNotes), 27.10.2026

Start: gałąź `noc/bledy` od `main` (baza 513 testów, 1 pominięty, 0 błędów; APK debug buduje się).
Rysunki z „Pomysły mNotes.pdf” odczytane własnym rasteryzatorem (Python, bez zależności) — pomogły przy B7, B10, F3, F28.

### Błędy (gałąź `noc/bledy`)
- **B1** gumka pikselowa: `EraseSession` + `EraseMath.eraseSwept` + `BoundsGrid`. Test `EraseSessionTest` (poprawność: nic nie zostaje pod ścieżką, dalekie kreski to te same obiekty; pomiar: dawny algorytm ≈ 4 s / 300 zdarzeń przy gumce 1 pt, nowy ≈ 45 ms). Tablet: płynność na gęstej stronie z zakreślaczami.
- **B2** pusta miniatura w „Strony”: przyczyna w Compose (kafel czytał `page.version` sam, strong skipping go pomijał) + gubiona druga prośba o miniaturę. `CoalescerTest`. Tablet: nowa strona, pisanie przy otwartym (zadokowanym) panelu stron.
- **B3/B10** prostokąt: próbki po długości kreski, zapas 4 dp. **B7** nachodzące pętle: niezerowe nawinięcie. `LassoSelectTest`.
- **B4** start poza stroną: lasso już działało (`layout.nearest`), dołożone lasso gumki i pędzla formatu. `DocLayoutTest`.
- **B5** import `.mnotes`: `MnotesArchive` (ZipFile z pliku tymczasowego, warianty ścieżek). Dawna przyczyna „tylko pierwsza strona” (rozpoznanie jako PDF przez nieskompresowane tło) była już poprawiona w `FileKinds`, ale bez testu — test dopisany. `MnotesArchiveTest`.
- **B9** krótkie proste: `ShapeRecognizer.minLineFor`. `ShortLineTest`.
- **B6 — nie odtworzono.** Przycisk „Skaluj i obracaj” od razu woła `view.invalidate()`, zaznaczenie po dotknięciu też odświeża widok w `onUp`. Hipotezy: (a) chodzi o przytrzymanie rysika do rozpoznania kształtu (`input.hold_ms` = 550 ms, a każdy dryf > 10 dp liczy czas od nowa, więc przy drżącej ręce wychodzi „1 s albo więcej”) — wtedy pomoże krótszy czas w Ustawienia → Wejście → „Czas przytrzymania”; (b) uchwyty boków pola tekstowego. **Marcin: jaki to uchwyt (kształt? tekst? „Skaluj i obracaj”?) i czy skrócenie „Czasu przytrzymania” pomaga.**
- **Decyzja (teksty):** aplikacja nie używa `strings.xml` (ma tylko nazwę), wszystkie napisy idą przez `T.t("pl", "en")` z przełącznikiem języka w Ustawieniach; nowe napisy piszę tak samo, bo `strings.xml` zepsułby ten przełącznik.
- **B8 — nie odtworzono.** W kodzie nie ma żadnego automatycznego przełączenia na „Rękę”. Hipotezy: (a) dymek-podpowiedź „Ręka (przewijanie)” przy najechaniu rysikiem na pasek (tooltip), (b) zaznaczenie dotknięciem palca w trybie „palec przewija” (EDT-036a) wygląda jak tryb ręki. **Marcin: kiedy dokładnie (lasso? dotknięcie palcem?) i co widać — ikona na pasku czy tylko napis.**

### Funkcje
- **F1** lasso „Zbalansowane / Tylko zahacz” — `noc/lasso`; `LassoSelectTest` (musnięcie kreski, róg obrazu w pętli). Tablet: panel lassa (przytrzymaj ikonę lassa / dotknij ponownie), obie opcje; Ustawienia → Gumka i lasso.
- **F2** wyłączanie napisów: dwa przełączniki w Ustawienia → Wygląd (podpowiedzi nad przyciskami, dymki po wyborze narzędzia) — `noc/lasso`; tylko UI, bez testu JVM. Tablet: wyłącz oba, najedź rysikiem na pasek i wybierz Taśmę — nie powinno być żadnego napisu. To może też tłumaczyć B8 (dymek „Ręka (przewijanie)” przy najechaniu rysikiem).
- **F3** rozcięcie prostej gumką pikselową daje dwie proste z uchwytami (`o—o o—o`) — `noc/lasso`; `PolyCutTest`. Tablet: narysuj prostą z przytrzymaniem, przetnij gumką pikselową, dotknij kawałka lassem — na końcach mają być niebieskie uchwyty.
- **F5** strony pionowe/poziome i różne formaty — `noc/strony`; `PageOrientationTest`. Tablet: panel stron → przycisk obok „Dodaj stronę” (dodaje poziomą po pionowej), „Styl strony” → Orientacja i Rozmiar (A4/A5); przewijanie w obu układach (pion/poziom) przez strony różnej wielkości, eksport PDF.
- **F6** animacja przestawiania stron (płótno + lista miniatur), wyłączana i z regulowanym czasem — `noc/strony`; `PageShiftTest`. Tablet: panel stron → „Wcześniej/Dalej” i przeciąganie miniatury przy widocznych sąsiednich stronach; Ustawienia → Wygląd → wyłączyć i sprawdzić, że strony przeskakują bez animacji.
- **F7** listwa narzędzi dla gumki/lassa/lasera — `noc/narzedzia`; `LaserColorsTest` (logika kolorów), reszta UI. **Wygląd:** ta sama listwa co przy piórze (zadokowana obok strony, te same przyciski `ToolBtn` 36–46 dp, separatory `BarSep`, akcent Mint na wybranej pozycji); gumka: 3 ikony trybów rysowane w siatce 24 jak Material (klocek nad kreską / kreska przerwana z okruchami / przerywana pętla z klockiem), 3 kółka rozmiarów, zakreślacz, „Tune”; lasso: przerywana pętla i przerywany prostokąt, „zaznacz wszystko”/„dotknięcie” dla trybu; laser: 4 kropki kolorów jak kolory pióra. Tablet: wybierz gumkę — listwa zmienia się na tryby; to samo lasso i laser (kolor śladu); wyłącz w Ustawieniach → Wygląd.
- **Dopisek 00:55** (cel: nauka, spójna estetyka): przy wpisach NOC w BACKLOG jest „Nauka: …”; nowe UI używa istniejących elementów (listwa, `ToolBtn`, `FloatingCard`, chipy). Teksty nadal przez `T.t` (patrz decyzja wyżej), nie `strings.xml`.

### Stan przy przerwie (limit użycia)
- Gałęzie (każda od poprzedniej): `noc/bledy` → `noc/lasso` → `noc/strony` → `noc/narzedzia` (najnowsza, zawiera wszystko). Na `noc/narzedzia`: **553 testy, 1 pominięty, 0 błędów, APK debug buduje się.**
- Zrobione: B1–B5, B7, B9, B10 (B6, B8 nieodtworzone, hipotezy wyżej), F1, F2, F3, F5, F6, F7.
- **Wznowienie od F8** (Układ współrzędnych), potem F10…F28 (F9 to etap 4, F4 po reszcie), L1, L2, pomysły Fable; na końcu `noc/wszystko`, raport w RAPORT-NOCY.md i plik KONIEC-A.
- **F8** układ współrzędnych — `noc/uklad`; `AxesTest` (podziałka, format liczb, położenie osi, zapis ustawień). **Wygląd:** okno `MDialog` jak eksport: na górze podgląd w zaokrąglonym polu w kolorach motywu, pod nim chipy gotowych zakresów, dwa wiersze „x od … do … krok”, suwak jednostki (mm), przełączniki `ToggleRow`, chipy „Opis co 1/2/5”. Na stronie: cienka siatka w przygaszonym kolorze pióra, osie 1,1 pt ze strzałkami, opisy 5,5–10 pt (nazwy osi kursywą). Tablet: menu „⋮” → „Układ współrzędnych…”, „I ćwiartka”, Wstaw; przesunąć lassem, narysować wykres piórem, cofnąć jednym ruchem; eksport PDF.
- **F10** auto-dodawanie strony przy tekście — `noc/tekst`; `PageOverflowTest`. **Wygląd:** bez nowego UI; dymek (ten sam co inne komunikaty edytora) „Tekst wychodzi poza ostatnią stronę — dodano nową”, przełącznik w Ustawienia → Wejście obok „Odciągnij za ostatnią stroną”. Tablet: na ostatniej stronie pole tekstowe przy dole, wpisz kilka akapitów → pojawia się nowa strona; Tekst + dotknięcie pod ostatnią stroną → nowa strona z polem; jedno cofnięcie.
- **F11** odwracanie kolorów strony — `noc/obraz`; `ColorInvertTest`. **Wygląd:** przełącznik `ToggleRow` w panelu „Styl strony” (FloatingCard) pod „Zastosuj do całego zeszytu”, z jednym zdaniem wyjaśnienia; strona po włączeniu ciemna, atrament jasny w swoim odcieniu. Tablet: dokument PDF → Styl strony → Odwróć kolory (+ cały zeszyt); pisanie czerwonym i niebieskim na odwróconej stronie (kolor w trakcie = po zatwierdzeniu), przesuwanie lassem, miniatury w panelu stron, eksport PDF w oryginale; płynność przewijania przy dużym powiększeniu (warstwa z filtrem).
- **F12** gumka „Wycinanie” — `noc/obraz`; `CutOutTest`. **Wygląd:** czwarty chip w panelu gumki i czwarta ikona na listwie (przerywana pętla z nożyczkami w tym samym stylu co pozostałe glify), pętla w trakcie rysowania czerwona przerywana jak przy lassie gumki. Tablet: gumka → Wycinanie, obrysuj środek kilku kresek pisma i część rozpoznanego prostokąta; brzegi cięcia równe, prostokąt zostaje łamaną z uchwytami; cofnij.
- **F13 + F15** ton obrazu i autobalans bieli — `noc/obraz`; `ImageToneTest`. **Wygląd:** panel „Efekty obrazu” (FloatingCard 320 dp, teraz przewijany wewnątrz): chipy efektu, suwaki Jasność / Temperatura / Odcień (podpisy „+12”), sekcja „Balans bieli” z dwoma chipami, zwijany nagłówek „Kanały R / G / B” ze strzałką, krycie, „Przywróć oryginał”. Tablet: zdjęcie kartki zrobione przy żarówce → Auto: białe tło; przesunąć temperaturę i rozwinąć R/G/B (suwaki się poruszają razem); cofnij jednym ruchem; płynność suwaków na dużym zdjęciu.
- **F14** wycinek jako obraz / do schowka — `noc/obraz`; `SnipTest` (plan i położenie). **Wygląd:** dwie pozycje w istniejącym menu kontekstowym zaznaczenia (pod „Duplikuj”), dymki potwierdzenia. Tablet: na stronie PDF zaznacz lassem fragment z dopiskiem → przytrzymaj → „Wycinek jako obraz” (obraz obok, ostry po powiększeniu); „Kopiuj wycinek do schowka” → wklej w komunikatorze/Notatkach Samsunga.

## Agent B (mNotes-lab, gałąź `noc/lab`), 27.10.2026

Baza: `noc/obraz` agenta A (zawiera wszystkie jego `noc/*` z 02:15). Worktree `paczka-noc/mNotes-lab`. Testy JVM:
wariant core 594, wariant lab 623 (te same + 29 testów lab); `assembleCoreDebug` i `assembleLabDebug` przechodzą.
`local.properties` nie skopiowany (ochrona plików w uprawnieniach nocy); `ANDROID_HOME` z `.toolchain/env.sh` wystarcza.

- **K0 warianty** `core` / `lab` (`flavorDimensions "wariant"`, ten sam applicationId). Wspólny kod widzi tylko
  interfejs `app/LabFeatures.kt`; `app/Flavor.kt` w `src/core` daje `null`, w `src/lab` obiekt `lab/Lab.kt`.
  W core nie ma kodu kredy ani Nearby (poza czystą logiką `core/nearby`, patrz K5). Budowanie:
  `./gradlew :app:assembleCoreDebug :app:assembleLabDebug :app:testCoreDebugUnitTest :app:testLabDebugUnitTest`.
- **K1 kreda** (`src/lab/.../core/image/`: `Raster`, `BoardFilters`, `BoardGeometry`, `Chalk`, `BoardPipeline`):
  przepisane od zera z algorytmów `kreda.py` na `IntArray`, bez OpenCV. Odstępstwo od wzoru: auto-próg to
  mediana + 4.5·σ z górnej połowy rozkładu (MAD po wyrównaniu oświetlenia zaniżał σ i zostawiał mgłę szumu).
  **Wygląd:** w menu zaznaczonego zdjęcia (pasek nad zaznaczeniem) przycisk z różdżką „Oczyść tablicę” (tylko lab);
  otwiera okno `MDialog` z podglądem na szachownicy, wyborem „Obraz kredy / Edytowalne kreski”, przełącznikami
  kroków (prostowanie, kadr, oświetlenie, wyostrzenie, auto-próg), suwakami progu i krycia i kolorem kredy
  (oryginalny / biały / grafit). Domyślne kroki w Ustawienia → Laboratorium → „Kreda: zdjęcia tablicy”.
  Dydaktyka: notatki z wykładu bez przepisywania tablicy.
- **K2 wektoryzacja** (`ChalkVector`): Zhang-Suen, łamane szkieletu, Douglas-Peucker → kreski pióra (`poly`)
  nad zdjęciem; zdjęcie zostaje. Znane ograniczenie: „schodki” szkieletu dzielą łuki (pętla O wychodzi w kawałkach).
- **K3 mediana** (`BoardStack`, `BoardPipeline.runMany`): zaznacz 2–6 zdjęć tej samej tablicy → ten sam przycisk
  („Połącz zdjęcia tablicy (bez osób)”) → nowy obraz bez wykładowcy; „różnica tablic” (`BoardStack.added`) tylko jako logika.
- **K4** `lab/recognize/Recognizer.kt`: interfejs bez modeli (`NoRecognizer`) i `PageExport` (JSON `mnotes-strona` v1,
  zgodny z mLabs). Opis integracji: `docs-noc/INTEGRACJA-MLABS.md`.
- **K5 Nearby**: logika w `core/nearby` (AES-256-GCM, ECDH P-256 + 6 cyfr, porcje do 24 MiB, sesja z licznikiem, wiadomości,
  plan synchronizacji stron; 15 testów, w tym wektor RFC 5869). Łącze `lab/nearby/NearbyLink.kt` na
  **`com.google.android.gms:play-services-nearby:19.3.0`** — jedyna nowa zależność nocy, **uzasadnienie**: Nearby to
  jedyny sposób na łącze telefon–tablet bez routera i bez internetu (Bluetooth / Wi-Fi Direct); tylko w wariancie lab
  (`labImplementation`), za przełącznikiem „Włącz łącze z telefonem” (domyślnie wyłączony), bez uprawnienia INTERNET
  (manifest lab usuwa je `tools:node="remove"`; sprawdzone w scalonym manifeście). Wersja 19.3.0, bo 19.4.0 wymaga
  desugaringu, a 19.5 Kotlina 2.4 (projekt: 2.2.10). Pierwszy build z nią był online (pobranie AAR), potem offline.
  Parowanie **bez QR** (w aplikacji nie ma skanera): jawna wymiana kluczy publicznych przez Nearby i sześć cyfr na obu
  ekranach; `Pairing.Offer.toCode()` jest gotowe pod przyszły kod QR. Klucz pary zaszyfrowany kluczem z Android Keystore.
  **Wygląd:** w pasku strony obok „Wstaw obraz” ikona telefonu „Zdjęcie z telefonu” (gdy łącze włączone); okno z
  jednym zdaniem stanu („Czekam na …”, „Wstawiono zdjęć: 2”); na telefonie Ustawienia → Laboratorium → „Telefon i
  tablet (Nearby)”: parowanie, „Wyślij zdjęcie na tablet” (galeria, zdjęcia > 4 MB zmniejszane do 3000 px, JPEG 88).
  **Sync zeszytów: tylko logika i protokół** (`SyncPlan`, `PageList/PageRequest/PageData`), ekranu jeszcze nie ma.

**Co Marcin ma sprawdzić na tablecie (lab):**
1. Zainstaluj `labDebug`. Wstaw zdjęcie tablicy z `kreda/blackboard/` → zaznacz → różdżka „Oczyść tablicę”: podgląd
   pojawia się w ciągu 1–2 s; „Zastosuj” zamienia zdjęcie na kredę na przezroczystym tle; cofnij przywraca zdjęcie.
2. To samo z „Edytowalne kreski”: kreski pióra nad zdjęciem, da się je zaznaczyć lassem i przesunąć.
3. Wyłącz kolejno kroki w panelu i w Ustawienia → Laboratorium; porównaj z wynikiem `kreda.py` na tych samych zdjęciach.
4. Zaznacz 2–3 zdjęcia tej samej tablicy (z wykładowcą w różnych miejscach) → „Połącz zdjęcia tablicy”.
5. Nearby (tablet + telefon z Usługami Google): włącz łącze na obu, sparuj (te same cyfry), na tablecie ikona
   telefonu, na telefonie „Wyślij zdjęcie na tablet”; zdjęcie trafia na stronę. Sprawdź prośby o uprawnienia
   (Android 16: lokalizacja jest wymagana do szukania, inaczej błąd 8034).
6. Wariant `coreDebug`: brak przycisku różdżki, ikony telefonu i kategorii „Laboratorium”.

Nie testowane: nic z UI na urządzeniu (brak tabletu), wydajność kredy na zdjęciu 12 Mpx (praca na 2400 px), Nearby
w ogóle (wymaga dwóch urządzeń).
