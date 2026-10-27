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

### Raport końcowy agenta A (ok. 03:45)
**Zrobione:** B1–B5, B7, B9, B10 (B6, B8 nie odtworzono — pytania niżej), F1–F8, F10–F23, F25–F28 (F9 i F24 to etap 4 agenta B), L1, L2, pomysły Fable 1, 2, 3, 9, 11, 12 (10 już był). Szczegóły, pliki i testy: wpisy poniżej oraz BACKLOG sekcja K (każdy wpis z „Nauka: …”).

**Gałęzie** (każda od poprzedniej, więc każda zawiera wcześniejsze): `noc/bledy` → `noc/lasso` → `noc/strony` → `noc/narzedzia` → `noc/uklad` → `noc/tekst` → `noc/obraz` → `noc/krzywe` → `noc/naklejki` → `noc/przewodnik` → `noc/punkty` → `noc/przyczep` → `noc/opcje` → `noc/symbole` → `noc/dwa-zeszyty` → `noc/licencjat` → `noc/fable`. **`noc/wszystko`** = `noc/fable` + scalona `noc/lab` agenta B (konflikty tylko addytywne: importy, okna w `EditorScreen`, ten plik). `main` nietknięty, bez `git push`.

**Testy (JVM):** na `noc/fable` (bez wariantów) 642 testy, 1 pominięty, 0 błędów, APK debug buduje się. Na `noc/wszystko` (warianty B): `testCoreDebugUnitTest` 657, `testLabDebugUnitTest` 686, 0 błędów; `assembleCoreDebug` i `assembleLabDebug` przechodzą; scalony manifest core ma tylko uprawnienia biometrii (bez INTERNET). Polecenie po scaleniu: `./gradlew --offline :app:testCoreDebugUnitTest :app:testLabDebugUnitTest :app:assembleCoreDebug :app:assembleLabDebug`.

**Zgodność plików:** nowe dane tylko jako flagi/dodatki zgodne wstecz — krzywa Béziera (flaga 64 kreski + punkty kontrolne na końcu rekordu; łamana jak dawniej), odwrócone kolory (bit 2 w polu `o` stylu strony), wyjustowanie (wyrównanie 3), wzór milimetrowy (kod 9). Starsza wersja pokaże: łamaną zamiast edytowalnej krzywej, stronę bez odwrócenia, tekst do lewej, gładką kartkę. Testy na starych fixture'ach (`PageCodecTest`, `SkillSamsungFormatTest`, `SkillZadaniaFormatTest`) przechodzą.

**Nie przetestowane (brak urządzenia):** całe UI i dotyk — wszystkie punkty niżej. Szczególnie ryzykowne: F4 (dwa płótna naraz: pamięć, cykl życia widoków), F11 (warstwa z filtrem a płynność przy dużym zoomie), F21 (podwójne dotknięcie 350 ms — może kolidować z szybkim przesuwaniem zaznaczenia), F28 (fałszywe trafienia √/∑ przy zwykłym piśmie z przytrzymaniem), Fable 3 (mini-mapa zasłania prawy dolny róg przy dużym zoomie), aparat w trybie wykładu (zachowanie aplikacji aparatu Samsunga z plikiem FileProvider).

**Co Marcin ma sprawdzić na tablecie — krok po kroku (najważniejsze najpierw):**
1. Otwórz stary zeszyt (z PDF-em i z obrazami): strony, miniatury, eksport PDF bez zmian.
2. B1: gumka pikselowa 1 pt i 14 pt po gęstej stronie z zakreślaczami — bez zacinania.
3. B2: dodaj stronę przy otwartym panelu stron i pisz — miniatura odświeża się po każdej kresce.
4. B3/B7/B10 + F1: lasso prostokątem wokół prostej po przekątnej; pętla z dwoma okrążeniami; „Tylko zahacz” na listwie lassa.
5. B5: zaimportuj wielostronicowy `.mnotes` z innego urządzenia — wszystkie strony.
6. B9 + F28: krótka kreska z przytrzymaniem (minus) prostuje się; √ i ∑ z przytrzymaniem; zwykłe słowo z przytrzymaniem zostaje pismem.
7. F7: wybierz gumkę, lasso, laser — listwa obok strony pokazuje ich opcje.
8. F17/F18/F21: narzędzie Krzywa (na pasku, może pod „⋯”); koło → podwójne dotknięcie lassem → kotwice; przytrzymaj kotwicę → menu.
9. F4: „⋮” → „Drugi zeszyt poniżej…”: dwa rzędy, rozdzielacz, pisanie w obu, ✕.
10. F8, L1: „⋮” → „Układ współrzędnych…”; trójkąt → menu zaznaczenia → „Okrąg opisany”.
11. F11: PDF → „Styl strony” → „Odwróć kolory”.
12. F13/F15/F14/F23: zdjęcie kartki → Efekty obrazu → „Auto: białe tło”; „Wycinek jako obraz”; „Uporządkuj obrazy”.
13. F19/F20: „Zapisz jako naklejkę…”, „⋮” → „Naklejki…”; Biblioteka → Nowy → „Zeszyt-przewodnik” i jego ćwiczenia.
14. Tryb wykładu (Fable 1): „⋮” → „Tryb wykładu…” → dwa zdjęcia.
15. Ustawienia: wyszukiwarka znajduje nowe opcje (dymki, animacja stron, maks. zoom, powrót do pisania, mini-mapa, grecki, zdjęcia do kratki).
16. **Pytania:** B6 — który uchwyt pojawia się po ~1 s? B8 — kiedy pióro „zmienia się w rękę”? L1 — czy wystarczą konstrukcje z zaznaczenia, czy potrzebny tryb „wybierz drugi punkt”?

