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
