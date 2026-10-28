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

Baza: `noc/obraz` agenta A (zawiera wszystkie jego `noc/*` z 02:15). Worktree `projekty/mNotes-lab`. Testy JVM:
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
  zgodny z mLabs). Opis integracji: `plan/INTEGRACJA-MLABS.md`.
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

## Faza 2 — agent B (od 27.10, gałąź `noc/lab2` od `noc/wszystko`, lista B2-* w `plan/PLAN-NOCY.md`)

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
    czy słaba kreda nie ginie. Uruchomienie: `KREDA_DIR=/home/piotr/Projects/Marcin/materialy/kreda/blackboard ./gradlew --offline
    :app:testLabDebugUnitTest --tests '*RealPhotoTest*'`.
  - Test: lab 712, core 667, 0 porażek. **Tablet:** zdjęcie tablicy → różdżka → „Edytowalne kreski” → kreski na kredzie
    (także po przycięciu zdjęcia w zeszycie); litery O i krzyżyki jako 1–2 kreski; grube i cienkie linie różnej grubości.
- **B2-7 mediana K3** (w `45fd396`): `BoardPipeline.prepareMany(count, opt) { load(i) }` wczytuje zdjęcia po jednym i trzyma
  tylko wyprostowane klatki; zdjęcie bez znalezionej tablicy (narożniki = cały kadr), gdy na innych ją znaleziono, wypada
  (`skipped`; w panelu „bez tablicy, pominięte: 2”, po „Zastosuj” dymek); bez tablicy na wszystkich — wszystkie całe jak
  przy wyłączonym prostowaniu; wynik i kreski odnoszą się do zdjęcia odniesienia (pierwszego z tablicą). Test
  `photoWithoutABoardIsSkippedInsteadOfGhosting`, `failedLoadGivesNull`. **Tablet:** zaznacz 3 zdjęcia, w tym jedno ściany →
  „Połącz zdjęcia tablicy” → informacja o pominięciu, bez „duchów”.

- **B2-8 K4 „Wyślij do mLabs”** (`0befd4c`): `docs/INTEGRACJA-MLABS.md` skopiowany do repo (i uaktualniony w `narzedzia/nadzor/`),
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
  Usługi Google Play?” i ponawiany w kółko; (3) przybliżona lokalizacja nie wystarcza (notatka z innej działającej aplikacji Nearby),
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
  — większość pozycji „Co poprawić” zamknięta w B2-5…B2-9 i 2b-B1…B3. *Poprawka 28.10 (2c-B5):* wtedy **nie** wszystkie:
  zostały podpowiedź po wstawieniu zdjęcia bez przycisków (sam dymek), gumka niezerowana po zmianie narożników i
  `busy = true` na zawsze w „Wyślij do mLabs” bez widoku; zamknięte w 2c-B5 (niżej). Otwarte dalej: scalanie stron w
  synchronizacji.

- **2b-B2 c.d., narożniki bez ściany** (`a7d983a`): `guessQuad` obejmował ścianę, ekran projektora i głowy, bo mają kolor
  tablicy i łączą się z nią w jedną plamę maski. `BoardGeometry.clipToContent`: mapa pisma (top-hat, bez listew i ram,
  bez kolorów skóry), zakres pisma, przycięcie **tylko boków, którymi maska wychodzi poza kadr**, o ≥ 2 % i tylko gdy za
  nowym bokiem zostaje ≤ 3 % śladów pisma. 11 z 24 zdjęć zmienionych, każde obejrzane: żaden wzór nie zginął
  (szczegóły i tabela w `docs/KREDA-POMIARY.md`). Wariant „tnij po ramie” sprawdzony i odrzucony (zjadał pismo na
  `20260323_135040`). Zostaje: osoba przy prawym boku `20260525_144320`, głowy pod `20260324_154544` (tryb „Narożniki”).
  Testy: 3 nowe w `BoardDetectTest` (ściana odcięta, pismo za cięciem blokuje je, tablica w kadrze bez zmian); lab 789,
  core 732, 0 porażek. **Nauka:** zdjęcie tablicy z sali bez ręcznego poprawiania narożników.
- **Koniec listy B (ok. 21:15):** `noc/wszystko` (A: 2b-A8…A11) scalona do `noc/lab2` bez konfliktów; na scalonym stanie lab 822,
  core 765, 0 porażek (`KREDA_DIR` ustawione, więc testy na prawdziwych zdjęciach też szły), oba APK. A może scalić `noc/lab2`
  z powrotem przewinięciem.

- **2b-B4 kreda → gładkie krzywe Béziera (28.10 rano, w toku)**: `core/image/ChalkCurves.kt` — szkielet jak dotąd
  (`ChalkVector.skeleton`), narożniki (skręt ≥ 45° zmierzony, skupiony: pół ramienia ≥ 0,65, sąsiedzi ≤ 0,55 — okręgi
  r=5…40 px bez fałszywych narożników), filtr 1‑4‑6‑4‑1 między narożnikami (końce i narożniki stoją), dopasowanie
  Schneidera (`Bezier.fit` dostał opcjonalne kierunki końców; domyślnie bez zmian), tolerancja 1 px (1,2 px przy 2400 px).
  `ChalkStrokes.toCurves`: przycięcie do wycinka w pikselach wyniku, punkty kontrolne przeniesione na stronę, kreska z
  `ctrl` (edycja uchwytami jak F17). Przełącznik „Edytowalne kreski jako gładkie krzywe” (panel i Ustawienia →
  Laboratorium → Kreda, domyślnie włączony; wyłączony = dawne łamane). Testy: `ChalkCurvesTest` (okrąg, sinusoida, S, ∑,
  L/V, schodki prostej, łuki bez narożników, przycięcie pętli, krzywe na kredzie zdjęcia), miary w `CurveMetrics`;
  testy kredy/tablicy/Béziera zielone. Zdjęcie wzoru z okładki PDF wyciągnięte poza repo (strumień obrazu 2992×942,
  `build/kreda-wejscie/okladka-wzor.png`), test dostaje je przez `KREDA_EXTRA`.
- **2b-B4 c.d. (28.10, 11:30–12:30)**: (1) `KredaKrzyweTest` (`08160a2`): łamana i krzywa na tych samych ścieżkach, 24 zdjęcia
  + okładka, tabela w system-out, 4 podglądy PNG w `app/build/kreda-krzywe/`. (2) Pomiar pokazał, że po etapie 1 zostały
  pętelki i ostrza (najgorszy skręt 178°, na `20260326_140000` zębów więcej niż na łamanej). Przyczyna w szkielecie, nie w
  dopasowaniu: porowata kreda ma dziurki 1–3 px, a ścienianie obchodzi każdą pętlą. `ChalkVector.fillHoles` (`3a82a26`)
  zamyka przed ścienianiem dziury ≤ 12 px i szczeliny grubości 1–2 px ≤ 48 px; wnętrza liter (o, e, θ) zostają (test).
  Wynik na 25 zdjęciach: węzły 11,1 → 6,9 na 100 px (łamana → krzywa), **zęby 2,94 → 0,30**, błąd 0,37 px, najgorszy
  skręt 89°; syntetyczne 0 zębów. Tabela i uczciwe wnioski w `docs/KREDA-POMIARY.md` → „Krzywe Béziera”: kryterium
  „≥ 3× mniej węzłów” nie do spełnienia na zdjęciach (średnia kreska 45 px, same końce to 4,5 kotwicy na 100 px), „skręt
  < 25°” spełnione tylko na syntetycznych (zostaje 0,3 zęba na 100 px w węzłach grubych plam). Nowe testy:
  `ChalkVectorTest.porousChalkHolesDoNotBecomeLoops` (bez poprawki: 20 wierzchołków zamiast 2) i `fillHolesKeeps…`.
  Pełne testy z `KREDA_DIR`: lab 852, core 781, 0 porażek (po 1 pominiętym), oba APK.
  **Nauka:** wzór z tablicy w notatce wygląda jak napisany, da się go poprawić uchwytami.
  **Tablet:** zdjęcie z blackboard/ → różdżka → „Edytowalne kreski” → zoom 4×: brak zębów i kółeczek, uchwyty działają.

### FAZA 2c — agent B (28.10 od 11:55, lista 2c-B* w `plan/PLAN-NOCY.md`, przegląd `dzienniki/PRZEGLAD-FABLE-2.md`)

- **2c-B1 utknięte zdjęcie nie przepada** (`74bbf92`): po 4 próbach części bez potwierdzenia `LinkMachine` porzucał zdjęcie
  (`current = null`), następne je wyprzedzało, a „Spróbuj ponownie” przy otwartym łączu szukał od nowa (mógł wisieć na
  „Szukam…”). Teraz zdjęcie wraca na początek kolejki (`PhotoSender.rearm`, licznik rund w dzienniku), wysyłanie czeka na
  użytkownika, a „Spróbuj ponownie” przy gotowym łączu tylko wznawia wysyłkę; nowe połączenie też ją wznawia. Test
  `stuckPhotoStaysFirstInTheQueue…` (eter milknie w połowie zdjęcia 2 z 3) **nie przechodził przed poprawką** (odbiorca
  miał 1 z 3); drugi test: zerwanie w połowie zdjęcia 2 z 3 → 3 zdjęcia, każde raz (hash).
  **Tablet:** 3 zdjęcia z telefonu, w połowie drugiego odsuń telefon za ścianę, aż pojawi się „Połączenie się urwało…” →
  wróć → „Spróbuj ponownie” → na tablecie 3 zdjęcia, żadne podwójnie.