### Szczegóły punktów (kontynuacja)
- **F8** układ współrzędnych — `noc/uklad`; `AxesTest` (podziałka, format liczb, położenie osi, zapis ustawień). **Wygląd:** okno `MDialog` jak eksport: na górze podgląd w zaokrąglonym polu w kolorach motywu, pod nim chipy gotowych zakresów, dwa wiersze „x od … do … krok”, suwak jednostki (mm), przełączniki `ToggleRow`, chipy „Opis co 1/2/5”. Na stronie: cienka siatka w przygaszonym kolorze pióra, osie 1,1 pt ze strzałkami, opisy 5,5–10 pt (nazwy osi kursywą). Tablet: menu „⋮” → „Układ współrzędnych…”, „I ćwiartka”, Wstaw; przesunąć lassem, narysować wykres piórem, cofnąć jednym ruchem; eksport PDF.
- **F10** auto-dodawanie strony przy tekście — `noc/tekst`; `PageOverflowTest`. **Wygląd:** bez nowego UI; dymek (ten sam co inne komunikaty edytora) „Tekst wychodzi poza ostatnią stronę — dodano nową”, przełącznik w Ustawienia → Wejście obok „Odciągnij za ostatnią stroną”. Tablet: na ostatniej stronie pole tekstowe przy dole, wpisz kilka akapitów → pojawia się nowa strona; Tekst + dotknięcie pod ostatnią stroną → nowa strona z polem; jedno cofnięcie.
- **F11** odwracanie kolorów strony — `noc/obraz`; `ColorInvertTest`. **Wygląd:** przełącznik `ToggleRow` w panelu „Styl strony” (FloatingCard) pod „Zastosuj do całego zeszytu”, z jednym zdaniem wyjaśnienia; strona po włączeniu ciemna, atrament jasny w swoim odcieniu. Tablet: dokument PDF → Styl strony → Odwróć kolory (+ cały zeszyt); pisanie czerwonym i niebieskim na odwróconej stronie (kolor w trakcie = po zatwierdzeniu), przesuwanie lassem, miniatury w panelu stron, eksport PDF w oryginale; płynność przewijania przy dużym powiększeniu (warstwa z filtrem).
- **F12** gumka „Wycinanie” — `noc/obraz`; `CutOutTest`. **Wygląd:** czwarty chip w panelu gumki i czwarta ikona na listwie (przerywana pętla z nożyczkami w tym samym stylu co pozostałe glify), pętla w trakcie rysowania czerwona przerywana jak przy lassie gumki. Tablet: gumka → Wycinanie, obrysuj środek kilku kresek pisma i część rozpoznanego prostokąta; brzegi cięcia równe, prostokąt zostaje łamaną z uchwytami; cofnij.
- **F13 + F15** ton obrazu i autobalans bieli — `noc/obraz`; `ImageToneTest`. **Wygląd:** panel „Efekty obrazu” (FloatingCard 320 dp, teraz przewijany wewnątrz): chipy efektu, suwaki Jasność / Temperatura / Odcień (podpisy „+12”), sekcja „Balans bieli” z dwoma chipami, zwijany nagłówek „Kanały R / G / B” ze strzałką, krycie, „Przywróć oryginał”. Tablet: zdjęcie kartki zrobione przy żarówce → Auto: białe tło; przesunąć temperaturę i rozwinąć R/G/B (suwaki się poruszają razem); cofnij jednym ruchem; płynność suwaków na dużym zdjęciu.
- **F14** wycinek jako obraz / do schowka — `noc/obraz`; `SnipTest` (plan i położenie). **Wygląd:** dwie pozycje w istniejącym menu kontekstowym zaznaczenia (pod „Duplikuj”), dymki potwierdzenia. Tablet: na stronie PDF zaznacz lassem fragment z dopiskiem → przytrzymaj → „Wycinek jako obraz” (obraz obok, ostry po powiększeniu); „Kopiuj wycinek do schowka” → wklej w komunikatorze/Notatkach Samsunga.
- **F17 + F18** krzywe Béziera — `noc/krzywe`; `BezierTest` (dopasowanie łuku, S, prostej; edycja kotwic i uchwytów), `BezierCodecTest` (zapis, zgodność wstecz). **Wygląd:** przycisk na pasku z glifem S i dwoma uchwytami (styl jak inne glify); po narysowaniu krzywa zaznaczona: kotwice to białe kółka z obwódką w kolorze akcentu (jak wierzchołki łamanej), uchwyty mniejsze, ramiona cienką półprzezroczystą linią akcentu. Tablet: pasek → „Krzywa” (może być pod „⋯”), narysuj łuk i literę S, przeciągnij kotwicę i uchwyt, cofnij; zwykłym piórem narysuj łuk i trzymaj (najpierw nic, po ~0,35 s wygładzenie), sprawdź, że prostokąt i koło nadal się rozpoznają; zapis i ponowne otwarcie (uchwyty dalej są).
- **F19** naklejki — `noc/naklejki`; `StickerTest`, `StickerStoreTest`. **Wygląd:** okno `MDialog` „Naklejki” z siatką kart 104 dp (biała miniatura w zaokrąglonej ramce `m.line`, nazwa pod spodem, mały kosz; drugie dotknięcie kosza po napisie „Usunąć?” usuwa), pusty stan jednym zdaniem; okno nazwy z polem `MField`. Tablet: narysuj wzór, zaznacz lassem → przytrzymaj → „Zapisz jako naklejkę…”; w innym zeszycie „⋮” → „Naklejki…” → wstaw, przesuń; naklejka ze zdjęciem; usuwanie.
- **F20** zeszyt-przewodnik — `noc/przewodnik`; `GuideTest`. **Wygląd:** zwykły zeszyt w kratkę z okładką „Łuki” w zieleni Mint; tytuł strony 22 pt pogrubiony w akcencie, polecenia 11,5 pt grafitowe, wzory szare przerywane (do przerysowania) albo grafitowe (do starcia/zaznaczenia), na stronie 5 osie z przerywanym sinusem. Tablet: Biblioteka → Nowy → „Zeszyt-przewodnik”; przejść strony i wykonać polecenia; sprawdzić czytelność i łamanie akapitów (wysokość pól liczona szacunkowo — po edycji pole się przelicza); świeża instalacja: przewodnik pojawia się sam.
- **F21** podwójne dotknięcie (edycja punktów) i przytrzymanie uchwytu (menu punktu) — `noc/punkty`; `PointEditTest`. **Wygląd:** bez nowych ekranów — dymek z podpowiedzią po wejściu w edycję punktów, menu punktu to ten sam `DropdownMenu` co menu kontekstowe, w miejscu uchwytu. Tablet: narysuj koło z przytrzymaniem, zaznacz lassem, dotknij dwa razy szybko → kotwice i uchwyty; przytrzymaj kotwicę → „Ostra kotwica”; prostokąt → podwójne dotknięcie → wierzchołki, przytrzymaj wierzchołek → „Usuń punkt” (trójkąt); kreska odręczna → podwójne dotknięcie → krzywa. Czas podwójnego dotknięcia 350 ms (stała `DOUBLE_TAP_MS`).
- **F22** przyczep do figur — `noc/przyczep`; `AttachTest`. **Wygląd:** `ToggleRow` w menu „⋮” pod „Okno powiększenia”; w trakcie przeciągania kółko 9 dp w kolorze akcentu z kropką w środku w miejscu przyczepienia. Tablet: włącz w „⋮”, narysuj trójkąt (przytrzymaj), potem prostą z przytrzymaniem od wierzchołka trójkąta do środka koła — końce wskakują; przeciągnij wierzchołek łamanej do innej kreski; wyłącz i sprawdź, że nic nie przyciąga.
- **F23** uporządkuj obrazy — `noc/przyczep`; `ImageArrangeTest`. **Wygląd:** jedna pozycja w menu kontekstowym zaznaczenia. Tablet: wstaw 3–5 zdjęć, rozrzuć je i obróć, zaznacz lassem → przytrzymaj → „Uporządkuj obrazy”; cofnij.
- **F24** (zdjęcia tablicy → linie) to kreda, etap 4 (agent B).
- **F25–F27** maks. zoom, powrót do pisania, wyjustowanie — `noc/opcje`; `SmallOptionsTest`. **Wygląd:** suwak i przełącznik (z jednym zdaniem objaśnienia) w Ustawieniach → Wejście; przycisk wyrównania (ikona Material jak w oknie tekstu) na pasku zaznaczenia obok „Edytuj tekst”. Tablet: ustaw ×12 i przybliż maksymalnie; włącz powrót do pisania, wybierz lasso → dotknij lasso ponownie (wraca pióro), wybierz rękę → dotknij pustego miejsca; zaznacz pole tekstu → przycisk wyrównania 4 razy (w tym wyjustowanie długiego akapitu).
- **F28** auto-korekta √ ∑ ∫ — `noc/symbole`; `MathGlyphsTest`. **Wygląd:** w panelu pióra (dotknij aktywnego pióra) pod przełącznikiem prostowania nagłówek „Auto popraw po przytrzymaniu” i trzy chipy jak w panelu lassa. Tablet: napisz √ z kreską u góry i przytrzymaj (wyrównuje się w tym samym miejscu); ∑ jednym pociągnięciem; włącz ∫ i narysuj całkę z haczykami (wygładza) oraz prostą ukośną (zostaje linią); sprawdzić, czy zwykłe pismo z przytrzymaniem nie zamienia się przypadkiem w √/∑. Stan po wszystkich F: 614 testów, 1 pominięty, 0 błędów, APK debug buduje się.
- **F4** dwa zeszyty w jednym obszarze — `noc/dwa-zeszyty`; `DualRowsTest`. **Projekt:** zamiast jednego wspólnego płótna z dwiema listami stron (duża przebudowa renderera kafelkowego) dwa niezależne widoki płótna jeden nad drugim, oba w układzie poziomym — wygląda i działa jak dwa rzędy z rysunku (osobne przesuwanie), a renderer zostaje bez zmian. **Wygląd:** rozdzielacz 40 dp w kolorze pasków (`m.chrome`) z uchwytem, nazwą zeszytu i małymi przyciskami jak na pasku; okno wyboru (`MDialog`) z listą zeszytów (kolor okładki, tytuł, liczba stron). Tablet: „⋮” → „Drugi zeszyt poniżej…” → wybierz; przewiń każdy rząd osobno; przeciągnij rozdzielacz; pisz w obu rzędach (to samo pióro); cofnij dolny z rozdzielacza; włącz/wyłącz wspólne powiększenie i zrób szczypnięcie w jednym rzędzie; zamknij ✕ i sprawdź, że górny zeszyt wrócił do swojego układu; pamięć przy dwóch dużych PDF-ach. **Pytanie 7 z WSPOLNE-USTALENIA:** wspólne powiększenie jest przełącznikiem (domyślnie wspólne).
- **L1** konstrukcje — `noc/licencjat`; `ConstructTest`, `ConstructOpsTest`. **Wygląd:** sekcja „Konstrukcja” w menu kontekstowym zaznaczenia (nagłówek drobnym szarym tekstem między liniami), wynik w kolorze aktywnego pióra, kreski pomocnicze przerywane. Tablet: narysuj trójkąt z przytrzymaniem → zaznacz → przytrzymaj → „Okrąg opisany”, „Wysokości i ortocentrum”; dwie proste → „Punkt przecięcia”; prosta + rysunek → „Odbij względem prostej (kopia)”. **Decyzja:** wejścia z zaznaczenia zamiast trybu krok po kroku; zapisane do potwierdzenia.
- **L2** grecki i indeksy — `noc/licencjat`; `MathTextTest`. **Wygląd:** bez nowego UI (przełącznik w Ustawienia → Wejście). Tablet: narzędzie Tekst, wpisz „\alpha + \beta = 90°”, „x^2 + y^2 = r^2”, „a_{n+1}”, „plik_nazwa.txt” (ma zostać); kursor po zamianie na końcu.

### Pomysły Fable (gałąź `noc/fable`)
- **1 Tryb wykładu** — `LecturePagesTest`. **Wygląd:** kapsuła `FloatingCard` u dołu płótna (napis „Tryb wykładu”, chip z aparatem, ✕), nagłówek strony szarym 11 pt. Tablet: „⋮” → „Tryb wykładu…” → zdjęcie → nowa strona; drugie zdjęcie z kapsuły; ✕; cofnij; sprawdzić aparat Samsunga (zgoda na zapis do pliku aplikacji).
- **2 Paleta z historią** — `CommandRankTest`. **Wygląd:** bez zmian w oknie palety, inna kolejność wyników. Tablet/klawiatura: Ctrl+K, użyj „Układ współrzędnych”, otwórz paletę ponownie — jest na górze; wpisz „zrodlo”, „wsprz”.
- **10 Gest dwoma palcami cofnij / trzema ponów** — już istniał (`input.two_finger_undo`, Ustawienia → Wejście); bez zmian.
- **3 Mini-mapa** — `MiniMapTest`. **Wygląd:** prostokąt strony 120×150 dp maks. w prawym dolnym rogu, lekki cień, ramka włosowa, widok jako półprzezroczysty prostokąt w kolorze akcentu z obwódką. Tablet: przybliż mocno (≥ 2,5×) — mini-mapa się pojawia; przeciągnij po niej; sprawdzić, czy nie przeszkadza w pisaniu przy prawym dolnym rogu (wtedy wyłączyć w Ustawieniach albo zgłosić).
- **11 Zdjęcie do kratki** (opcja) i **12 Przyklej jako tło** — `GridFitTest`. Tablet: włącz opcję, wstaw zdjęcie na stronie w kratkę (krawędzie na liniach); zaznacz obraz → przytrzymaj → „Przyklej do strony jako tło” → pisz po nim, lasso go nie łapie; cofnij.
- **9 Papier milimetrowy** — `MillimeterTemplateTest`. Tablet: „Styl strony” → Wzór „Milimetrowy”; czy cienkie linie nie są za ciemne (kolor linii wzoru jak w kratce) i czy przewijanie jest płynne przy dużym zoomie.


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

## Faza 2 — agent B (od 27.10, gałąź `noc/lab2` od `noc/wszystko`, lista B2-* w `docs-noc/PLAN-NOCY.md`)

Gradle tylko w worktree `mNotes-lab`; `source .toolchain/env.sh` jest w tej sesji zablokowane, więc te same zmienne
podaję przez `env JAVA_HOME=… ANDROID_HOME=… GRADLE_USER_HOME=… ./gradlew …`. Testy:
`./gradlew --offline :app:testCoreDebugUnitTest :app:testLabDebugUnitTest :app:assembleCoreDebug :app:assembleLabDebug`.
Na starcie (`noc/wszystko` @ `471ed08`): lab 696 testów, 0 porażek.

- **B2-5 kreda K1 — poprawki z przeglądu** (+ część B2-6 i B2-7, bo dzielą ten sam przebudowany potok):
  - `Lab.loadRaster`: obrót z EXIF (jak `Assets.decodeFile`) i dekodowanie od razu do ≤ 2400 px (`core/image/DecodePlan`:
    `inSampleSize` + skalowanie gęstością w dekoderze). Nie użyłem samego `Assets.decodeFile`, bo próbkuje tylko potęgą
    dwójki: 12 Mpx (4000 px) wychodziło w pełnym rozmiarze i dopiero `Raster.fit` je zmniejszało (trzy pełne klatki w
    pamięci). Teraz w szczycie: jedna klatka próbkowana w dekoderze + wynik 2400 px.
  - Potok podzielony: `BoardPipeline.prepare` (prostowanie, kadr, wyrównanie, wyostrzenie → `BoardSource`) i `finish`
    (kolor do alfy, gumka). Podgląd liczy `prepare` tylko po zmianie przełączników kroków albo narożników, 60 ms po ostatniej
    zmianie; suwaki progu, krycia i koloru wołają tylko `finish` (auto-próg policzony raz na źródło).
  - Ciężka praca na `Dispatchers.Default` (własny zakres `Lab.work`), a nie na jednowątkowym `repo.io`, które zapisuje zeszyt.
  - `Chalk.autoCut` bez listy pudełek `Integer` (pętla po `IntArray`); mediana w progu tylko po wyrównaniu oświetlenia
    (bez niego próg = K·σ jak w `kreda.py`).
  - **UI (wygląd):** nad podglądem chipy „Wynik / Narożniki / Gumka” (ten sam `Choice` co w ustawieniach). „Narożniki”:
    zdjęcie z czworokątem tablicy w kolorze akcentu i 4 kółkami (półprzezroczyste koło 14 dp + kropka 5 dp); kółko łapie
    się w promieniu 36 dp, dotyk dalej przewija panel; po puszczeniu przelicza się wynik; „Narożniki automatycznie”
    wraca do zgadniętych. „Gumka”: palec rysuje pętlę na wyniku (linia akcentu 2 dp), po puszczeniu obszar znika;
    narysowane pętle widać jako przerywany obrys; „Cofnij gumkę”, „Wyczyść gumkę (n)”. Pod podglądem jedno zdanie
    podpowiedzi dla trybu. Szachownica pod przezroczystością w kolorach motywu (`canvasBg` + `line`), w ciemnym motywie ciemna.
  - Po wstawieniu zdjęcia (wariant lab) sprawdzenie w tle na 320 px (`looksLikeDarkBoard`); dla ciemnej tablicy raz dymek
    „Wygląda jak tablica: różdżka „Oczyść tablicę” w menu zdjęcia zostawi samą kredę (podpowiedź raz; Ustawienia →
    Laboratorium)”; przełącznik „Podpowiedź po wstawieniu zdjęcia tablicy” w Ustawienia → Laboratorium → Kreda. **Zmiana w
    `src/main` (dla A przy scaleniu):** `LabFeatures.imageInserted(ed, el)` z pustą domyślną implementacją i jedno wywołanie
    `Flavor.lab?.imageInserted(this, el)` w `Editor.insertImage` (po zaznaczeniu wstawionego obrazu); core bez zmian zachowania.
  - **Nauka:** zdjęcie tablicy w notatce bez przepisywania; narożniki i gumka, gdy automat się pomyli (ręka, rama, odblask).
  - Test: lab 707 (było 696), core 667, 0 porażek; oba APK (`45fd396`, `dc22233`). **Tablet:** zdjęcie pionowe z telefonu →
    różdżka → podgląd stoi pionowo; suwak progu chodzi płynnie (bez przeliczania prostowania); „Narożniki” → przeciągnij
    kółko → „Wynik”; „Gumka” → obwiedź rękę wykładowcy → znika; 12 Mpx → „Zastosuj” nie zamraża zapisu zeszytu; wstaw
    zdjęcie tablicy → raz dymek; ciemny motyw → szachownica ciemna.
- **B2-6 wektoryzacja K2** (`45fd396` mapowanie, `a2256eb` porządki):
  - Kreski leżą na kredzie: punkt łamanej → `BoardSource.toPhoto` (kadr, odwrotność prostowania, ułamki zdjęcia) →
    `ChalkStrokes.place` (wycinek obrazu w zeszycie, także obrócony, odbicie, obrót elementu) → strona. Punkty poza widocznym
    wycinkiem przycinane na krawędzi (bisekcja), grubość z lokalnej skali. Geometria w `core/image/ChalkStrokes.kt`
    (`ChalkStrokesTest`), `Lab.toStrokes` usunięte.
  - Fragmentacja: sąsiedztwo m w śledzeniu szkieletu (schodek to nie rozgałęzienie), ostrogi krótsze niż grubość w węźle
    znikają, węzły połączone krótkim mostkiem to jedno skupisko, w skupisku ścieżki łączą się parami po kącie (< 40°), przy
    dwóch zawsze; grubość każdej łamanej z transformaty odległości (2·średnia − 1). Testy: linia 45° (3 grubości) ≤ 2 łamane,
    pierścień 1–2, krzyż ≤ 2 (było 4 i 4), paski 3 i 11 px mają 3 i ~11 (było 7,8 dla obu).
  - **Prawdziwe zdjęcia** (`RealPhotoTest`): czyta `KREDA_DIR` (pominięty bez niej; zdjęć nie ma w repo), 4 pierwsze z
    `kreda/blackboard/` na 1000 px: kreda 4–7 % widocznych pikseli, rozrzut tła po wyrównaniu 0,08–0,16, 180–329 kresek,
    **94,5–99,6 % punktów kresek trafia w jasną kredę zdjęcia; dawne mapowanie 28–51 %** (asercja dokumentuje błąd K2).
    IoU z wynikami `kreda.py` pominięte: w `kreda/` nie ma PNG wyników dla tych zdjęć (tylko `example.png` 320×114).
    Uwaga: na dwóch zdjęciach auto-próg dochodzi do górnej granicy 0,50 (szum po wyrównaniu) — do sprawdzenia na tablecie,
    czy słaba kreda nie ginie. Uruchomienie: `KREDA_DIR=/home/piotr/Projects/Marcin/kreda/blackboard ./gradlew --offline
    :app:testLabDebugUnitTest --tests '*RealPhotoTest*'`.
  - Test: lab 712, core 667, 0 porażek. **Tablet:** zdjęcie tablicy → różdżka → „Edytowalne kreski” → kreski na kredzie
    (także po przycięciu zdjęcia w zeszycie); litery O i krzyżyki jako 1–2 kreski; grube i cienkie linie różnej grubości.
- **B2-7 mediana K3** (w `45fd396`): `BoardPipeline.prepareMany(count, opt) { load(i) }` wczytuje zdjęcia po jednym i trzyma
  tylko wyprostowane klatki; zdjęcie bez znalezionej tablicy (narożniki = cały kadr), gdy na innych ją znaleziono, wypada
  (`skipped`; w panelu „bez tablicy, pominięte: 2”, po „Zastosuj” dymek); bez tablicy na wszystkich — wszystkie całe jak
  przy wyłączonym prostowaniu; wynik i kreski odnoszą się do zdjęcia odniesienia (pierwszego z tablicą). Test
  `photoWithoutABoardIsSkippedInsteadOfGhosting`, `failedLoadGivesNull`. **Tablet:** zaznacz 3 zdjęcia, w tym jedno ściany →
  „Połącz zdjęcia tablicy” → informacja o pominięciu, bez „duchów”.

- **B2-8 K4 „Wyślij do mLabs”** (`0befd4c`): `docs/INTEGRACJA-MLABS.md` skopiowany do repo (i uaktualniony w `docs-noc/`),
  `Recognizer.kt` odsyła do niego. Polecenie „Wyślij stronę do mLabs…” (menu Plik i paleta Ctrl+K, tylko lab): okno `MDialog`
  z jednym zdaniem, co pójdzie (strona N zeszytu „X” jako PNG i JSON w formacie mLabs, przykłady pytań), polem `MField`
  „Tekst strony (opcjonalnie)” i zdaniem, że rozpoznawania jeszcze nie ma; „Udostępnij…” renderuje bieżącą stronę do PNG
  1600 px (papier, wzór, tło PDF, treść — jak wycinek F14) i JSON `mnotes-strona` v1 (`PageExport.userBlock`: tekst jako
  jeden blok `silnik: uzytkownik`, pewność 1) do `cache/share/mlabs/` i otwiera wybór aplikacji (`ACTION_SEND_MULTIPLE`,
  `FileProvider`, dodatek `com.mdev.mnotes.extra.PAGE`). **Odstępstwo:** pliki w `cache/share`, nie w `files/` — tylko ten
  katalog udostępnia istniejący `FileProvider`, a nie chciałem zmieniać `file_paths.xml` w core. **Zmiana w `src/main`
  (dla A):** `LabCommand` i dwie metody z pustą domyślną implementacją w `LabFeatures` (`commands`, `Overlay`), dopisanie
  `Flavor.lab?.commands(ed)` w `commandList` (`EditorDialogs.kt`) i `Flavor.lab?.Overlay(ed)` obok panelu telefonu
  (`EditorScreen.kt`). Test: `RecognizerTest.userTextBecomesOneSureBlockAndEmptyTextNone`; lab 713, core 667, 0 porażek,
  oba APK. **Nauka:** strona z wykładu trafia do mLabs, które ją sprawdza albo układa pytania kontrolne. **Tablet:** Plik →
  „Wyślij stronę do mLabs…” → wpisz `v = s/t` → „Udostępnij…” → np. Pliki → dwa pliki: PNG strony i JSON z blokiem.