- **2c-B2 szukanie bez zawieszenia** (`29514d2`): `stopSearch()` przed każdym `advertise/discover`, po 8002 jeden stop i
  start od nowa, punkt znany z poprzedniego szukania próbowany od razu, po odmowie (8004) ponowienie po 3 s.
  `FakeAir` zachowuje się jak Nearby (8002/8001, jedno `onFound` na szukanie, odmowa, zamknięty transport bez
  powiadomienia drugiej strony). Trzy testy **czerwone przed poprawką**: tablet odrzuca pierwsze połączenie; zamknięcie
  okna telefonu w trakcie „Łączę…” i ponowne otwarcie; 8002 przy starcie.
  **Tablet:** na tablecie otwórz „Odbierz zdjęcia”, na telefonie „Wyślij” → w trakcie „Łączę…” zamknij okno na
  telefonie i otwórz znowu → łączy się samo (najdalej ok. 20 s, gdy tablet czeka na swój limit).
- **2c-B3 część 1** (`bd6515c`): `LinkState.Sending` ma `photo`/`photos` i `batchProgress` (bajty całej partii; partia =
  zdjęcia dodane, zanim kolejka się opróżniła); okno pisze „Wysyłam zdjęcie 2 z 3 · 47 %”, części bieżącego pliku w drugiej
  linii. `LinkState.Problem.cause` = ostatni wpis dziennika: w oknie „Kod 8012: zdjęcie nie doszło po 4 próbach (runda 1)”.
  Telefon po 8004 pokazuje „Drugie urządzenie odrzuciło…” i sam ponawia po 3 s; tablet zgłasza odrzucone prośby
  (`Listener.rejected`, zdanie w oknie). `PhotoReceiver`: `count` ponad limit pliku → odrzucone (wcześniej
  `arrayOfNulls(Int.MAX_VALUE)` = brak pamięci; tego testu nie uruchamiałem na starym kodzie, wynik z zachowania JVM),
  najwyżej 4 składania naraz (najstarsze wypada). „Limit liczby zdjęć w partii (`count`)” z planu rozumiem jako ten limit
  pola `count`; osobnego limitu długości kolejki nadawcy nie dodawałem (UI dodaje zdjęcia po jednym).
  **Tablet:** 3 zdjęcia → jeden pasek dla całości i „zdjęcie 2 z 3”; problem → kod i przyczyna pod zdaniem.
- **2c-B3 część 2** (`ce9dcfc`): `core/nearby/LinkFile.kt` — plik w łączu w pamięci albo na dysku. Odbiorca zapisuje
  każdą część od razu pod `index × długość części` (ostatnia, która przyszła pierwsza, czeka w pamięci), SHA-256 liczy
  strumieniem; nadawca czyta części z pliku w chwili wysyłki. Lab: „Wyślij zeszyt” eksportuje do `cache/nearby/`, odbiór
  importuje z pliku; pliki znikają po imporcie, po zamknięciu okna, a resztki starsze niż godzina przy otwarciu.
  Test `sixtyFourMegabyteNotebookGoesThroughCacheFilesWithAtMostTwoPartsInMemory`: na starym odbiorcy (z tym samym
  licznikiem) szczyt 134 217 728 B, po poprawce ≤ 2 × 256 KB. Licznik liczy pamięć samego odbiorcy (część w ręku,
  odłożone ostatnie części, bufor sumy), nie ramek w Nearby ani szyfrowania.
  **Tablet:** „Wyślij zeszyt” z dużym zeszytem (z PDF-em, kilkadziesiąt MB) → na drugim urządzeniu nowy zeszyt, który
  się otwiera; w trakcie odbioru aplikacja nie zamyka się z braku pamięci.
- **2c-B4** (`c4ac106`): `core/image/ChalkInk.kt` — kontrast WCAG 2 do papieru strony, na którą trafia wynik
  (`BoardOptions.paper`); za słaby kolor ciemnieje z zachowaniem odcienia, na ciemnym papierze jaśnieje. Cel 4,5:1, nie
  3:1 z planu: szary 3:1 na okładce był blady (podglądy `app/build/kreda-papier/okladka-wzor-{przed,po}.png`, „przed” to
  biel na bieli). `looksLikeDarkBoard`: tło 0,42–0,62 liczy się jako tablica, gdy pismo jest jaśniejsze od mediany tła;
  prawdziwe zdjęcia: rozpoznane dawniej 19, teraz 24 z 24; okładka PDF rozpoznana (wcześniej nie). Po oczyszczeniu w
  trybie wykładu dymek ma przycisk „Cofnij do oryginału” (wspólny `Editor.say(msg, actions)`, dymek z przyciskiem wisi
  8 s). `pdftoppm` nie ma offline — okładka to obraz wyciągnięty z PDF wcześniej (`build/kreda-wejscie/okladka-wzor.png`),
  pomiar: `KREDA_EXTRA=… --tests '*ChalkPaperTest*'`.
  **Ograniczenie (pytanie do Marcina):** kolor dobierany do papieru zapisanego w zeszycie. W „Strony: ciemne” zdjęcia
  nie są odwracane (domyślnie wyłączone „odwracaj zdjęcia”), więc kreda przyciemniona pod białą kartkę będzie ciemna na
  ciemno pokazanej stronie. Edytowalne kreski tego problemu nie mają (tryb stron przelicza je jak atrament). Czy obraz
  kredy ma się odwracać razem ze stroną (osobna flaga obrazu w rysowaniu, kod wspólny — agent A)?
  **Tablet:** tryb wykładu na białej stronie → zdjęcie tablicy → kreda ciemnoszara, czytelna; „Cofnij do oryginału”
  w dymku wraca do zdjęcia (także po przesunięciu go); zdjęcie szarej tablicy → podpowiedź „Wygląda jak tablica”.

- **2c-B5** (`303cfdf`): resztki K1. Podpowiedź po wstawieniu zdjęcia tablicy ma dwa przyciski: „Oczyścić?” zaznacza
  zdjęcie i otwiera panel kredy (przez `Lab.Overlay`, wspólny ekran bez zmian), „Nie pokazuj więcej” wyłącza ją (ten sam
  przełącznik co w Ustawieniach → Laboratorium). Do tej chwili podpowiedź pokazuje się przy każdym zdjęciu tablicy, nie
  raz. Gumka: `core/image/BoardEdits` — zmiana narożników, „Narożniki automatycznie”, prostowania albo kadru zeruje pętle
  (były w ułamkach wyniku, więc po zmianie trafiały w inną kredę); `BoardEditsTest` (4). „Wyślij do mLabs”: `busy` i
  zamknięcie okna w `finally` przez główny wątek, nie przez `ed.view`. Poprawiona linia o K1–K5 wyżej. Lab 875, core 793.
  **Tablet:** zdjęcie tablicy → dymek z „Oczyścić?” → panel kredy dla tego zdjęcia; drugi raz → „Nie pokazuj więcej” →
  trzecie zdjęcie bez dymka. W panelu: gumka (2 pętle) → „Narożniki” → przesuń kółko → licznik „Wyczyść gumkę (0)”.

- **2c-B6** (`bd8220d`): tabela przed/po (24 zdjęcia i okładka) i podglądy były już w 2b-B4 c.d.; dopisane wiersze dla
  okręgu, sinusoidy, S i ∑ osobno (`docs/KREDA-POMIARY.md`): skręt < 25° na wszystkich, ≥ 3× mniej węzłów tylko okrąg.
  PNG przed/po: `app/build/kreda-krzywe/{syntetyczne,okladka-wzor,20260318_154119,20260326_140000}.png` (plan podawał
  `kreda-bezier/`, katalog z testu został). **Tablet:** jak 2b-B4 (zoom 4× na „Edytowalnych kreskach”).

### Co Marcin ma sprawdzić na tablecie — wariant lab, po fazie 2 (kolejność)

Instalacja: `labDebug` z `noc/lab2` (albo `noc/wszystko` po scaleniu przez A) na tablecie **i** na telefonie (Nearby).

1. **Kreda, jedno zdjęcie:** wstaw zdjęcie tablicy zrobione pionowo telefonem → raz dymek „Wygląda jak tablica…” → różdżka
   → podgląd stoi pionowo, suwak progu chodzi płynnie → „Narożniki”: przeciągnij kółko → „Wynik” → „Gumka”: obwiedź rękę →
   „Zastosuj” (zeszyt w tym czasie dalej się zapisuje) → ↶ wraca do zdjęcia.
2. **Listwy i ramy:** zdjęcie dwóch tablic z listwą → listwa i ściana znikają, wzory zostają; wyłącz kolejno „Usuń listwy…”,
   „Dociągaj bladą kredę…”, „Usuń drobinki” i porównaj podgląd. Zdjęcie tablicy z kawałkiem ściany pod nią albo z boku
   (jak `20260525_*`) → „Narożniki” od razu na krawędziach tablicy, nie na ścianie; żaden wzór nie ucięty.