- **B2-9 Nearby K5 — bezpieczeństwo i błędy, etapy A i B** (`82ca30c`; etap C — ekran synchronizacji zeszytów — nie
  zrobiony, bo FAZA 2b każe przerobić Nearby od nowa):
  - Parowanie commit-then-reveal (`core/nearby/PairingSession`, protokół `mnotes-para:2:`): po połączeniu każda strona
    wysyła tylko zobowiązanie SHA-256 swojej oferty (klucz publiczny, 16-bajtowy nonce, nazwa); ofertę dopiero po
    zobowiązaniu drugiej strony; oferta niezgodna ze zobowiązaniem albo bez niego zrywa parowanie. SAS = HKDF z klucza pary
    i obu nonce. Napastnik w środku musi się zobowiązać, zanim pozna nonce ofiary: 1 szansa na 10⁶ na próbę zamiast
    dobierania klucza w tle. Testy: uczciwa para (te same cyfry i klucz), oferta wychodzi dopiero po zobowiązaniu,
    podmieniony klucz i zmieniona nazwa odrzucone, oferta bez zobowiązania odrzucona, napastnik z własnymi kluczami daje
    różne cyfry. Wersja 1 parowania nie jest przyjmowana (obie aplikacje muszą mieć tę wersję). Komentarz w
    `SyncCrypto.kt` mówi teraz prawdę.
  - **Świadoma decyzja: brak tajności w przód.** Klucz pary jest stały (zaszyfrowany kluczem z Keystore); kto go wykradnie
    z urządzenia, odczyta nagrane wcześniej ramki. Dla zdjęć z wykładu między własnymi urządzeniami to rozsądna cena.
  - Okna: „Następne zdjęcie” wysyła od razu (wcześniej czekało na `onUp`, który już minął); para zapisana, ale nie do
    odczytu → `PairStore.loadOrForget` czyści ją i okna mówią „Para wygasła…”, z przyciskiem „Sparuj telefon”;
    przybliżona lokalizacja nie jest odmową (`LinkPermissions.granted`, test); kolejne zdjęcia z telefonu schodkami co
    24 pt; `PairStore.save` w `runCatching` z komunikatem; w Ustawienia → Laboratorium zdanie, czemu Android pyta o
    lokalizację. Test: lab 719, core 673, 0 porażek, oba APK. **Tablet + telefon:** sparuj (nowa wersja na obu), te same
    cyfry; wyślij 3 zdjęcia jedno po drugim przez „Następne zdjęcie” → trzy obrazy schodkami.

### FAZA 2b (uwagi Marcina z PDF, pierwszeństwo)

- **2b-B1 Nearby od nowa** (`96f7796` projekt, `fe5acff`, `9387024`, `3d9ccd1`). Najpierw `docs/NEARBY-PRZEBUDOWA.md`
  (diagnoza z kodu i ze stałych odczytanych z jara 19.3.0, projekt, kolejność, jak sprawdzić). **Najważniejsze przyczyny
  „nie działa”:** (1) okno parowania z Ustawień startowało z rolą „telefon” **na obu** urządzeniach, więc oba szukały i
  nikt się nie ogłaszał; (2) kod 8025 (wyłączona *usługa* lokalizacji) był pokazywany jako „Nearby nie wystartowało…
  Usługi Google Play?” i ponawiany w kółko; (3) przybliżona lokalizacja nie wystarcza (notatka z działającego mChochlika),
  a B2-9 ją przepuszczał — wycofane; (4) brak limitów czasu i postępu, zdjęcie w 100+ porcjach po 30 KB, choć limit
  BYTES to 1 047 552 B.
  - `core/nearby/LinkMachine` (+ `LinkTransport`): cała logika łącza z testami na JVM (`FakeAir`: dwa końce w jednym
    teście, gubienie ramek, zerwanie): role z czynności (odbiór = tablet się ogłasza, wysyłanie = telefon szuka; w
    parowaniu domyślnie z rozmiaru ekranu ≥ 600 dp, zmiana jednym stuknięciem), lista znalezionych w parowaniu (nazwa
    punktu `mnp|<nazwa urządzenia>`, jedyne znalezione wybiera się samo po 1 s), parowanie z zobowiązaniem (B2-9),
    `hello` przed danymi, limity: szukanie 30 s → lista „co sprawdzić”, łączenie 20 s i zabezpieczanie 10 s → od nowa,
    błędy ustawień/uprawnień bez pętli ponowień (`LinkErrors.explain` — zdanie „co zrobić” dla każdego kodu), inne
    ponowienie po 3 s, dziennik 20 zdarzeń. Zdjęcie: `PhotoSender`/`PhotoReceiver` — części 256 KB, okno 2, potwierdzenie
    każdej, ponowienie po 15 s (4 próby), wznowienie po zerwaniu od pierwszej niepotwierdzonej, SHA-256 całości,
    bez podwójnego wstawienia. Porcja `LinkChunks` 900 000 B (część = jedna ramka).
  - Lab: `NearbyTransport` (cienki, 1:1 `ConnectionsClient`), jedno okno `PhoneLinkDialog` (Połącz / Zdjęcie z telefonu /
    Wyślij zdjęcie) i `DiagnosticsDialog`. **Wygląd:** `MDialog`; u góry ikona stanu (Bluetooth, szukanie, kłódka, ✓,
    ⚠) w kolorze akcentu i zdanie stanu w `titleMedium`; pod nim jedna rzecz: przyciski z nazwami znalezionych tabletów,
    sześć cyfr `displayMedium` w `accentText`, pasek postępu w kolorze akcentu („Wysyłam zdjęcie · część 2 z 3 · 64 %”),
    albo lista „co sprawdzić”; zdanie „To urządzenie: tablet (odbiera) · zmień” w parowaniu; na dole „Diagnostyka”.
    Przed pierwszą prośbą systemową ekran „Łącze potrzebuje zgód Androida” z jednym zdaniem na zgodę, po odmowie
    „Ustawienia aplikacji”. Diagnostyka: ✓/✗ dla każdej zgody, Bluetooth, Wi‑Fi, usługi lokalizacji, Usług Google Play,
    wersja Androida, ostatnie zdarzenia z kodami, „Kopiuj raport”. Bez pary „Zdjęcie z telefonu”/„Wyślij” najpierw paruje
    w tym samym oknie. Ustawienia → Laboratorium: „Połącz telefon z tabletem”, „Wyślij zdjęcie…”, „Diagnostyka łącza”.
  - Test: 41 testów `core/nearby` (w tym 9 maszyny), lab 739, core 693, 0 porażek, oba APK. **Nie zrobione jeszcze:**
    ekran synchronizacji zeszytów (ostatnia część 2b-B1) — po 2b-B2 i 2b-B3, bo wymaga warstwy danych zeszytu, a kreda ma
    priorytet; wysyłanie w tle po zamknięciu okna (okno musi zostać otwarte).
  - **Krok 4: zeszyty przez łącze** (`24a3692`). Ustawienia → Laboratorium (po sparowaniu): „Wyślij zeszyt na drugie
    urządzenie” (okno `MDialog` z listą zeszytów od ostatnio zmienionych; po stuknięciu „Przygotowuję „X”…”, potem to
    samo okno łącza ze zdaniem „Wysyłam zeszyt · część 3 z 12 · 25 %”) i „Odbierz zeszyt z drugiego urządzenia” („Czekam
    na „Pixel 8”…”, po odbiorze „Dodano zeszyt „X” do biblioteki”). Kierunek dowolny: kto wysyła, ten szuka; kto odbiera,
    ten się ogłasza. **Świadomie wersja 1 bez scalania stron:** zeszyt idzie jako `.mnotes` (ten sam `Exporter`/`Importer`
    co „Eksport i udostępnianie” i import), odbiorca dodaje go jako **nowy** zeszyt — nic nie jest nadpisywane, więc żadna
    strona nie może zginąć. Scalanie stron („nowsza wygrywa”, `SyncPlan` w `core/nearby` już jest) wymaga zapisu do
    otwartego zeszytu przez `NotebookDoc` i testów na dwóch kopiach — zostawione na później. Limit 64 MB (zeszyt z dużym
    PDF-em → podpowiedź „Eksport i udostępnianie”). Test: symulacja zeszytu 3 MB przez łącze (`notebookFileGoesTheSameWay…`),
    lab 786, core 732, 0 porażek, oba APK. **Tablet + telefon:** wyślij mały zeszyt z telefonu, na tablecie „Odbierz
    zeszyt” → zeszyt w bibliotece, strony i obrazy jak w oryginale.
  - **Nauka:** zdjęcie tablicy z telefonu w notatce na tablecie, bez kabla i bez sieci; notatki z telefonu na tablecie.
  - **Tablet + telefon (oba `labDebug` z tej gałęzi):** `docs/NEARBY-PRZEBUDOWA.md` §5 (parowanie z rolami ustawionymi
    samymi, lokalizacja wyłączona → zdanie „Włącz lokalizację…”, 3 zdjęcia z postępem, Bluetooth wyłączony w połowie →
    wznowienie, Diagnostyka → „Kopiuj raport” i wklej Marcinowi, jeśli coś nie działa).

- **2b-B2 kreda — pomiary i poprawki** (`917a60d`, `2067ec1`; raport `docs/KREDA-POMIARY.md`). Pomiar na **24**
  zdjęciach z `kreda/blackboard/` (plan mówił o 10; biorę wszystkie) przed i po: udział kredy, liczba kresek, średnia
  długość, fragmenty < 10 px, próg, czas (`KredaPomiaryTest`, `KREDA_DIR`; `KREDA_OUT` zapisuje podglądy do oczu).
  Główne znalezisko: listwa między tablicami, rama i kawałki ściany szły jako kreda (do 14,8 % pikseli). Nowe kroki,
  każdy z przełącznikiem (panel i Ustawienia → Laboratorium → Kreda): „Usuń listwy, ramy i ścianę” (pasy grubsze niż
  kreda i długie ≥ 8 % boku; zwarte pogrubione litery zostają — pierwsza wersja bez tego warunku je zjadała, widać to
  było na podglądzie), „Dociągaj bladą kredę do wyraźnej” (histereza: blade piksele połączone z wyraźnymi zostają),
  „Usuń drobinki (ziarno tablicy)”; wektoryzacja łączy przerwane kreski po kierunku. Wynik: kreda % 6,6 → 3,9 (pismo
  zostaje, sprawdzone na podglądach), kresek 485 → 468, fragmenty 21 → 19 %, czas bez zmian. **Nie poprawione:** próg
  globalny dobija do 0,50 na 9/24 zdjęć (próg lokalny odłożony — w pustych miejscach wyciągałby smugi, a bez zdjęcia z
  okładki PDF nie umiem tego uczciwie sprawdzić; prośba o to zdjęcie w `kreda/blackboard/`), `guessQuad` obejmuje ścianę
  na zdjęciach z dużym kawałkiem ściany (obejście: tryb „Narożniki”). Test: lab 747, core 693, 0 porażek; `RealPhotoTest`
  z `KREDA_DIR` dalej ≥ 94 % punktów kresek na kredzie. **Tablet:** zdjęcie dwóch tablic z listwą → różdżka → listwa
  znika, wzory zostają; pogrubiony napis zostaje; wyłącz kolejno trzy nowe przełączniki i porównaj podgląd.
- **2b-B3 „Zdjęcie tablicy” w lab** (`bac8564` scalenie `noc/wszystko` z hookiem A2-8, `3feab65`). `Lab.onLecturePhoto`:
  zdjęcie z trybu wykładu, jeśli to ciemna tablica (`looksLikeDarkBoard`), przechodzi w tle przez kredę z krokami z
  Ustawień; plik obrazu podmienia się jako **osobna edycja** (obraz zostaje w tym samym miejscu i szerokości), więc strona
  od razu pokazuje wynik (podgląd), a jedno cofnięcie (↶) wraca do zdjęcia — dymek mówi to wprost. Zdjęcia, które nie są
  tablicą (kartka, slajd), zostają bez zmian; zdjęcie przesunięte albo usunięte w trakcie liczenia też nie jest ruszane.
  Przełącznik „Zdjęcie z trybu wykładu od razu przez kredę” w Ustawienia → Laboratorium → Kreda (domyślnie włączony).
  **Ikona** dla 2b-A14: `ui/BoardCameraGlyph.kt` (`BoardCameraIcon`) — tablica z półką i falką kredy, w prawym dolnym rogu
  aparat z obiektywem; te same role i grubości co glify piór i gumki z 2b-A1 (`GlyphCanvas`), jednobarwna; test siatki
  24×24. **Nie podpięta do paska** — to część A (2b-A14: jeden przycisk „Zdjęcie tablicy” i nowa ikona wstawiania
  obrazu); nowy plik, więc scalenie bez konfliktów. Test: lab 785, core 731, 0 porażek, oba APK. **Nauka:** zdjęcie
  tablicy na wykładzie od razu jako czysta kreda w notatce. **Tablet:** „⋮” → „Tryb wykładu…” → zdjęcie tablicy → po
  chwili na nowej stronie sama kreda i dymek; ↶ → oryginalne zdjęcie; zdjęcie kartki → bez zmian.

- **B2-11 testy z przeglądu §3.5a i przegląd własny:** (1) zgodność kresek ze zdjęciem — `ChalkStrokesTest` i
  `RealPhotoTest` (94–100 % punktów na kredzie); (2) regresja fragmentacji — `ChalkVectorTest` (45°, pierścień, krzyż,
  różne grubości, przerwy); (3) IoU z wynikami `kreda.py` — **niewykonalne tutaj**: w `kreda/` nie ma PNG wyników dla
  zdjęć z `blackboard/`, a `kreda.py` wymaga OpenCV/numpy, których w tym środowisku nie ma (bez pip). Zamiast tego
  `KredaPomiaryTest` (24 zdjęcia, podglądy do oczu). Przegląd §3.5: K0 wyszukiwanie „Laboratorium” zrobił A (A2-10); K1–K5
  — wszystkie pozycje „Co poprawić” zamknięte w B2-5…B2-9 i 2b-B1…B3 (poza scalaniem stron w synchronizacji).

### Co Marcin ma sprawdzić na tablecie — wariant lab, po fazie 2 (kolejność)

Instalacja: `labDebug` z `noc/lab2` (albo `noc/wszystko` po scaleniu przez A) na tablecie **i** na telefonie (Nearby).

1. **Kreda, jedno zdjęcie:** wstaw zdjęcie tablicy zrobione pionowo telefonem → raz dymek „Wygląda jak tablica…” → różdżka
   → podgląd stoi pionowo, suwak progu chodzi płynnie → „Narożniki”: przeciągnij kółko → „Wynik” → „Gumka”: obwiedź rękę →
   „Zastosuj” (zeszyt w tym czasie dalej się zapisuje) → ↶ wraca do zdjęcia.
2. **Listwy i ramy:** zdjęcie dwóch tablic z listwą → listwa i ściana znikają, wzory zostają; wyłącz kolejno „Usuń listwy…”,
   „Dociągaj bladą kredę…”, „Usuń drobinki” i porównaj podgląd.
3. **Kreski:** „Edytowalne kreski” → kreski leżą na kredzie także po przycięciu zdjęcia w zeszycie; litera O i krzyżyk to
   1–2 kreski; grube i cienkie linie mają różną grubość.
4. **Mediana:** 3 zdjęcia tej samej tablicy (+ jedno zdjęcie ściany) → „Połącz zdjęcia tablicy” → informacja o pominięciu,
   wykładowca znika, bez „duchów”.
5. **Tryb wykładu:** zdjęcie tablicy → na nowej stronie po chwili sama kreda + dymek; ↶ → zdjęcie; zdjęcie kartki bez zmian.
6. **Nearby (dwa urządzenia):** `docs/NEARBY-PRZEBUDOWA.md` §5 — parowanie (role same, te same 6 cyfr), lokalizacja
   wyłączona → zdanie „Włącz lokalizację…”, 3 zdjęcia z postępem, Bluetooth wyłączony w połowie → wznowienie,
   „Diagnostyka” → „Kopiuj raport”; potem „Wyślij zeszyt” / „Odbierz zeszyt”.
7. **Wyślij do mLabs:** Plik → „Wyślij stronę do mLabs…” → dwa pliki (PNG i JSON) w wybranej aplikacji.
8. **Core:** `coreDebug` — brak różdżki, ikony telefonu, „Laboratorium” i polecenia „Wyślij do mLabs”.

## Faza 2 — agent A (od 27.10, lista A2-* w `docs-noc/PLAN-NOCY.md`)

Każdy punkt na własnej gałęzi `noc/f2-<temat>` od `noc/wszystko`, po zamknięciu scalony do `noc/wszystko`.
Testy: `./gradlew --offline :app:testCoreDebugUnitTest :app:testLabDebugUnitTest :app:assembleCoreDebug :app:assembleLabDebug`.
Środowisko: `source .toolchain/env.sh` jest w tej sesji zablokowane przez uprawnienia, ale zmienne (JDK, SDK, GRADLE_USER_HOME)
dziedziczy już proces z `start-A.sh`, więc `./gradlew` działa bez niego.

- **A2-1** `x^10` → x¹⁰ — `noc/f2-indeksy`; `MathTextTest` (`multiDigitRuns`, `finalFlush`). Cyfry (z opcjonalnym znakiem
  `+`/`-` na początku) po `^`/`_` czekają na koniec ciągu. **Odstępstwo od planu (świadome):** po `^` ciąg kończy też litera
  (`x^2y ` → x²y, jak przed poprawką), po `_` tylko nielitera (zgodnie z planem; `plik_2a.txt` zostaje) — wykładnik przed
  literą to typowy wzór, a podkreślenie z cyfrą przed literą typowa nazwa pliku. Dodatkowo „Gotowe” w oknie tekstu kończy
  znacznik na samym końcu tekstu (inaczej `x^10` wpisane na końcu i zatwierdzone zostałoby surowe). Przy okazji: `x^-1 `
  dawało x⁻1, teraz x⁻¹. Testy: core 659, lab 688, 0 błędów. **Tablet:** Tekst → wpisz `x^10 + a_12 = 10^-3` i „Gotowe” →
  `x¹⁰ + a₁₂ = 10⁻³`; `plik_2a.txt` zostaje bez zmian.
- **A2-2** mini-mapa nie zjada rysika — `noc/f2-minimapa`; `MiniMapTest` (krycie w czasie, dotyk tylko palcem). Zrobione oba
  warianty z planu naraz: mapa jest tylko chwilę po przewinięciu/zoomie (1,2 s + 180 ms wygaszania, jak pasek przewijania),
  znika od razu przy dotknięciu rysikiem, a reaguje tylko na palec (nie na dłoń odrzuconą przez „Odrzucanie dłoni”).
  Domyślnie wyłączona (`view.minimap = false`), dopóki Marcin nie sprawdzi na tablecie. **Wygląd:** jak dotąd prostokąt
  strony ≤ 120×150 dp w prawym dolnym rogu, ale cień i włosowa ramka tymi samymi kolorami co kartki na płótnie (wcześniej
  ostry ciemny prostokąt), widok jako półprzezroczysty prostokąt akcentu; pojawia się i gaśnie płynnie. Ustawienie
  przeniesione z „Wejście” do „Wygląd” („Mini-mapa strony po przewinięciu przy dużym powiększeniu (przesuwana palcem)”).
  **Tablet:** Ustawienia → Wygląd → włącz mini-mapę; przybliż ≥ 2,5× i przewiń palcem — mapa w rogu, po ~1 s gaśnie;
  przewiń i od razu pisz rysikiem w prawym dolnym rogu — mapa znika, kreska się rysuje; przewiń i przeciągnij palcem po
  mapie — widok jedzie.
- **A2-3** F18 „trzymaj dalej → krzywa” — `noc/f2-krzywa`; `BezierTest` (`sharpCornerBlocksHoldCurve`,
  `smoothStrokesAreCurveCandidates`). Domyślnie wyłączone (`input.hold_curve = false`), czas 350 ms bez zmian (razem
  ~0,9 s, a opcja i tak jest świadomie włączana). Nowe: kreska z narożnikiem ostrzejszym niż 60° (na odcinku 1/16 jej długości)
  nie jest wygładzana; wynik przy piórze nie jest zaznaczany (Fable §3.2: pasek zaznaczenia wyskakiwał w środku pisania);
  jednorazowy dymek „Kreska wygładzona w krzywą — wyłączysz to w Ustawieniach → Edytor i pióro” (plan pisał „Wejście”, ale
  opcja od początku siedzi w „Edytor i pióro” → „Pióro”). Strona 2 przewodnika przeredagowana. **Wygląd:** bez nowego UI.
  **Tablet:** Ustawienia → Edytor i pióro → włącz „Trzymaj dalej, aby wygładzić w krzywą”; narysuj łuk i trzymaj ~1 s →
  gładka krzywa (bez paska zaznaczenia), dymek tylko za pierwszym razem; napisz „V” albo „Z” i trzymaj → zostaje pismem;
  zaznacz krzywą lassem → kotwice i uchwyty jak przy narzędziu Krzywa.