3. **Kreski:** „Edytowalne kreski” → kreski leżą na kredzie także po przycięciu zdjęcia w zeszycie; litera O i krzyżyk to
   1–2 kreski; grube i cienkie linie mają różną grubość. **Od 28.10 krzywe Béziera (2b-B4):** zoom 4× — łuki gładkie, bez
   zębów i bez kółeczek w grubej kredzie; stuknięcie w kreskę → uchwyty jak przy narzędziu „Krzywa”; Ustawienia →
   Laboratorium → Kreda → „Edytowalne kreski jako gładkie krzywe” wyłączone → dawne łamane.
4. **Mediana:** 3 zdjęcia tej samej tablicy (+ jedno zdjęcie ściany) → „Połącz zdjęcia tablicy” → informacja o pominięciu,
   wykładowca znika, bez „duchów”.
5. **Tryb wykładu:** zdjęcie tablicy → na nowej stronie po chwili sama kreda + dymek; ↶ → zdjęcie; zdjęcie kartki bez zmian.
6. **Nearby (dwa urządzenia):** `docs/NEARBY-PRZEBUDOWA.md` §5 — parowanie (role same, te same 6 cyfr), lokalizacja
   wyłączona → zdanie „Włącz lokalizację…”, 3 zdjęcia z postępem, Bluetooth wyłączony w połowie → wznowienie,
   „Diagnostyka” → „Kopiuj raport”; potem „Wyślij zeszyt” / „Odbierz zeszyt”.
7. **Wyślij do mLabs:** Plik → „Wyślij stronę do mLabs…” → dwa pliki (PNG i JSON) w wybranej aplikacji.
8. **Core:** `coreDebug` — brak różdżki, ikony telefonu, „Laboratorium” i polecenia „Wyślij do mLabs”.

## Faza 2 — agent A (od 27.10, lista A2-* w `plan/PLAN-NOCY.md`)

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
- **2b-A10** krzywe Béziera, cztery tryby — `noc/f2b-bezier`; `CurveDraftTest` (10 testów), core 761, lab 815, zielone.
  Logika: `core/shapes/CurveDraft.kt` — `CurveMode` (kolejność listwy, ustawienie `curve.mode`), `CurveFit.through`
  (dośrodkowy Catmull‑Rom → Bézier, otwarty z odbiciem na końcach, zamknięty z zawinięciem), `CurveFit.freehandTol`,
  `CurveDraft` (węzły, uchwyt piórka, `tapKind`: dodaj / zakończ / domknij); `Bezier.moved`/`smooth` znają szew krzywej
  zamkniętej. **Decyzje:** zamknięcie bez nowego pola (kotwica końcowa = początkowa, jak zamknięta łamana); wypełnienie jako
  pole kreski (`Stroke.fill`, flaga 128 + i32 na końcu rekordu), a nie osobny element — po edycji uchwytów idzie za krzywą;
  zmiana koloru kreski zmienia też wypełnienie; gumka pikselowa tnie krzywą na zwykłe kreski bez wypełnienia. Palec
  stawia punkty tylko z „palec rysuje” (jak Geometria); bez tego palec przewija. **Wygląd:** z Krzywą w ręku listwa
  zaczyna się od 4 glifów trybów (+ wiaderko „wypełniaj” w trybach krokowych), potem pióro jak zwykle; nad płótnem
  kapsuła jak w Geometrii („Kontur zamknięty · Kolejny punkt; pierwszy albo ostatni stuknięty ponownie domyka”, ✓ i ✕);
  szkic: krzywa piórem na żywo, węzły w akcencie, pierwszy powiększony, gdy można domknąć, uchwyty piórka jako odcinek
  z kropkami. Podgląd: `docs/podglad/krzywe-tryby.png`. **Tablet:** pasek → Krzywa → glif „Przez wskazane punkty”:
  stuknij rysikiem 4–5 punktów → krzywa idzie przez wszystkie; stuknij ostatni drugi raz → gotowa i zaznaczona, przeciągnij
  kotwicę; „Kontur zamknięty” + wiaderko: 4 punkty → stuknij pierwszy → zamknięty, lekko wypełniony kształt, pismo pod nim
  widać; „Piórko”: stuknij, przeciągnij (uchwyty jak w Inkscape), stuknij → łuk; ✕ porzuca; „Odręczna”: narysuj falę →
  krzywa z kilkoma węzłami; otwórz zeszyt w starej wersji aplikacji (jeśli jest) — kontur bez wypełnienia.
- **2b-A11** samouczek — `noc/f2b-samouczek`; `GuideTest` (8 testów), core 765, lab 819, zielone. Forma z [założenia]:
  zeszyt-przewodnik z rozdziałami podstawy → zaawansowane, każda strona = krótkie zadanie piórem, na końcu „Sprawdź się”.
  `core/guide/Guide.kt` przepisany: lekcje jako dane (`Lesson`: rozdział, tytuł, wstęp, polecenia, wskazówka,
  ilustracja od y), układ liczony (`textBottom`/`artTop`, stopka od `FOOT`), wariant `lab` (GuideMaker podaje
  `Flavor.lab != null`). **Decyzje:** stare przewodniki w bibliotekach zostają jak były (nowy powstaje z „Nowy →
  Przewodnik”); szerokość znaku w szacowaniu 0,6 em (podgląd Java2D pokazał, że 0,54 bywa za mało); dużo miejsca pod
  ilustracjami zostawione celowo na ćwiczenia. **Wygląd:** podgląd `docs/podglad/przewodnik-strony.png` (17 stron).
  **Nie sprawdzone:** czcionka Roboto na tablecie jest węższa niż DejaVu z podglądu — akapity mogą mieć trochę więcej
  powietrza niż na podglądzie. **Tablet:** Biblioteka → „Nowy” → Przewodnik; przewiń: okładka ze spisem treści,
  stopka „n / 17”; strona 8 „Krzywe Béziera”: Krzywa → „Przez wskazane punkty”, stuknij A–E i E drugi raz; strona 9:
  Geometria → „Przecięcie okręgów” na dwóch okręgach strony; strona 15: „⋮” → Strony → „Ciemne” i porównaj z ilustracją;
  w wersji lab strona 14 mówi o kredzie, w core nie.
- **2b-A12** profile pracy — `noc/f2b-profile`; `ProfilesTest` (8 testów), core 773, lab 827, zielone. Logika:
  `core/prefs/Profiles.kt` — lista kluczy, które profil może nadpisać (5 sekcji; nic z danych), `switch` (najpierw powrót
  do kopii sprzed profilu, potem nowy — A → B → brak = stan sprzed A), plik tekstowy z wierszami i tabulatorami (bez JSON,
  bo `org.json` w testach JVM to atrapa), `merge`, `next`, przypięcia. `Settings`: rejestr `Pref` po kluczu,
  `prefValue`, `switchProfile`, `profiles` (pusty zapis = gotowe profile). **Decyzje:** zmiany ustawień zrobione ręcznie
  w trakcie profilu znikają po jego wyłączeniu, jeśli dotyczą kluczy profilu (powrót jest dokładny) — żeby je zachować w
  profilu: „Zapisz obecne”; przypięty profil włącza się przy otwarciu zeszytu i zostaje po wyjściu (bez cichego
  przełączania z powrotem); stanowisko — przez główny zeszyt. **Wygląd:** „⋮” → sekcja „Profil pracy” (chipy jak „Strony”)
  i przełącznik przypięcia; Ustawienia → Wejście na górze: karta z wyjaśnieniem, profil = nazwa + opis „palec … · sekcje:
  n” + chipy akcji; okno profilu: nazwa, dotyk (chipy), 5 przełączników sekcji, uwaga jak zmienić pasek w profilu.
  **Tablet:** „⋮” → Profil pracy → „Tylko rysik” → dymek „palec wyłączony”; przesuń palcem — nic, rysik pisze; „Bez
  profilu” — palec znów przewija; Ustawienia → Wejście → „Nowy z obecnych ustawień…” → „Moje”, dotyk „Rysuje” → zapisz →
  włącz z „⋮”; włączony profil + „Włączaj … przy otwieraniu tego zeszytu” → wyjdź, przełącz na „Bez profilu”, otwórz
  zeszyt → dymek „(przypięty do zeszytu)”; eksport profili do pliku i import (pojawią się kopie z nowymi id).
- **Scalenie `noc/lab2`** (po 2b-A12): agent B skończył listę (narożniki bez ściany 2b-B2, notatki); bez konfliktów; core 773,
  lab 830.