- **A2-4** F28 fałszywe trafienia — `noc/f2-symbole`; `MathGlyphsTest` (`handwritingIsNeverCorrected`: 12 kształtów pisma
  × 5 wariantów drżenia, `rootNeedsALongBar`). Test najpierw pokazał dwa realne błędy: „v” z łącznikiem do następnej litery
  dawało √, a „ε” dawało ∫ (gdy ∫ włączona). Poprawki: kreska √ ≥ 0,5 szerokości (plan), kąty ukosów ∑ 25–70° i 110–155°
  (było 15–80°/100–165°), poziome kreski ∑ ≥ 0,45 szerokości, środkowy wierzchołek ∑ ≥ 0,3 szerokości od lewej, ∫ bez
  nawrotu w prawo (> 0,2 szerokości). Pozytywne testy (10 wariantów √ i ∑, ∫ z haczykami) bez zmian. Koszt: √ z bardzo krótką
  kreską (np. tylko nad jedną cyfrą, kreska < połowy szerokości znaku) zostaje pismem. Dymek po pierwszej zamianie
  „√ wyrównany — wyłączysz to w panelu pióra („Auto popraw”) albo w Ustawieniach” (raz, `math.hinted`). **Wygląd:** panel
  pióra bez zmian poza dopiskiem „(każdy osobno)” w nagłówku; w Ustawienia → Edytor i pióro → Pióro trzy nowe `ToggleRow`
  pod „Przytrzymaj, aby wyprostować kształt”. **Tablet:** √ z długą kreską i przytrzymaniem → wyrównany + dymek (tylko raz);
  napisz „ve”, „vo” odręcznie z przytrzymaniem na końcu „v” → zostaje pismem; Ustawienia → szukaj „pierwiastek”.
- **A2-5** F4 dwa zeszyty (zostają dwa płótna) — `noc/f2-dwa-zeszyty`; `DualRowsTest.handleMovesAloneWhileDragging`, reszta
  UI. Dolny rząd: `CtxMenuOverlay(ed2, openStyle = null, tools = ed)` i `PointMenuOverlay(ed2)` (teraz `internal`), okno
  „Zapisz jako naklejkę” dla dolnego zeszytu; „Zaznacz wszystko” przełącza narzędzie przez górny edytor (pasek jest wspólny),
  „Styl strony…” ukryte (panel stylu zna tylko górny zeszyt). Dymek raz na otwarcie dolnego zeszytu przy pierwszym
  zaznaczeniu. Rozdzielacz: `graphicsLayer.translationY` + `zIndex` w trakcie, `frac` dopiero w `onDragEnd` (anulowanie
  gestu wraca na miejsce). **Wygląd:** rozdzielacz jak dotąd (pasek `m.chrome` 40 dp), w trakcie przeciągania unosi się z
  cieniem 6 dp nad dolnym płótnem, płótna nie migają; okno wyboru przy braku innych zeszytów: zdanie „Nie ma innych
  zeszytów. Utwórz nowy — otworzy się poniżej…” i przycisk akcentu „Utwórz zeszyt”. **Tablet:** „⋮” → „Drugi zeszyt
  poniżej…” → zaznacz coś lassem w dolnym rzędzie → dymek; przytrzymaj zaznaczenie → menu (np. „Duplikuj”, „Okrąg opisany”
  dla trójkąta); przytrzymaj wierzchołek łamanej → menu punktu; przeciągnij rozdzielacz — płótna zmieniają wysokość po
  puszczeniu, bez szarpania; na świeżej instalacji z jednym zeszytem „Drugi zeszyt poniżej…” → „Utwórz zeszyt”.
- **A2-6** F11 odwrócone kolory — wydajność — `noc/f2-odwrocenie`; `ColorInvertTest.liveInkMatchesCommittedLook`.
  **Odstępstwo od planu (świadome):** plan mówił „odwracać kafelki w `PageCache` + test, że odwrócony kafelek jest
  cache’owany”. Zrobiłem wariant z przeglądu Fable (§3.2 F11): `PageCache.draw(…, inverted)` rysuje te same kafelki i bazę
  farbą z `ColorMatrixColorFilter` (dwie farby i jeden filtr tworzone raz na `PageCache`). Na GPU filtr w farbie to kilka
  instrukcji w shaderze; nie ma warstwy na klatkę ani drugiej kopii każdego kafelka (pamięć ×2 przy dwóch płótnach F4),
  przełączenie zostaje natychmiastowe, a łatanie kafelków i pipeta działają na oryginale jak dotąd. Nie ma więc „kafelka
  odwróconego” do cache’owania — test sprawdza za to kluczowe założenie drugiej zmiany: kreska w trakcie rysowana od razu
  odwróconym kolorem (`ColorInvert.liveInk`) daje ten sam piksel co kafelek po zatwierdzeniu, a dla kreski półprzezroczystej
  (macierz obcina nasycone kanały) już nie — dlatego zakreślacz i pióra z kryciem < 1 dalej rysują w trakcie przez warstwę.
  Warstwa została też dla obiektów przesuwanych lassem (tylko w trakcie przeciągania) i dla rysowania wektorowego, zanim
  powstaną kafelki. Mini-mapa (A2-2) pokazuje teraz stronę odwróconą. **Tablet:** PDF → Styl strony → Odwróć kolory (cały
  zeszyt) → zoom 3×, szybkie przewijanie (powinno być jak na zwykłej stronie, także w dwóch zeszytach F4); pisanie piórem
  czerwonym i niebieskim (kolor w trakcie = po puszczeniu), zakreślacz na odwróconej stronie; przesunięcie lassem.
- **A2-7** animacja stron 260 → 180 ms — `noc/f2-animacja`; `PageShiftTest.defaultDurationFollowsTheMotionRule`. Stałe
  w `PageShift` (domyślnie 180, suwak 100–400), `Settings.kt`: dziesięć komentarzy sklejonych w ogon linii `imageGridFit`
  (artefakt scalania) wróciło do swoich ustawień, a komentarz `pipetteKeep` siedział przy `pipetteOriginal`. Kto ustawił
  wcześniej czas ręcznie, zachowuje swój (suwak pokazuje najwyżej 400). **Tablet:** panel stron → „Dalej” przy widocznych
  sąsiednich stronach — przejazd krótszy, wyraźny, bez szarpnięcia.
- **A2-8** tryb wykładu — kolizje — `noc/f2-wyklad`; `BottomStackTest`. Kapsuła zostaje u dołu (16 dp), pasek szukania
  wchodzi nad nią (72 dp), a dymek nad oba (przy samym pasku szukania dymek też jest teraz nad nim — wcześniej 28 dp
  wypadało na pasku 14–64 dp). Chip akcji `selected = false`; błąd uruchomienia aparatu → `lectureMode = false` + dymek;
  etykieta w „⋮” „Tryb wykładu…” (paleta poleceń bez zmian: „Tryb wykładu: zdjęcie na nową stronę”).
  **Hook dla agenta B (B2-10):** `LabFeatures.onLecturePhoto(ed: Editor, pageId: String, image: ImageEl)` — metoda z
  domyślną pustą implementacją w `src/main/.../app/LabFeatures.kt`; `Editor.lectureShot` woła ją na wątku UI tuż po
  `exec(InsertPages)` i `goToPage`. Lab nadpisuje ją w `Lab.kt`: kreda w tle, potem jedna edycja podmieniająca obraz (np.
  `DocEdit.Elements` z nowym `ImageEl.asset`), więc cofnięcie wraca do oryginału. `Lab.kt` nie był ruszany.
  **Wygląd:** jak dotąd; kapsuła `FloatingCard` u dołu z chipem w kolorze neutralnym, pasek szukania i dymek wyżej, gdy
  kapsuła jest widoczna. **Tablet:** „⋮” → „Tryb wykładu…” → anuluj aparat → kapsuła zostaje; Ctrl+F (szukaj) przy
  włączonej kapsule — pasek nad kapsułą; dymek (np. po zdjęciu) nie zasłania kapsuły; urządzenie bez aparatu → tryb się
  kończy z komunikatem.
- **A2-9** L1 nazwy punktów i podmenu — `noc/f2-konstrukcje`; `ConstructOpsTest` (3 nowe testy, stare filtrują kreski).
  Każda konstrukcja zbiera swoje linie/okręgi i nazwane punkty (`Draw`), nazwa idzie w środek największej wolnej luki
  kątowej między kierunkami linii przy punkcie (boki, wysokości, środkowe, symetralne, styczne okręgów), odległość = promień
  kropki + pół przekątnej pola. Litery: A, B, C, O, I, S/G, H, M, P/Q (plan wymieniał A, B, C, O, H; resztę dobrałem wg
  szkolnych oznaczeń). Nazwy wierzchołków dopisywane przy każdej konstrukcji trójkąta, chyba że w zaznaczeniu są już pola
  „A/B/C” (zaznacz trójkąt razem z podpisami, a nie powtórzą się). **Wygląd:** podpisy kursywą 11 pt w kolorze pióra, jak
  podpisy osi w F8; menu: „Konstrukcja” ze strzałką ›, po dotknięciu to samo okno menu pokazuje nagłówek „‹ Konstrukcja”
  i listę konstrukcji; wskazówka szarym drobnym tekstem w miejscu pozycji. **Tablet:** narysuj trójkąt z przytrzymaniem →
  zaznacz → przytrzymaj → „Konstrukcja ›” → „Wysokości i ortocentrum”: litery A, B, C na zewnątrz rogów, H obok kropki,
  nie na liniach; trójkąt prostokątny → „A=H”; zaznacz odręczny trójkąt (bez przytrzymania) → przytrzymaj → wskazówka;
  Ustawienia → Edytor i pióro → Konstrukcje → wyłącz podpisy.
- **A2-10** szukanie w „Laboratorium” — `noc/f2-szukanie`; `SettingsQueryTest.labCategoryIsSearchedOnlyInLab`.
  `SEARCHABLE_CATS = SettingsQuery.searchableCats(Flavor.lab != null)`, w wynikach kategoria 7 rysuje
  `Flavor.lab?.SettingsSection()`. Uwaga dla B (bez zmian w `Lab.kt`): opisy w grupach lab to gołe `Text` — w wynikach
  szukania pokażą się razem z pasującym wierszem; jeśli to przeszkadza, owinąć je w `Extra { }` jak w „Obracanie i kąty”.
  **Tablet (labDebug):** Ustawienia → szukaj „kreda” albo „telefon” → wiersze z „Laboratorium”; w coreDebug brak tej
  kategorii w wynikach.