- **2b-A13** zeszyt „Tablica” — `noc/f2b-tablica`; `BoardTest` (7 testów), core 780, lab 837, zielone. Logika:
  `core/layout/Board.kt` (rozmiar startowy, krok i zapas wzrostu, limit, kadr eksportu, punkt startu widoku);
  `PageSpec.OPT_BOARD` w polu `o` (tablica może mieć do 24 000 pt; zwykłe strony dalej przycinane do 8000).
  `Editor.exec` dokłada płótno w tej samej grupie edycji (`DocEdit.Group`: zmiana treści + rozmiar + ewentualne
  przesunięcie całej treści), `Editor.record` (przeciągnięcie lassem) — osobnym wpisem; `NoteView.boardShifted` przesuwa
  widok razem z treścią. `PageCache.tiledBase`: baza kafelków najwyżej 4 mln pikseli. **Decyzje:** tablica to jedna
  strona, nie nowy format (stara wersja ją otworzy); „100 %” = szerokość A4; mini-mapa zawsze na tablicy; eksport = kadr
  treści. **Znane:** cofnięcie wzrostu w lewo/w górę przesuwa treść z powrotem bez przesunięcia widoku (widać skok o krok
  płótna); stara wersja po zapisie tablicy > 8000 pt zapisze przycięty rozmiar (treść zostaje, nowa wersja dołoży płótno
  przy następnej zmianie). **Wygląd:** tablica wygląda jak duża kartka w kratkę bez marginesu, okładka w bibliotece
  w kratkę. **Tablet:** Biblioteka → „Nowy” → „Nowa tablica” → otwiera się na środku płótna w skali zeszytu; rysuj, przesuń
  widok do prawej krawędzi i pisz przy niej → płótno się wydłuża (mini-mapa w rogu pokazuje większą stronę); to samo przy
  lewej krawędzi — treść nie skacze; cofnij — płótno wraca; szczypnij maksymalnie — widać całe płótno; Ctrl+0 / „Dopasuj” —
  cała treść; Eksport → PNG → obraz samego rysunku z małym marginesem; pamięć przy długim rysowaniu.
- **2b-A14** aparat i tablica — `noc/f2b-aparat`; `KeymapTest.everyCommandHasAnAction` (nowy), core 781, lab 838, zielone.
  Nagłówek: ikona wstawiania obrazu z pliku → `AddPhotoAlternate` (ramka z górami i plusem), nowy przycisk „Zdjęcie
  tablicy” z glifem B (`BoardCameraIcon`). Jeden przepływ: `ed.lectureShootTick++` → aparat systemowy (bez uprawnienia
  CAMERA) → `Editor.lectureShot` (nowa strona z datą) → w lab `Flavor.lab.onLecturePhoto` (kreda). Tryb wykładu dalej
  włącza kapsułę i używa tej samej ścieżki. **Decyzja:** „Zdjęcie tablicy” wkłada zdjęcie na nową stronę (jak tryb
  wykładu), nie na bieżącą — notatki z wykładu nie lądują pod zdjęciem; do potwierdzenia. **Tablet:** w nagłówku dwie
  różne ikony obok siebie: obraz z plusem (galeria/pliki) i aparat przy tablicy; dotknij aparatu → aparat systemowy →
  zrób zdjęcie → nowa strona za bieżącą z datą i zdjęciem; w lab zdjęcie ciemnej tablicy po chwili zamienia się w samą
  kredę, ↶ wraca do zdjęcia; obróć tablet podczas aparatu — po powrocie aparat nie otwiera się drugi raz.
- **Faza 2b — lista A wyczerpana** (2b-A1…A14). Dalej reszta fazy 2: A2-14, A2-15.
- **A2-14** pomysły Fable FB5–FB8 i FB13 → BACKLOG (sekcja K, „Pomysły Fable na później”) z „Nauka: …”; bez wdrażania —
  po A2-13 lista nie była pusta (A2-14, A2-15), więc warunek „wdrażaj FB7/FB6” nie zaszedł. Gałąź `noc/f2-pomysly`.
- **A2-15** przegląd własny — `noc/f2-przeglad`; testy na `noc/wszystko`: core 781, lab 838, 0 porażek, oba APK.
  PRZEGLAD-FABLE §3.1–3.4 przejrzane punkt po punkcie; zamknięte wcześniej w fazie 2/2b: B5 (A2-11), B6/B8 (A2-12), F4 →
  stanowiska (2b-A7), F6 (A2-7), F11 → tryb ciemny (A2-6, 2b-A9), F12 i F22/F28 w wyszukiwarce (2b-A4), F18 (A2-3), F19
  (2b-A5, A2-11), F20 (A2-11, 2b-A11), L1 (A2-9), L2 (A2-1), FB1 (A2-8, 2b-A14), FB3 (A2-2, 2b-A3), FB4 (A2-13). **Zamknięte
  teraz:** F7 nazwy w panelach jak w Ustawieniach („Gumka pomija zakreślacz”, „Co zaznacza lasso”, „Połowa w pętli” /
  „Wystarczy dotknąć” zamiast „Zbalansowane”); F8 „I ćwiartka” przez `T.t`, pola zakresu z klawiaturą liczbową; F17 komentarz
  o bitach flag osobnych dla rodzaju rekordu (też nowy 128 = wypełnienie kreski); F21 podpowiedź „To uchwyt…” jako tekst,
  nie wyszarzona pozycja; F22 „Przyczep do figur” także w panelu lassa; F25 „Największe powiększenie” → Wygląd, zdjęcia do
  kratki → Edytor i pióro; F26 dopowiedzenie znika razem z wierszem w wyszukiwaniu; §3.2a menu „⋮” przewijane (do 560 dp)
  z sekcjami Układ stron / Strony / Profil pracy / Widok / Wstaw / Stanowisko; ikony orientacji kartki `CropPortrait/
  CropLandscape` zamiast telefonu. **Zostaje na tablet / decyzję:** B1, B2, B9 (tylko pomiar), F21 czas podwójnego dotknięcia.

### 28.10 rano — zaległości z przeglądu Fable (dopisek 04:55, przed FAZĄ 2c)

Gałąź `noc/f2c-zaleglosci` od `noc/wszystko`. PRZEGLAD-FABLE §3.1–3.4 i §4 przejrzane jeszcze raz w kodzie; z kolumny
„Co poprawić” zostały otwarte trzy rzeczy, teraz zamknięte:
- **F21 przycisk „Punkty i uchwyty”** (gest podwójnego dotknięcia był niewidoczny): `PointEdit.offers` + test
  `pointsButtonOnlyWhereItChangesSomething`. **Wygląd:** na pasku zaznaczenia, zaraz za „Skaluj i obracaj”, glif łamanej
  (`Timeline`) w tym samym rozmiarze 36/20 dp; przy prostokącie świeci akcentem, gdy wierzchołki są pokazane. Nie ma go
  dla odcinka, łamanej i krzywej (uchwyty są od razu) ani dla zablokowanej kreski.
- **§3.2a menu zaznaczenia**: „Wycinek jako obraz”, „Kopiuj wycinek do schowka” i „Zapisz jako naklejkę…” w podmenu
  „Zaznaczenie jako ▸” (ten sam wzór co „Konstrukcja ▸”: nagłówek z „‹” wraca). Pierwszy poziom menu z zaznaczeniem: do
  18 zamiast 20 pozycji. Przewodnik (lekcje „Lasso”, „Naklejki”, „Wycinek strony”) mówi o nowej ścieżce.
- **§3.2a kolor lasera w Ustawieniach**: Edytor i pióro → grupa „Laser” → „Kolor śladu lasera” (4 kropki jak przy kolorze
  akcentu); wyszukiwarka zna „laser”, „wskaźnik”, „pointer” (`SettingsSynonymsTest`).
- BACKLOG: dwa ostatnie wpisy NOC bez „Nauka: …” (F16, FB10) dostały to zdanie; wszystkie wpisy NOC/2b je mają.
- Sprawdzone, że są już zamknięte: B5, F6 (180 ms, suwak 100–400), F7 (glify w oknie układu paska), F8, F12, F17, F18,
  F19 (tło kafla, „Usunąć?” gaśnie po 3 s), F20, F22, F25, F26, F28, §3.2a (menu „⋮”, kategorie, ikony orientacji,
  komentarze w `Settings.kt`), L1, L2, FB1, FB3 (cień i ramka mini-mapy jak strony). §3.5 (lab) i §3.6 (mLabs) robił B.
- **Nie zrobione — zablokowane:** `dzienniki/DZIENNIK.md` znów ma 3 ucięte znaki UTF‑8 (wiersze ok. 901–903, wpisy
  „ostatni commit: …” z 27.10 wieczorem). Przyczyna: `narzedzia/nadzor/nadzor.sh:198` tnie temat commita przez
  `cut -c1-90`, a GNU `cut` liczy bajty, więc przecina „…” i „ł” w pół. Naprawy pliku skryptem nie dostałem (polecenie
  odrzucone w trybie bez pytań), a skryptu nadzoru nie zmieniam w trakcie jego pracy (bash czyta plik w biegu). Propozycja
  dla nadzoru: `commit=$(git … log -1 --format='%h %ar %s' | python3 -I -c 'import sys; print(sys.stdin.read().strip()[:90])')`
  albo `awk '{print substr($0,1,90)}'` w locale UTF‑8; plik naprawić jednorazowo z `errors='replace'`.
- Testy: core 782, lab 839, 0 porażek, oba APK debug.
- **Tablet:** zaznacz lassem odręczną falę → na pasku zaznaczenia nowy glif łamanej → kreska zmienia się w krzywą z
  kotwicami; zaznacz prostokąt z przytrzymaniem → glif → wierzchołki zamiast ramki, glif świeci, drugie dotknięcie wraca;
  odcinek — glifu nie ma. Przytrzymaj zaznaczenie → „Zaznaczenie jako ▸” → trzy pozycje, „‹” wraca. Ustawienia → szukaj
  „wskaźnik” → „Kolor śladu lasera”; zmień na zielony i sprawdź ślad lasera.

- **FB8 tagi stron** (po zaległościach, wg A2-14: lista pusta do czasu FAZY 2c) — `noc/f2c-tagi`; `PageTagsTest` (9),
  core 791, lab 848, zielone, oba APK. Szczegóły w BACKLOG (FB8). **Wygląd:** okno `MDialog` z chipami „#tag”; tagi w
  zakładce „Zakładki” panelu stron (trzecia zakładka nie mieści się w 236 dp). **Nie sprawdzone:** UI tylko kompilacja.
  **Tablet:** miniatura → „⋮” → „Tagi strony…” → „#definicja” → Zapisz; pod miniaturą „#definicja”; Ctrl+F „#def” →
  strony z tagiem; biblioteka: szukaj „#definicja” → zeszyt z „· n stron”, otwarcie przenosi do pierwszej.
- **Zostaje (limit użycia):** FB7 statystyki zeszytu, FB6 strona jako obraz do schowka; FAZA 2c, gdy się pojawi.

### Raport końcowy agenta A — faza 2 i 2b (27.10, lista wyczerpana)

**Gałęzie** (każda od `noc/wszystko`, po punkcie przewinięta do `noc/wszystko`): fazy 2 `noc/f2-*` (A2-1…A2-15), fazy 2b
`noc/f2b-*` (ikony, pasek-pomocniczy, minimapa, synonimy, naklejki, osie, stanowiska, geometria, tryb-ciemny, bezier,
samouczek, profile, tablica, aparat). **`noc/wszystko`** = całość A + wszystkie scalenia `noc/lab2` agenta B (ostatnie po
2b-A12, B skończył listę). `main` nietknięty, bez push. **Testy:** core 781, lab 838, 0 porażek (1 i 3 pominięte: podgląd
i testy na prawdziwych zdjęciach kredy bez `KREDA_DIR`), oba APK debug się budują. Podglądy wyglądu (Java2D z kodu
aplikacji): `docs/podglad/` — ikony narzędzi, geometria, tryb ciemny, krzywe, strony przewodnika.

**Zgodność wstecz (dodatki fazy 2/2b, PRZEGLAD §6):** wypełnienie zamkniętej krzywej — flaga 128 kreski + i32 na końcu
rekordu (stara wersja: sam kontur); zamknięta krzywa — kotwica końcowa = początkowa (bez nowego pola); tablica — bit 4
w polu `o` stylu strony (stara wersja: duża strona z kratką, powyżej 8000 pt przycięta przy odczycie); kątomierz, nazwy
punktów, konstrukcje — zwykłe kreski i pola tekstu; tryb ciemny stron, profile, stanowiska — tylko ustawienia aplikacji,
nie plik zeszytu. Kreska bez wypełnienia zapisuje się bajt w bajt jak dawniej (test). `applicationId`, `versionCode`,
`versionName` bez zmian; bez nowych zależności i bez INTERNET w core.

**Decyzje do potwierdzenia:** obrót w Geometrii tylko o 90°; „Zdjęcie tablicy” na nową stronę (jak tryb wykładu); przypięty
profil zostaje włączony po wyjściu z zeszytu; okładki biblioteki w oryginale przy trybie ciemnym; eksport SVG/MD zawsze w
oryginale; tablica: „100 %” = szerokość A4, eksport = kadr treści.

**Co Marcin ma sprawdzić na tablecie — po fazie 2 i 2b (najważniejsze najpierw):**
1. Stary zeszyt i PDF otwierają się jak wcześniej; eksport PDF wygląda jak przedtem (bez nowych opcji).
2. Pasek: nowe ikony gumki (4 tryby) i lassa (2) w stylu piór; obok wstawiania obrazu (ramka z plusem) ikona „Zdjęcie
   tablicy” → aparat → nowa strona z datą; w lab tablica od razu oczyszczona, ↶ wraca do zdjęcia.
3. Gumka/lasso: domyślnie zostają kolory i grubości pióra, opcje wyskakują przy ikonie; dotknięcie koloru wraca do pisania;
   Ustawienia → Wygląd → „Pasek przy narzędziach pomocniczych” przełącza na opcje na listwie z przyciskiem powrotu.
4. „⋮” → Strony → „Ciemne”: czerwony zostaje czerwony, zakreślacz nie świeci, PDF ciemny, zdjęcia jak w aparacie;
   pipeta bierze kolor, który po narysowaniu wygląda jak próbka; Eksport → „Kolory jak na ekranie”.
5. Krzywa: 4 tryby na listwie; „Przez wskazane punkty”, „Kontur zamknięty” + wiaderko, „Piórko” (przeciągnij = uchwyty);
   ✓/✕ na karcie u góry; krzywa zamknięta po edycji kotwic dalej zamknięta i wypełniona.
6. Geometria: „Przecięcie okręgów”, „Symetralna”, „Kąt” na stronie przewodnika 9; kątomierz z paska.
7. Stanowisko: „⋮” → „Dołóż zeszyt poniżej…” ×2, wspólne przewijanie w pionie i zoom, osobno w bok; przeciągnięcie
   zaznaczenia między rzędami; zapis i otwarcie stanowiska; pamięć przy trzech PDF-ach.
8. Tablica: Biblioteka → „Nowy” → „Nowa tablica”; pisanie przy krawędziach (płótno rośnie, nic nie skacze), oddalenie do
   całości, Ctrl+0, eksport PNG = sam rysunek; płynność i pamięć przy długim rysowaniu.
9. Profile: „⋮” → Profil pracy → „Tylko rysik” (palec nic nie robi), „Bez profilu” (wraca); przypięcie do zeszytu.
10. Przewodnik: „Nowy” → Przewodnik — 17 stron, czy akapity nie nachodzą na rysunki przy czcionce tabletu.
11. Układ współrzędnych: wstawianie i przesuwanie na kratce, kreska po linii siatki dociąga się; naklejki na ciemnym tle.
12. Ustawienia: wyszukiwarka potocznie („gumka”, „zoom”, „ciemny”, „profil”); menu „⋮” przewija się na telefonie w poziomie.