- **A2-11** drobne z przeglądu — `noc/f2-drobne`; `GuideTest.checkYourselfPageExercisesWork`,
  `MnotesArchiveTest.temporaryArchiveLivesOutsideTheNotebook`. (1) Przewodnik: strona 7 „Sprawdź się” (5 zadań), strona 2
  zdanie o √/∑. **Wygląd:** jak pozostałe strony przewodnika — tytuł 22 pt w zieleni, polecenia 11,5 pt, szary przerywany
  trójkąt do przerysowania i dwa grafitowe okręgi (prawdziwe kształty, nie wzory). (2) B5: `MnotesArchive.extract(…, tmpDir)`
  z `repo.cache` (= `cacheDir`). (3) BACKLOG: naklejki poza eksportem — wpis F19 doprecyzowany. **Tablet:** Biblioteka →
  Nowy → „Zeszyt-przewodnik” → strona 7: zadania 1 i 2 wykonalne bez rysowania okręgów; import `.mnotes` jak w B5.
- **Scalenie `noc/lab2`** (po A2-11): trzy commity B (B2-5–B2-7). Jedyny konflikt w `LabFeatures.kt` — obie strony dodały
  metodę z pustą domyślną implementacją (`onLecturePhoto` A, `imageInserted` B), zostały obie. Testy po scaleniu: core 677,
  lab 717, 0 błędów; oba APK.
- **A2-12** B6 i B8 jeszcze raz — `noc/f2-wejscie`; `HoldGestureTest` (4 testy), B8 tylko UI. Przeczytana ścieżka: `onDown` →
  `beginTool` → `penDown` (start licznika) → `penMove` (dryf) → `holdRunnable` → `trySnapShape` / `curveHold`; podpowiedzi:
  `ToolBtn` → `TooltipBox`. **B6:** licznik przytrzymania startuje od nowa dopiero po dryfie > 20 dp (było 10 dp), a w chwili
  odpalenia pióro musi stać (≤ 6 dp w ostatnich 100 ms; jeśli nie — sprawdzenie co 80 ms), bo większy promień sam w sobie
  łapałby drobne pismo w miejscu. Logika w `core/pen/HoldGesture` (bufor ostatnich 64 próbek). **B8:** `TooltipBox(…,
  enableUserInput = false)` i własny wyzwalacz: najechanie pokazuje podpowiedź tylko kursorowi myszy (po 600 ms), dotyk i
  rysik — tylko po przytrzymaniu (czas systemowy long-press); zdarzenia nie są zużywane, kliknięcia bez zmian. Trzecia
  hipoteza B8 do sprawdzenia: z klawiaturą (etui z klawiaturą) jednoliterowy skrót „H” przełącza na Rękę, gdy żadne pole
  tekstu nie ma fokusu. **Tablet:** (B6) narysuj prostokąt i przytrzymaj lekko drżącą ręką — kształt w ~0,55 s, a nie po
  1 s+; napisz drobne słowo bez odrywania (np. „mmm”) — nic się nie prostuje w trakcie; (B8) zaznacz coś lassem, prowadź
  rysik nad paskiem bez dotykania — żadnych dymków; przytrzymaj przycisk Ręki rysikiem — dymek „Ręka (przewijanie)”; z myszą
  (DeX) najechanie dalej pokazuje dymek. **Pytanie do Marcina bez zmian:** który uchwyt (B6) i kiedy dokładnie „ręka” (B8).
- **Scalenie `noc/lab2`** (po A2-12): B2-6 (wektoryzacja, `RealPhotoTest` pomijany bez `KREDA_DIR`) i notatki B2-7; bez
  konfliktów; testy core 681, lab 726 (2 pominięte), oba APK.
- **A2-13** FB4 linijka i kątomierz — `noc/f2-linijka`; `ProtractorTest` (6 testów), `ConstructOpsTest` (nowa pozycja
  „angles”). Linijka istniała już w pełnym zakresie z planu (przesuwanie/obrót dwoma palcami, przyklejanie pióra do krawędzi,
  podziałka w mm w skali strony) — bez zmian poza wspólnym interfejsem `Instrument`. Dołożony kątomierz i odczyt kąta.
  **Decyzja:** odczyt kąta „przez zaznaczenie” poszedł do podmenu „Konstrukcja ▸” (tam, gdzie już są operacje na
  zaznaczonych kształtach), a miara jest widoczna od razu w nazwie pozycji — wstawianie łuku jest opcjonalne.
  **Wygląd:** kątomierz w tym samym stylu co linijka (wypełnienie akcentem 24–33 % krycia, obwódka akcentu, podziałka
  grafitowa/biała w ciemnym motywie, opisy 9,5 dp, białe uchwyty z obwódką); dymki z miarą jak przy linijce (ciemna pigułka).
  Łuki i miary kątów na stronie: kolor pióra, 11 pt, jak nazwy punktów z A2-9. **Tablet:** pasek (albo „⋯”) → Kątomierz;
  przesuń go palcem, obróć dwoma palcami albo uchwytem; postaw rysik w krzyżyku i prowadź — prosta po promieniu, dymek
  „35°”; druga prosta z tego samego środka → zaznacz obie lassem → przytrzymaj → „Konstrukcja ›” → „Kąt między prostymi:
  …” (ta sama miara); trójkąt z przytrzymaniem → „Kąty: … · … · …” → łuki z miarami.
- **Scalenie `noc/lab2`** (po A2-13): B2-8 („Wyślij stronę do mLabs”, `LabFeatures.commands/Overlay`); bez konfliktów;
  core 686, lab 732, oba APK.

### Faza 2b — agent A (uwagi Marcina z PDF „Do poprawy w mNotes pt2”, pierwszeństwo przed resztą fazy 2)

PDF-u nie da się tu wyrenderować (brak `pdftoppm`, bez instalowania pakietów) — pracuję z opisu w `PLAN-NOCY.md`.
Wygląd ikon sprawdzam rysunkiem Java2D z tej samej geometrii (`PreviewHarness`, `MNOTES_PREVIEW_DIR`).

- **2b-A1** ikony gumki/lassa — `noc/f2b-ikony`; `PenGlyphsTest` (`eraserModesShareTheEraserAndDifferInOneMark`,
  `everyBarItemHasAnIcon`). **Wygląd** (`docs/podglad/ikony-narzedzi.png`: jasny, ciemny i aktywny akcent): gumka jak
  pióro na przekątnej (gumowa końcówka, opaska, oprawka, kontur 1,35, wypełnienie 16 %), długość zakreślacza; znak trybu
  w prawym dolnym rogu: fala / trzy okruchy / przerywana pętla / nożyczki. Lasso: przerywana pętla + sznur z supłem
  (pierwsza wersja z prostym ogonkiem wyglądała jak dymek komiksowy — poprawione po podglądzie); prostokąt: ramka
  przerywana + pełny kwadratowy uchwyt w rogu. Stan aktywny jak u piór (kolor akcentu z `ToolBtn`). Gumka na pasku
  pokazuje bieżący tryb (wcześniej ikona Material). **Tablet:** pasek i listwa gumki/lassa — czy ikony stoją w jednym
  rzędzie z piórami i są czytelne w 36 dp; Ustawienia → Edytor i pióro → Układ paska — te same ikony.
- **Scalenie `noc/lab2`** (po 2b-A1): B2-9 (parowanie z zobowiązaniem) i projekt 2b-B1; bez konfliktów; core 694, lab 740.
- **2b-A2** listwa przy narzędziach pomocniczych — `noc/f2b-listwa`; `HelperStripTest` (4 testy). Dwa tryby z planu
  (a = domyślny, b) jako `Pref("toolbar.helper_strip")`, menu jako lista wyłączonych pozycji (`toolbar.helper_menu`, pusty =
  wszystko). Stary przełącznik `toolbar.tool_strip` nie jest już czytany (kto miał opcje na listwie, dostaje domyślny tryb a).
  `Editor.tunePen` i przycisk powrotu idą przez `returnToWriting()` → ostatnie narzędzie pisania (`LastOf`). **Wygląd:**
  menu to ta sama karta co „Pobierz format” (`m.chrome`, obwódka `m.line`, cień 4 dp, zaokrąglenie 12 dp), w środku te
  same przyciski co na listwie, ułożone wzdłuż paska; pojawia się 150 ms, gaśnie 180 ms; przycisk powrotu ma glif pióra
  w jego kolorze (albo krzywej). W Ustawieniach: wybór trybu (chipy) z jednym zdaniem objaśnienia, trzy przełączniki „Menu
  przy gumce/lassie/laserze” i pod każdym chipy grup. **Tablet:** wybierz gumkę → listwa zostaje przy kolorach, przy
  ikonie gumki menu z trybami i rozmiarami; dotknij koloru na listwie → wraca pióro (albo Krzywa, jeśli to nią pisałeś);
  Ustawienia → Wygląd → „Opcje narzędzia + powrót” → opcje gumki na listwie, ostatni przycisk wraca do pisania; wyłącz
  „Menu przy laserze” → laser bez menu.
- **Scalenie `noc/lab2`** (po 2b-A2): Nearby 2b-B1 kroki 1–2; bez konfliktów; core 718, lab 764.
- **2b-A3** mini-mapa: rozmiar i narożnik — `noc/f2b-minimapa`; `MiniMapTest.cornerAndSizeAreConfigurable`. Razem z A2-2
  (gaśnie po ruchu, tylko palec). **Wygląd:** pod przełącznikiem mini-mapy w Wygląd suwak „Rozmiar mini-mapy: 120 dp”
  (krok 10) i chipy narożnika; mapa jak dotąd, tylko w wybranym miejscu i wielkości. **Tablet:** ustaw 200 dp i lewy górny
  → przybliż ≥ 2,5× i przewiń — mapa w lewym górnym rogu, większa; przeciągnięcie palcem po niej przenosi widok.
- **2b-A4** synonimy w wyszukiwarce ustawień — `noc/f2b-synonimy`; `SettingsSynonymsTest` (3 testy). Zamiast dopisywać
  ręcznie po kilkanaście słów do ~90 wierszy: słownik grup (`SettingsSynonyms.GROUPS`) i rozszerzanie zapytania w
  `SettingsQuery.matches`; wiersze, które słownik nie pokrywał (3 w „Laboratorium”), załatał słownik, bez zmian w `Lab.kt`
  agenta B. Test wymaga ≥ 3 synonimów na wiersz i sam znajdzie nowy wiersz bez nich. **Wygląd:** bez zmian (te same
  wyniki, więcej trafień). **Tablet:** Ustawienia → szukaj „mazanie”, „lupa”, „wibracje”, „magnes”, „ciemny”, „mapa”,
  „kreda”, „telefon” — za każdym razem właściwe wiersze; „przyczep” — nowy przełącznik w Gumka i lasso.