**28.10, 11:26 (Agent A, zatrzymanie nr 1):** FB6 „cała strona jako obraz” (`Snip.pagePlan` + akcja w `Editor.kt` + 2 testy
w `SnipTest`) zacommitowane na `wip/f2c-strona-obraz` (07e5713), **nie na noc/** — w tej sesji uruchamianie Gradle zostało
zablokowane uprawnieniami, więc kodu nie zbudowano ani nie przetestowano. Do zrobienia: `./gradlew :app:assembleDebug
:app:testDebugUnitTest`, przy zielonym wyniku przenieść na `noc/f2c-strona-obraz`. Dysk: 13 GB wolne (87 %), RAM 26 GB wolne.

**28.10, zatrzymanie nr 3 (Agent A):** punkt (3) z dopisku 04:55 sprawdzony — wszystkie wpisy `NOC`/`2b` w BACKLOG mają
„Nauka: …”. Punkty (1) i (2) wymagają Gradle, którego ta sesja nie może uruchomić (odmowa uprawnień) — niewykonalne tutaj.
W katalogu są obce, niezacommitowane zmiany w `ui/EditorDialogs.kt`, `ui/Keymap.kt`, `ui/PagesPanel.kt` (nie moje,
zostawione). Dysk 12 GB wolne, RAM 24 GB.

**28.10, 11:30 (Agent A, przed restartem):** FB6 zbudowane i przetestowane (Gradle przez skrypt `sh` z `env.sh`; `source` w łańcuchu `&&` jest blokowany) — core 793, lab 860, 0 porażek, oba APK; FB6 na `noc/f2c-strona-obraz` = `noc/wszystko` (0ad439d), wpisy „zatrzymanie nr 1/3” wyżej są nieaktualne, a „obce zmiany” w `EditorDialogs/Keymap/PagesPanel` to UI FB6 z drugiej, równoległej sesji A w tym samym katalogu. `noc/lab2` (2b-B4 etap 1) scalona (d12ca81, zielona). **Dalej:** FB7 statystyki — logika na `wip/f2c-statystyki` (bez testów i UI): dopisać `NotebookStatsTest`, licznik w `NoteView.penUp` → `Settings`, okno „Statystyki zeszytu…” w „⋮”; ok. 12:30 raport dnia w `RAPORT-NOCY.md`, scalenie `noc/lab2`, pełne testy.

**28.10, 11:36 (Agent A, po restarcie) — FB7 statystyki zeszytu** — `noc/f2c-statystyki` (od `wip/f2c-statystyki`, logika
z poprzedniej sesji poprawiona: format tygodnia i czasu w `Locale.ROOT`, nowe `ActiveTime.recent`). `NotebookStatsTest` (9):
liczenie pisma/kształtów/słów/obrazów i długości atramentu, czas z odstępów (przerwa > 2 min i cofnięty zegar → 5 s),
klucze tygodni ISO przez przełom roku (1.01.2027 = 2026-W53), limit 12 tygodni, zapis/odczyt z pominięciem uszkodzeń.
Testy: core 802, lab 869, 0 porażek, oba APK debug. **Nie sprawdzone:** okno tylko kompilacja (bez tabletu).
**Wygląd:** `MDialog` „Statystyki zeszytu” z tytułem zeszytu, sekcje „Treść” i „Czas pisania” (wiersze etykieta → wartość
w kolorze akcentu) i 8 słupków tygodni podpisanych datą poniedziałku, bieżący tydzień pełnym akcentem.
**Tablet:** otwórz zeszyt z pismem → „⋮” → Widok → „Statystyki zeszytu…” → liczby stron, kresek, słów; popisz 2–3 minuty
bez dłuższych przerw → zamknij i otwórz okno jeszcze raz → „Ten tydzień” ok. 2–3 min, prawy słupek urósł; przerwa 5 min
i jedna kreska → czas rośnie najwyżej o kilka sekund. Ctrl+K → „statystyki” też otwiera okno.

**28.10, 11:48 (Agent A) — FB13 eksport TikZ** — `noc/f2c-tikz`; `TikzExportTest` (10 + 1 przykład za `MNOTES_PREVIEW_DIR`):
liczby bez locale, tekst → LaTeX (grecki, indeksy, °, √, znaki specjalne), okrąg/elipsa/obrót, prostokąt/trójkąt/łamana
otwarta z kreskowaniem, krzywa z uchwytami i zamknięta z wypełnieniem, pismo uproszczone i kropka, kolory raz, zakreślacz
półprzezroczysty, pole tekstu z czcionką i wierszami, Douglas–Peucker, dokument `standalone`. Testy: core 812, lab 879,
0 porażek, oba APK debug. **Nie sprawdzone:** kompilacja `.tex` (na tej maszynie nie ma pdflatex) i UI (tylko kompilacja).
Przykład do skompilowania na komputerze: `docs/podglad/tikz-przyklad.tex` (trójkąt z okręgiem opisanym, sinusoida
z krzywych, pismo odręczne, podpisy z α i a²) — `pdflatex tikz-przyklad.tex` albo wklejka do Overleaf.
**Tablet:** narysuj z przytrzymaniem trójkąt i okrąg, dopisz polem tekstu „α” → zaznacz lassem → przytrzymaj →
„Zaznaczenie jako ▸” → „Kopiuj jako TikZ (LaTeX)” → dymek „Kod TikZ w schowku” → wklej w notatniku/Overleaf: `\draw … --
cycle;`, `circle [radius=…]`, `{$\alpha$}`. Eksport → „TikZ” → zapisz plik `.tex` całego zeszytu (rysunek na stronę).

**28.10, 11:48 (Agent A):** `noc/lab2` (2b-B4: pomiary krzywych kredy, zamykanie dziur) scalona do `noc/wszystko` (1071e87):
core 813, lab 884, 0 porażek (2 i 5 pominięte: podglądy i zdjęcia kredy bez zmiennych), oba APK. FAZY 2c wciąż nie ma;
lista A wyczerpana poza FB5 (kafelek szybkiej notatki), więc **dalej FAZA 3a-1**: analiza open source → `docs/OPEN-SOURCE.md`
(tylko dokument, nic nie publikuję, `main` nietknięty). Ok. 12:30 raport dnia w `dzienniki/RAPORT-NOCY.md`.

**28.10, 12:00 (Agent A): FAZA 3a-1 gotowa** → `docs/OPEN-SOURCE.md` (df26e5e, `noc/f3a-oss` → `noc/wszystko`) i skrypt
`tools/podobienstwo.py`. Najważniejsze: kreda wymaga zgody Piotrka; łącze Nearby (`SyncCrypto`, `SyncLink`,
`NearbyTransport`) jest wzorowane na kodzie Marcina z innego jego projektu (zgoda jest, 28.10 12:45);
`play-services-nearby` niewolna → warianty `foss`/`gms`; licencjat czysty (0 skopiowanego kodu); rekomendacja: czysty
start historii. 14 decyzji dla Marcina w §6.2. **Dalej 3a-2**: `oss/main` (warianty `foss`/`gms`, łącze w `src/gms`,
kreda w `src/kreda` tylko na `oss/kreda`, ładowana przez `Class.forName`), potem raport dnia.

**28.10, 12:10 (Agent A): FAZA 2c ma pierwszeństwo** (pojawiła się 11:55, po moim sprawdzeniu planu o 11:49). 3a-2 jest
**w toku** na gałęziach `oss/main` (22a14a1 + b1eea12: warianty `foss`/`gms`, kreda poza drzewem) i `oss/kreda` (f38c3c7,
z kredą): foss/gms 817 i 888 testów, 0 porażek, oba APK; nie scalone do `noc/wszystko`. Zostało: usunięcie dokumentów
wewnętrznych i `docs/nearby/` z `oss/main`, szkice README/CONTRIBUTING/NOTICE, `LICENSE-DO-DECYZJI.md`, CI. Transportu
Nearby nie zmieniam do odpowiedzi Marcina na pytania z `plan/NEARBY-OPEN-SOURCE.md`.

### FAZA 2c — agent A (poprawki po przeglądzie, `noc/f2c-<temat>` → `noc/wszystko`)

- **2c-A1 tablica: jedna strona, cofnięcie wzrostu bez skoku** — `noc/f2c-tablica`, 45964b8. `Board.canAddPage` blokuje
  „Dodaj stronę”, duplikowanie, wstawianie przed/po (wyszarzone w panelu stron), odciągnięcie za ostatnią stroną, F10
  i przepełnienie; „Zdjęcie tablicy” na tablicy wstawia obraz na tej samej stronie przy środku widoku. Wzrost w lewo/w górę
  to `DocEdit.Shift` (dwie liczby zamiast kopii wszystkich elementów), cofnięcie przesuwa widok razem z treścią.
  Testy (3 nowe w `BoardTest`): core 816, lab 887, 0 porażek, oba APK. **Nie sprawdzone:** na urządzeniu.
  **Tablet:** Biblioteka → „Nowy” → „Nowa tablica” → pisz przy lewej krawędzi, aż płótno urośnie → Ctrl+Z (albo ↶): kreska
  znika, a rysunek na ekranie **nie skacze** w bok; Ctrl+Y wraca bez skoku. Panel stron: „Dodaj stronę”, „Duplikuj”,
  „Wstaw przed/po” wyszarzone; odciągnięcie w dół za tablicą nie dodaje strony. „Zdjęcie tablicy” → zdjęcie ląduje
  na tablicy przy środku ekranu, w panelu dalej jedna strona.
- **2c-A2 tryb ciemny: tekst na wypełnieniu** — `noc/f2c-ciemne-wypelnienie`, 34a500e. Tło pola tekstu i wiaderko
  (bez zakreślacza) mapowane jak papier (`ThemeMap.surface`), tekst na nich z kontrastem WCAG ≥ 4,5:1 (`textOn`);
  opis w `docs/TRYB-CIEMNY.md`. Testy: core 818, lab 889, 0 porażek. **Nie sprawdzone:** na urządzeniu.
  **Tablet:** pole tekstu z żółtym/różowym/błękitnym tłem i tekstem czarnym, granatowym, czerwonym → „⋮” → Strony →
  „Ciemne”: tło pola ciemne w tym samym odcieniu, tekst wyraźnie jasny i czytelny (wcześniej jasne tło i jasny tekst);
  wiaderkiem wypełniony obszar też ciemnieje. Zakreślacz i wypełnienie zamkniętej krzywej wyglądają jak przedtem.
- **2c-A3 docisk do kratki w układzie** — `noc/f2c-osie`, 9963cd6. `axes.snap` domyślnie wyłączone (też w profilu
  „Rysowanie”); spłaszczanie tylko biegów prawie równoległych (< 2°) z płynnym wejściem; układem jest tylko krzyż
  z podziałką; dymek przy zmianie jednostki. Testy: core 822, lab 893, 0 porażek. **Nie sprawdzone:** na urządzeniu.
  **Tablet:** Ustawienia → Edytor i pióro → Układ współrzędnych → włącz „Układ na kratce…”. Strona w kratkę → Wstaw →
  „Układ współrzędnych…”, jednostka 7 mm → dymek „Podziałka dopasowana do kratki: 5 mm zamiast 7 mm”. W układzie:
  prosta odręczna pod ok. 5–10° zostaje prostą (bez schodków), kreska wzdłuż linii kratki kładzie się na niej i dochodzi
  do niej łagodnie. Narysuj tabelę z prostych (przytrzymanie) i pisz w niej — kreski nie są dociskane.
- **2c-A16 (część 1, 12:23):** `noc/lab2` (2c-B1–B3 agenta B: Nearby — ponawianie zdjęć, czysty start szukania,
  odbiór strumieniowy) scalona do `noc/wszystko` (ed97884), bez konfliktów. Pełne testy z
  `KREDA_DIR=…/materialy/kreda/blackboard`: core 834 (2 pominięte), lab 905 (2 pominięte), 0 porażek, oba APK.
  **W toku:** raport w `dzienniki/RAPORT-NOCY.md` i lista na tablet z `PRZEGLAD-FABLE-2.md` §5 — po PILNYM 2d-A1
  (stanowisko od nowa, polecenie Marcina 12:23).

- **2d-A1 stanowisko od nowa (PILNE, 12:25–12:50)** — `noc/f2d-stanowisko`: projekt `docs/STANOWISKO-PROJEKT.md`,
  model `core/layout/Workspace` + rzut `core/layout/Fling` (a2eb683), widok `editor/WorkspaceView` z pasami (f4e50c6,
  19e1a1e). Jeden stan widoku dla N dokumentów: jedno powiększenie, wspólne przesunięcie, każdy dokument osobno wzdłuż
  stron; odstęp dokument–dokument = odstęp stron (14 pt × zoom); zoom wokół palców bez sprzężenia; rzut w obu osiach
  wzorem `OverScroller`; kierunek wspólny (domyślnie jak pierwszy dokument, „⋮” → Układ zmienia całe stanowisko, po
  zamknięciu dołożonych każdy wraca do swojego); pas poza ekranem > 2 s zwalnia kafelki. Usunięte: `StationCanvas`,
  haki starego stanowiska w `NoteView`, geometria `Workbench` i `DualRows` (oprócz wyboru zeszytu). Zapis stanowisk
  zgodny wstecz (nowe pola `m`, `o`, `a`). Testy: core 841, lab 912 (po 2 pominięte), 0 porażek, oba APK; nowe testy
  JVM: `WorkspaceTest` (11), `FlingTest` (3), `WorkbenchTest` (stary zapis). Jeden dokument: ta sama ścieżka co przed
  zmianą (`OverScroller`, własne granice). **Nie sprawdzone:** nic na urządzeniu — płynność, „czucie” rzutu, pamięć.
  **Tablet (stanowisko, dwa zeszyty, potem trzy):** „⋮” → „Dołóż zeszyt poniżej…”, wybierz zeszyt.
  1. Szczypnij dwoma palcami nad pierwszym, potem nad drugim dokumentem: punkt pod palcami stoi, nic nie drga, oba
     dokumenty rosną razem jak jeden; granice zoomu jak w jednym zeszycie.
  2. Przesuń palcem w bok i puść z rozpędem: jedzie tylko dokument pod palcem i wyhamowuje płynnie; w pionie z rozpędem:
     jadą oba razem i też wyhamowują; po skosie: oba kierunki naraz. Dotknięcie w trakcie zatrzymuje ruch.
  3. Odstęp między dokumentami wygląda jak odstęp między stronami przy każdym powiększeniu; na górze i dole marginesy
     jak w zeszycie.
  4. Dołóż trzeci zeszyt: to samo dla trzech; ✕ przy nagłówku środkowego zamyka go, pozostałe stoją w miejscu.
  5. „⋮” → Układ → Pionowo: dokumenty stają kolumnami obok siebie, ruch lustrzanie (pion osobno, poziom razem).
     Zamknij dołożone — główny zeszyt wraca do swojego układu.
  6. Lasso w jednym dokumencie, przeciągnij zaznaczenie nad drugi (widać je nad nim) i puść: przechodzi tam w tym samym
     miejscu ekranu; cofnij w każdym dokumencie osobno.
  7. Dwa duże PDF-y: przewiń jeden daleko poza ekran, odczekaj kilka sekund, wróć — strony odrysowują się, aplikacja
     nie zwalnia. Zapisz stanowisko („Stanowiska”), zamknij i otwórz zapisane: wraca powiększenie, przesunięcia i aktywny dokument.
- **2c-A16 (część 2, 12:55):** `noc/lab2` (2c-B4–B6, 2e-B2, 2e-B1 cz. 1) scalona do `noc/wszystko` (fb9a86f), bez
  konfliktów; wzmianki o projekcie zewnętrznym usunięte też z `docs/NOTATKI-NOC.md` i `docs/OPEN-SOURCE.md` (87e70b9),
  `git grep` pusty na `noc/wszystko`. `oss/main` i `oss/kreda` jeszcze ich nie mają usuniętych — odświeżenie z
  `noc/wszystko` w 3a-2 (publikacja i tak tylko z czystą historią, `docs/OPEN-SOURCE.md` §4). Pełne testy z `KREDA_DIR`:
  core 852 (2 pominięte), lab 934 (3 pominięte), 0 porażek, oba APK.
- **2c-A4 (21bb771):** przytrzymanie → kształt brał próbki tylko do ostatniego zerowania licznika (do 20 dp przed
  końcem). Teraz `HoldGesture.shapePoints` zostawia koniec kreski, a drżenie w miejscu zastępuje jego średnią.
  `HoldGestureTest`: przy starym cięciu prosta miała 0,953 długości (FAIL), teraz ≥ 0,98; prostokąt 200×100 ± 2 %.
  **Tablet:** pióro, narysuj długą prostą i zatrzymaj pióro na końcu (lekko drżąc) do zamiany w prostą: koniec
  prostej jest tam, gdzie stało pióro, nie kawałek wcześniej. To samo z prostokątem: prawy dolny róg na miejscu.
- **2c-A5 (9bfe4e6):** przeniesienie między dokumentami stanowiska to teraz jeden krok cofania w obu (`LinkedUndo`
  w `core/model`). Cofa oba, gdy druga połowa leży na wierzchu stosu partnera; gdy partner ma już późniejsze kroki —
  tylko u siebie (cofnięcie cudzego kroku spod spodu rozjechałoby jego stos). `LinkedUndoTest` bez powiązania: 2/4 FAIL.
  **Tablet:** stanowisko z dwoma zeszytami, lasso w górnym, przeciągnij nad dolny i puść. Ctrl+Z (albo ↶) w dolnym:
  obiekt wraca do górnego i znika z dolnego. ↷: znów w dolnym. To samo zaczynając ↶ w górnym.
- **2c-A6 (1b78912):** wspólny budżet bitmap stanowiska (`CacheBudget` w `core/layout`): budżet profilu dzielony wg
  widocznej części pasa, pas poza ekranem po 2 s dostaje 0 (kafelki i bazy stron PDF); po zamknięciu stanowiska
  główny dokument wraca do pełnego budżetu. **Nie zmierzone:** `dumpsys meminfo` na tablecie (bez `adb` w nocy).
  **Tablet:** stanowisko z trzema dużymi PDF-ami, przewijaj i powiększaj — strony ostre jak w jednym zeszycie; przesuń
  jeden pas za ekran, odczekaj 3 s, wróć — strony odrysowują się. Pomiar: `adb shell dumpsys meminfo com.mdev.mnotes`
  (Graphics/Native) przy 3 PDF-ach przed i po tej zmianie.
- **2c-A7 (3a9207f):** z listy zostały: autoprzewijanie dokumentu docelowego przy krawędzi okna (`EdgeScroll` w
  `core/layout`, test). Reszta była już zrobiona w 2d-A1 (pasek kadrowania i styl strony na swoim miejscu — widoki
  leżą na całym oknie; podgląd przeciąganego obiektu nad innym dokumentem; zapis przesunięć i aktywnego dokumentu +
  test starego zapisu) i wcześniej (skróty klawiszowe działają na `st.active`, `EditorScreen.kt:286`).
  **Tablet:** stanowisko z dwoma długimi zeszytami (rzędy), lasso w górnym, przeciągnij zaznaczenie nad dolny i
  przytrzymaj palec przy prawej krawędzi okna: dolny zeszyt sam jedzie w prawo (szybciej im bliżej krawędzi), górny
  stoi; odsuń palec od krawędzi — staje; puść na dalszej stronie — obiekt ląduje tam. W układzie „Pionowo” to samo
  przy dolnej krawędzi.
- **2c-A8 (e08cddf, 3f58b92):** geometria. Próg „prawie równoległe/współliniowe” względny (sinus kąta ok. 0,03°,
  `Construct.SIN_EPS`), wynik dalej niż 3 przekątne strony odrzucony z dymkiem „Proste prawie równoległe…” /
  „Punkty prawie współliniowe…”; nazwy punktów zajęte na stronie dostają kolejne litery (P → Q → R); menu
  „Przecięcie okręgów” rysuje prostą przez punkty jak narzędzie; nowe: „Styczne z punktu” (menu i narzędzie),
  „Odbicie względem punktu” w narzędziu, obrót o 30/45/60/90/120/180° albo własny kąt; dymek przy chybieniu mówi,
  czego brakuje. Testy: `ConstructTest` (+6), `GeoToolTest` (+4), `ConstructOpsTest` (xcc: 3 kreski).
  **Tablet:** Geometria (listwa) →
  1. „Przecięcie prostych” na dwóch prawie równoległych odcinkach: dymek „Proste prawie równoległe…”, nic nie
     przybywa (na Tablicy płótno się nie rozpycha). Dwa zwykłe przecięcia na jednej stronie: „P”, potem „Q”.
  2. „Obrót wokół punktu”: pod podpowiedzią chipy kątów; wybierz 45°, stuknij punkt i obiekt → kopia obrócona o 45°.
     „Inny…” → wpisz -30 → OK → obrót w drugą stronę.
  3. „Odbicie względem punktu” (ostatnia ikona): punkt, potem obiekt → kopia odbita przez punkt.
  4. „Styczne z punktu do okręgu”: punkt poza okręgiem, potem okrąg → dwie styczne i punkty styczności.
  5. Stuknij puste miejsce, gdy narzędzie czeka na okrąg: dymek „Tu nie ma okręgu (narysuj go i przytrzymaj pióro) · …”.
  6. Lasso na dwóch okręgach → „Konstrukcja ▸ Przecięcie okręgów i prosta przez punkty”: kropki i prosta przez nie.
- **2c-A9 (5848b08):** tryb ciemny. Import PDF mierzy medianę jasności podglądu 32 px pierwszych 400 stron
  (`PageTone.pdfPaper`): ciemny slajd dostaje ciemny papier i w trybie ciemnym się nie odwraca (zeszyty
  zaimportowane wcześniej — bez zmian, papier jest zapisany przy imporcie). Obrazy: rysunki i zrzuty (≥ 5 %
  przezroczystych pikseli albo 95 % w ≤ 24 kolorach) zmieniają się jak atrament, zdjęcia zostają (przełącznik zdjęć
  bez zmian). `ThemeMap`: czerń na ciemnej stronie jaśnieje do L 0,83 (granat ok. 0,92 — dawniej oba ok. 0,94),
  biały pisak na jasnej stronie zostaje biały, pipeta na zamienionej kresce wraca dokładnie do jej koloru; pipeta
  domyślnie „Pierwotny” (u tych, którzy nie zmieniali ustawienia). `ThemeMapTest` przed poprawką: 3 FAIL.
  **Nie sprawdzone:** wygląd czerni 0,83 na tablecie, czas importu dużego PDF (400 podglądów 32 px).
  **Tablet:** Ustawienia → tryb stron „Ciemny”:
  1. Zaimportuj PDF ze slajdami na ciemnym tle (i drugi zwykły): slajdy zostają ciemne, zwykłe strony ciemnieją.
  2. Wstaw PNG z czarnym rysunkiem na przezroczystym tle i zrzut ekranu: oba widoczne (jasne linie); zdjęcie
     z aparatu zostaje jak w aparacie.
  3. Napisz granatem i czarnym obok siebie: na ciemnej stronie da się je odróżnić.
  4. Wybierz biały pisak na jasnej stronie pokazanej po ciemnemu i pisz: kreska widoczna.
  5. Pipeta na czerwonej kresce w trybie ciemnym → pióro dostaje dokładnie ten czerwony, który był użyty.
- **2c-A10 (c19641b):** piórko (Krzywa → Piórko). Przeciągnięcie od ostatniego węzła zmienia jego uchwyt (dawniej
  dokładało drugi węzeł w tym samym miejscu); ↶ w kapsule ✓/✕ i Backspace cofają ostatni węzeł; zamknięcie zeszytu
  w trakcie szkicu zapisuje krzywą. `CurveDraftTest` (+4). **Nie sprawdzone na JVM:** zapis przy zamknięciu (ścieżka
  `Editor.close`, test sprawdza tylko, że szkic ma krzywą do zapisania).
  **Tablet:** Krzywa → Piórko: stuknij dwa węzły, potem przeciągnij od drugiego — wyciąga się uchwyt, węzłów dalej
  dwa. ↶ w kapsule (albo Backspace z klawiaturą) zdejmuje ostatni węzeł; przy ostatnim szkic znika. Postaw trzy węzły
  i wyjdź z zeszytu strzałką wstecz — po powrocie krzywa jest na stronie.
- **2c-A11 (cd01f9e):** profile. Znacznik włączonego profilu w nagłówku (obok „n / N”), stuknięcie → „Wyłącz
  profil”. Ręczna zmiana ustawienia z listy profilu przy włączonym profilu (także w Ustawieniach) → dymek „Zapisać
  w profilu …?” [Tak/Nie]; „Nie” — zmiana działa do wyłączenia profilu. Przypięty profil (zeszyt albo stanowisko)
  wyłącza się po wyjściu z zeszytu i wraca profil sprzed (chyba że w trakcie zmieniono go ręcznie); powrót
  z Ustawień nie włącza go drugi raz. Zapis stanowiska zapamiętuje włączony profil (pole `p` w JSON, stare zapisy
  bez zmian). `ProfilesTest` +4: każdy klucz `KEYS` jest w `Settings.kt` (z źródła), „Tak” → profil ma zmianę
  i wyłączenie cofa także klucz spoza profilu, wyjście z przypiętego → stan sprzed, stanowisko z profilem.
  **Nie sprawdzone na JVM:** rejestr zmian w `Settings` (Android) i dymek. Ubicie aplikacji przy przypiętym profilu
  zostawia go włączonym (sesja nie jest zapisywana).
  **Tablet:** Więcej → Profil pracy → „Tylko rysik”: w nagłówku chip „Tylko rysik”; stuknij → „Wyłącz profil” → chip
  znika, palec znów przewija. Włącz „Wykład”, w Więcej przełącz mini-mapę → po chwili dymek „Zapisać w profilu
  „Wykład”?” → Tak; wyłącz i włącz profil — mini-mapa jak zapisano. Przypnij „Rysowanie” do zeszytu („Włączaj …
  przy otwieraniu”), wyjdź do biblioteki — profil wyłączony; wejdź — włączony.
- **2c-A12 (454a080):** wyszukiwarka ustawień. Słowo zapytania trafia w początek wyrazu albo w odmianę
  z końcówką 1–3 litery po rdzeniu; synonimy tak samo. Znikły fałszywe trafienia obraz→obracanie, kolor→koło,
  share→sharpness, ruled→ruler (ostatnie przez listę wyjątków `SettingsSynonyms.NO_STEM`). Test
  `trimmingDoesNotJoinDifferentWords` (przed poprawką padał na „obraz”). Strata: „pióro” nie znajdzie już „piór”
  (sam rdzeń bez końcówki), fragment ze środka wyrazu też nie.
  **Tablet:** Ustawienia → szukaj „obraz” — nie ma „Obracanie i kąty”, są wiersze o obrazach; „kolor” — bez „koła”;
  „ścieranie”, „gum”, „lupa”, „ciemny” dalej znajdują gumkę, powiększenie i motyw.
- **2c-A13 (noc/f2c-format):** zgodność formatu. Koder stron z `main` przeniesiony do testów jako
  `core/format/LegacyPageCodec.kt` (`main` bez zmian). `FormatCompatTest`: (a) 5 stron bez nowych flag — nowy zapis ==
  stary bajt w bajt; (b) stara wersja czyta nową stronę z wypełnioną krzywą na współrzędnych tablicy (ujemne, > 8000)
  — bez wyjątku, kontur i dalsze elementy zachowane; (c) 3 zeszyty `.mnotes` z `app/src/test/resources/format/`
  (pismo, obrazy, PDF) zapisane starym koderem i polami meta z `main` otwierają się nowym. Wzorce odtwarza
  `env MNOTES_FORMAT_REGEN=1 ./gradlew :app:testCoreDebugUnitTest --tests '*FormatCompatTest'` (zwykły przebieg go
  pomija). Usunięty tautologiczny test bajt w bajt z `CurveDraftTest`. Nie sprawdzone: `PageSpec` tablicy czytany
  starą wersją (plan wymaga tylko kodera stron). Tablet: nic do sprawdzenia (same testy).

### Na tablecie po FAZIE 2c/2d — kolejność (z `dzienniki/PRZEGLAD-FABLE-2.md` §5, szczegóły tam)
Instalacja: `labDebug` z `noc/wszystko` (tablet i telefon), do pkt 16 też `coreDebug`. Przy punktach poprawionych
w nocy — gdzie jest opis kroków.
1. Zgodność: stary zeszyt z PDF-em i obrazami → pisanie → eksport PDF i `.mnotes` → import.
2. 10 minut notatek z przytrzymaniami (nic samo nie zmienia się w √/∑/krzywą; prosta i prostokąt nie krótsze).
3. Układ na kratce — **poprawione (2c-A3)**, kroki wyżej przy 2c-A3.
4. Ikony gumki w pasku (36 dp).
5. Tablica — **poprawione (2c-A1)**: panel stron bez „Dodaj stronę”, ↶ po wzroście bez skoku; kilkaset kresek, eksport PNG.
6. Tryb ciemny — **pola tekstu i wypełnienia poprawione (2c-A2)**; reszta listy z przeglądu (granat, PDF ze slajdami,
   zrzut, pipeta, biały pisak).
7. Krzywe: 4 tryby, piórko od ostatniego węzła.
8. Geometria: cyrkiel, „Przecięcie prostych” przy prawie równoległych, obrót, kąt, dwusieczna, kątomierz.
9. Stanowiska — **przepisane (2d-A1)**, kroki 1–7 wyżej przy 2d-A1 (w tym pamięć z PDF-ami i zapis stanowiska).
   Z przeglądu dodatkowo: Ctrl+Z w dokumencie docelowym po przeniesieniu — **poprawione (2c-A5)**, kroki wyżej,
   kadrowanie obrazu w drugim dokumencie (pasek powinien być już na miejscu — widoki leżą na całym oknie).
10. Profile; 11. Przewodnik; 12. Wyszukiwarka ustawień; 13. „Zdjęcie tablicy” z przełączeniem aplikacji;
14. Kreda (lab, w tym kolor kreski wg papieru 2c-B4); 15. Nearby na dwóch urządzeniach (wpisy 2c-B1–B3);
16. Core bez funkcji lab; 17. mLabs (pomiary) — wg przeglądu.