- **Scalenie `noc/lab2`** (po 2b-A4): okno Nearby z diagnostyką (2b-B1 krok 3); bez konfliktów; core 722, lab 768.
- **2b-A5** naklejki na białym tle — `noc/f2b-naklejki`; `StickerTest` (2 nowe). Zapis naklejki nigdy nie miał tła (test
  to potwierdza), problemem był podgląd: miniatura na białym papierze w białej karcie. Teraz `Thumbs.render(…, paper =
  false)` i tło karty z `Sticker.darkBackdrop` (jasność atramentu: > 0,62 ciemne tło, < 0,38 jasne, pomiędzy — motyw).
  **[założenie z planu]** przezroczyste tło, podgląd na tle dopasowanym; szachownicy nie dodałem — żadna naklejka nie ma
  dziś przezroczystości, którą trzeba by pokazać (obrazy kredy mają ją, ale nie są rysowane w miniaturze, patrz BACKLOG).
  **Wygląd:** karty 104 dp z zaokrągleniem 8 dp i obwódką `m.line`, tło #FAFAF7 albo #2B2E33. **Tablet:** narysuj coś
  białym na ciemnej stronie → zapisz jako naklejkę → „⋮” → „Naklejki…”: karta ciemna, rysunek widać; czarny rysunek w
  ciemnym motywie — karta jasna; dotknij kosza — „Usunąć?” gaśnie po 3 s.
- **2b-A6** układ współrzędnych na kratce — `noc/f2b-uklad`; `AxesSnapTest` (5 testów). Układ nie ma w pliku żadnego
  znacznika (to zwykłe łamane), więc `AxesSnap.frames` rozpoznaje go po kształcie: pozioma i pionowa łamana z dwóch punktów
  (≥ 40 pt), które się przecinają; oczka siatki z odstępów znaczników i linii siatki. Dzięki temu docisk działa też na
  układzie narysowanym ręcznie i wyprostowanym (dwie proste na krzyż). Docisk kreski: tylko biegi punktów przy tej samej
  linii, ciągnące się ≥ max(3 × próg, pół oczka) — przecinanie linii nie robi „ząbków”. Przy włączonym „Przyciągaj do
  siatki” (stare, co 5 mm) ono ma pierwszeństwo przy przesuwaniu. **Uwaga:** jednostka z okna osi jest na kratce
  zaokrąglana do pełnych oczek (np. 7 mm → 5 mm przy kratce 5 mm) — okno tego nie pokazuje; do decyzji, czy dopisać zdanie.
  **Wygląd:** w Ustawieniach grupa „Układ współrzędnych” (przełącznik + suwak progu). **Tablet:** strona w kratkę → „⋮” →
  „Układ współrzędnych…” → Wstaw: osie i siatka układu leżą na liniach kratki; przesuń układ lassem — początek wskakuje
  w węzły; narysuj wykres v(t) ze stałym odcinkiem poziomym — odcinek równo na linii, ukośne fragmenty bez zmian.
- **Scalenie `noc/lab2`** (po 2b-A6): kreda 2b-B2; bez konfliktów; core 729, lab 780.
- **2b-A7** stanowiska (N dokumentów na jednym polu) — `noc/f2b-stanowiska`; `WorkbenchTest` (5), reszta UI.
  **Decyzja projektowa:** zamiast przebudowy `NoteView` na wiele dokumentów (ok. 3,5 tys. linii z jednym `doc`/`layout`/`ed`)
  każdy dokument ma własny `NoteView` w trybie rzędu, a pole (`ui/StationCanvas`) układa je jeden pod drugim we własnym
  `Layout` z jednym przesunięciem pionowym. Haki w `NoteView`: pionowy ruch i rzut idą do pola (`onPanY/onFlingY`), rząd
  przesuwa się tylko w bok; „100 %” narzuca pole (`baseZoomOverride`, inaczej wysokość rzędu ↔ powiększenie sprzęgały się);
  widoczny pas (`band`) ogranicza rysowanie i kafelki; `hostOffsetY` przesuwa menu i pasek zaznaczenia rysowane w polu;
  upuszczenie zaznaczenia poza rzędem (`onDropOutside` → `dropIn` → `Editor.pasteElements`) przenosi elementy. `EditorScreen`
  pracuje na `Station.active` (dotknięty rząd). Dawne `DualCanvas.kt`, `Editor.dualId/dualPick` i ustawienia
  `dual.fraction`/`dual.sync_zoom` usunięte (`DualRows` zostaje: wspólne powiększenie i lista kandydatów). **Ograniczenia:**
  przeciągany obiekt znika z widoku, gdy wyjdzie poza swój rząd (widać go znów po upuszczeniu w docelowym); skróty klawiszowe
  w `MainActivity` działają na zeszyt otwarty z biblioteki, nie na aktywny rząd; pasek wycinania/kadrowania obrazu nie
  uwzględnia przesunięcia rzędu (tylko w pierwszym rzędzie jest na miejscu). **Wygląd:** rzędy jeden pod drugim z 10 dp
  przerwy w kolorze płótna; nad każdym rzędem chip 10 dp zaokrąglenia: tytuł (aktywny — tło `accentSoft`, obwódka i tekst
  w akcencie), ✕ przy dołożonych; okna `MDialog` „Dołóż zeszyt na stanowisko” (lista jak dawniej + „Nowy zeszyt”) i
  „Stanowiska” (pole nazwy, lista z dokumentami w drugiej linii, kosz z potwierdzeniem). **Tablet:** „⋮” → „Dołóż zeszyt
  poniżej…” dwa razy → trzy rzędy; przewiń pole palcem w pionie (też w przerwie między rzędami) i rzutem; każdy rząd w bok
  osobno; szczypnij — rzędy rosną razem, palce zostają nad tym samym miejscem; zaznacz lassem fragment w rzędzie 1 i
  przeciągnij do rzędu 3 → przenosi się (cofnięcie w obu); przytrzymaj zaznaczenie w rzędzie 2 → menu przy zaznaczeniu;
  panel stron po dotknięciu rzędu 2 pokazuje jego strony; „⋮” → „Stanowiska…” → zapisz „Wykład + zadania”, zamknij
  rzędy ✕, otwórz stanowisko z listy; pamięć i płynność przy trzech PDF-ach.
- **Scalenie `noc/lab2`** (po 2b-A7): kreda 2b-B2, zdjęcie tablicy 2b-B3, zeszyty przez łącze 2b-B1 krok 4; bez konfliktów;
  core 737, lab 791.
- **2b-A8** narzędzie Geometria — `noc/f2b-geometria`; `GeoToolTest` (5 testów). Logika: `GeoTool.Kind` (wejścia każdej
  konstrukcji), `pick` (wejście pod dotknięciem), `build` (wynik przez `ConstructOps.compose`, ten sam `Draw` co menu
  „Konstrukcja ▸”, więc nazwy punktów i styl wyniku są wspólne). `Tool.GEO`: dotknięcie bez ruchu (rysik, mysz; palec, gdy
  „palec rysuje”) → `Editor.geoTap`. **Decyzja:** obrót tylko o 90° (bez wyboru kąta) — kąt do wpisania to osobne okno;
  do potwierdzenia. **Wygląd:** listwa jak przy piórze, glify w siatce 24 tą samą kreską co narzędzia, separatory między
  grupami; nad płótnem kapsuła `FloatingCard` z nazwą konstrukcji w akcencie i krokiem szarym tekstem; wskazane wejścia
  obrysem akcentu (60 % krycia, 5 dp). **Tablet:** pasek (albo „⋯”) → Geometria; „Przecięcie okręgów…”: narysuj dwa koła
  z przytrzymaniem, dotknij jednego i drugiego → P, Q i prosta; „Prostopadła przez punkt”: dotknij odcinka, potem
  dowolnego miejsca → przerywana prostopadła ze spodkiem F; „Okrąg: środek i punkt” — dwa dotknięcia jak cyrklem;
  trójkąt → „Wysokości”; cofnij jednym ruchem.
- **2b-A9** tryb ciemny/jasny stron — `noc/f2b-tryb-ciemny`; projekt `docs/TRYB-CIEMNY.md`; `ThemeMapTest` (9 testów),
  core 751, lab 805, zielone. Logika: `core/color/ThemeMap` (OKLab; papier i szarości — odwrócona jasność w granicach
  0,12…0,94; kolor — ten sam odcień, jasność przesunięta tylko do kontrastu ≥ wyjściowego; zakreślacz ≤ 0,6 na ciemnym,
  ≥ 0,8 na jasnym; macierz dla PDF i zdjęć). Rysowanie: przekształcenie wypalane w kafelkach (`PageRenderer.theme`,
  `PageCache.themeOf`), zmiana trybu czyści kafelki; kreska w trakcie, lasso, mini-mapa, miniatury panelu stron — ten
  sam `Transform`; pipeta odwraca widoczny kolor. Stary filtr F11 usunięty (flaga w pliku bez zmian, stare zeszyty
  otwierają się jak dawniej). **Decyzje:** okładki w bibliotece w oryginale; eksport SVG/MD zawsze w oryginale; zdjęcia
  domyślnie nieodwracane. **Wygląd:** w „⋮” nowa sekcja „Strony” z trzema chipami pod „Układem stron”; w Ustawieniach →
  Wygląd „Strony zeszytów” (chipy) i przełącznik zdjęć; w oknie eksportu przełącznik „Kolory jak na ekranie” tylko dla
  PDF/PNG i tylko, gdy ekran różni się od oryginału. Podgląd kolorów: `docs/podglad/tryb-ciemny.png`. **Nie
  sprawdzone:** płynność pierwszego przeliczenia kafelków przy długim PDF (na JVM nie da się zmierzyć). **Tablet:**
  zeszyt z białymi stronami i pismem czarnym, czerwonym, granatowym i zakreślaczem → „⋮” → Strony → „Ciemne”: tło ciemne,
  czerń jasna, czerwony dalej czerwony, zakreślacz nie świeci; pisz granatem — kreska w trakcie od razu w kolorze
  końcowym; pipetą weź czerwony z kartki i narysuj obok — wygląda tak samo; otwórz PDF wykładu — tło ciemne, tekst jasny;
  zdjęcie na stronie bez zmian (Ustawienia → Wygląd → „Odwracaj też zdjęcia” je odwraca); „Jak powstały” + „Styl strony”
  → F11 na jednej stronie; Eksport → PDF → „Kolory jak na ekranie” wł./wył. i porównaj pliki.
