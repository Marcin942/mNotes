# mNotes — BACKLOG (jedyne źródło prawdy o zakresie i postępie)

Cel: odpowiednik GoodNotes na Androida (rysik/tablet/DeX), **bardzo estetyczny** i **„po linuksowemu” wygodny** (Linux Mint: motyw Mint-Y, skróty klawiszowe, menu kontekstowe, otwarte formaty, prywatność, pełna konfigurowalność).
Legenda: `[x]` zrobione · `[~]` częściowo · `[ ]` do zrobienia · `[-]` poza zakresem. Priorytety P0 (v1) … P3. Szczegóły EDT: `02-edytor-narzedzia.md`.

Założenie projektowe: „Linux Mint user” = osoba ceniąca kontrolę, skróty klawiszowe, otwarte pliki, brak kont/telemetrii i spójny, spokojny wygląd (Mint-Y: zieleń #87CF3E, szarości, zaokrąglone 6 dp). Platforma pozostaje Androidem (tablet/DeX/Chromebook).

## A. Wygląd i motyw (LNX / CUS)
- [x] LNX-001 Motyw „Mint-Y” jasny/ciemny/AMOLED + podążanie za systemem, akcent Mint domyślnie (alternatywy: Aqua, Blue, Orange, Pink, Purple, Red, Sand, Teal)
- [x] LNX-002 Gęstość UI: komfortowa / kompaktowa (jak Nemo/Cinnamon)
- [x] LNX-003 Typografia i ikony spójne (Material Symbols Rounded-like), animacje 120–200 ms, brak migotania przy starcie
- [x] CUS-001 Ustawienia: zakładki (Wygląd, Wejście, Narzędzia, Strony, Kopie, Bezpieczeństwo, Skróty, O aplikacji)
- [x] CUS-002 Pozycja paska narzędzi: góra / dół / lewo / prawo, rozmiar ikon, pływający pasek
- [x] CUS-003 Język PL/EN (zasoby), jednostki mm/pt
- [x] CUS-004 Eksport/import ustawień jako JSON (dotfile)

## B. Biblioteka (LIB) — układ jak menedżer plików Nemo
- [x] LIB-001 Zeszyty i foldery zagnieżdżone, nawigacja ścieżką (breadcrumb), panel „Miejsca” (Biblioteka, Ostatnie, Ulubione, Foldery, Tagi, Kosz)
- [x] LIB-002 Widoki: ikony / lista / kompaktowy + suwak rozmiaru ikon; sortowanie (nazwa, zmiana, utworzenie, ręczne)
- [x] LIB-003 Okładki: kolor/gradient + wzór + miniatura pierwszej strony; edycja okładki
- [x] LIB-004 Nowy zeszyt (tytuł, rozmiar, orientacja, szablon, papier, okładka), nowy folder
- [x] LIB-005 Zmiana nazwy, duplikuj, przenieś (dialog + przeciągnij), ulubione, przypięte
- [x] LIB-006 Zaznaczanie wielu, akcje zbiorcze (przenieś, usuń, eksportuj, taguj)
- [x] LIB-007 Kosz: 30 dni, przywracanie, opróżnianie
- [x] LIB-008 Tagi (kolorowe) + filtrowanie
- [x] LIB-009 Wyszukiwanie globalne: tytuły, tagi, tekst z pól tekstowych, zakładki, spis treści
- [~] LIB-010 Import: PDF (adnotacje), obrazy (jako strony), `.mnotes`; „Otwórz w mNotes” z innych aplikacji (intent) _(import (PDF/obrazy/.mnotes) i intenty VIEW/SEND/CREATE_NOTE zaimplementowane — brak testu na urządzeniu)_
- [x] LIB-011 Menu kontekstowe (prawy przycisk / długie przytrzymanie)
- [x] LIB-012 Pusty stan / onboarding / szybka notatka (skrót, CREATE_NOTE intent)
- [~] LIB-013 Blokada zeszytu (biometria/PIN) _(zaimplementowane (biometria/PIN) — brak testu na urządzeniu)_

## C. Strony i szablony (PAG)
- [x] PAG-001 Rozmiary: A4/A5/A6/B5/Letter/Legal/Tablet/Kwadrat/własny, pion/poziom
- [x] PAG-002 Szablony: puste, linie, kratka, kropki, izometryczne (kropki/siatka), Cornell, pięciolinia, lista zadań; rozstaw, grubość, kolor, margines
- [x] PAG-003 Kolory papieru (biały, kremowy, żółty, szary, niebieski, zielony, różowy, ciemny, czarny)
- [x] PAG-004 Dodaj stronę (po bieżącej / na końcu / przed), duplikuj, usuń, przenieś (przeciąganie w panelu stron), wyczyść
- [x] PAG-005 Zmiana szablonu strony / całego zeszytu
- [x] PAG-006 Adnotacja PDF: strony PDF jako tło (PdfRenderer), zachowanie rozmiaru
- [~] PAG-007 Wstaw strony z PDF/obrazów do istniejącego zeszytu _(wstawianie stron z PDF/obrazów do istniejącego zeszytu — do sprawdzenia)_
- [x] PAG-008 Kopiuj/wklej stronę, także między zeszytami (kopiuje obrazy i podkład PDF) _(Ctrl+Shift+C/V, menu miniatury; bez testu na urządzeniu)_
- [x] PAG-009 Obraz jako podkład strony (EDT-056) — zablokowany `ImageEl` na spodzie; bez testu na urządzeniu

## D. Edytor — narzędzia (EDT; szczegóły w 02-…)
- [x] EDT-076 Rysowanie na żywo, predykcja ruchu (androidx.input), cache rastrowy stron
- [x] EDT-070/071 Odrzucanie dłoni, tryb palca (przewijaj / rysuj / ignoruj)
- [~] EDT-072 Przycisk S Pen: mapowanie akcji _(wykrywanie przycisku S Pen w NoteView; mapowanie akcji w ustawieniach do przejrzenia)_
- [x] EDT-073 Hover: pierścień rozmiaru gumki/pióra
- [x] EDT-075 Mysz/touchpad/DeX: kółko, Ctrl+kółko, środkowy, prawy przycisk
- [x] EDT-001..003 Pióra: kulkowe, wieczne, pędzel (nacisk, zwężenia)
- [x] EDT-004 Zakreślacz (multiply, kwadratowe końce)
- [x] EDT-008 Ołówek
- [x] EDT-005/006 Wygładzanie/stabilizator, krzywa nacisku
- [x] EDT-009 Próbnik pióra — `PenTester` w panelu pióra; bez testu na urządzeniu
- [x] EDT-010 Gumka obiektowa · EDT-011 pikselowa · EDT-012 opcje (ignoruj zakreślacz) · EDT-013 wyczyść stronę · EDT-014 gumka lasso
- [x] EDT-080 Presety piór (sloty, ulubione kolory) · EDT-081 popover koloru i grubości · EDT-083 szybkie przełączanie
- [x] EDT-084 Pipeta (próbnik koloru ze strony) _(klawisz I lub przycisk w selektorze koloru; lupa z hexem; próbkuje bitmapę strony; Esc/PPM anuluje; nie działa w dialogach modalnych; bez testu na urządzeniu)_
- [x] EDT-085 Nowa paleta kolorów — stonowane atramenty (3×8) i osobna, pastelowa paleta zakreślaczy (miniatury pokazują efekt na papierze; ciemne barwy są łagodzone) _(bez testu na urządzeniu)_
- [x] EDT-020/021 Lasso swobodne/prostokątne · EDT-022 uchwyty (przesuń, skaluj, obróć) · EDT-023 pasek kontekstowy
- [x] EDT-024 wytnij/kopiuj/wklej/duplikuj (schowek wewn.) · EDT-025 kolor/grubość zaznaczenia · EDT-027 kolejność/blokada · EDT-029 wyrównanie
- [x] EDT-030..032 Auto-kształty „rysuj i przytrzymaj” (linia, okrąg/elipsa, prostokąt, trójkąt, strzałka)
- [x] EDT-035 Linijka _(klawisz R; pióro przylega do krawędzi, podziałka mm/cale, obrót uchwytem/dwoma palcami, magnes 45°, Alt+kółko obraca; bez testu na urządzeniu)_
- [x] EDT-037 Siatka przyciągania
- [x] EDT-040/041 Pole tekstowe + formatowanie (font, rozmiar, kolor, B/I/U/S, wyrównanie) · EDT-043 tło pola
- [x] EDT-042 Listy punktowane/numerowane w polu tekstowym — znaczniki tekstowe, `ListText`; bez testu na urządzeniu
- [x] EDT-050 Obraz z galerii
- [x] EDT-053 Edycja obrazu (obrót, krycie, blokada) _(uchwyt obrotu, suwak „Krycie obrazu”)_
- [x] EDT-054 Wklej obraz/tekst ze schowka systemu _(Ctrl+V przy pustym schowku wewnętrznym; bez testu na urządzeniu)_
- [x] EDT-060/061 Laser (znikający ślad) · EDT-062 Ręka
- [x] EDT-064 Taśma (ukrywanie treści) — `TapeEl`, narzędzie K; bez testu na urządzeniu
- [x] EDT-065 Wstaw odstęp (przesunięcie treści w dół) _(narzędzie X: przeciągnij od linii w dół = wstaw, w górę = usuń puste miejsce; ogranicza do strony i do treści nad linią; zablokowane stoją; `core/model/PageSpace.kt` + test; bez testu na urządzeniu)_
- [x] EDT-090 Warstwy/rejestr typów elementów (model)
- [x] Undo/redo bez limitu praktycznego (500), ponawianie, grupy

## E. Nawigacja i widok (NAV)
- [x] NAV-001 Zoom/pan (pinch, kółko, Ctrl+kółko, przeciągnięcie), dopasuj do szerokości/strony, zoom do zaznaczenia
- [x] NAV-002 Układ: ciągły pionowy / poziomy / pojedyncza strona
- [x] NAV-003 Panel miniatur stron (przeciąganie, menu, dodaj)
- [x] NAV-004 Spis treści (Outline) i zakładki
- [x] NAV-005 Wyszukiwanie w zeszycie (tekst z pól, nazwy zakładek)
- [x] NAV-006 Tryb prezentacji (ukryj UI) / pełny ekran (F11) / tryb tylko do odczytu
- [x] NAV-007 Przejdź do strony (Ctrl+G), PgUp/PgDn, Home/End
- [x] NAV-008 Multi-window, wiele zeszytów obok siebie _(`NoteWindowActivity`, Ctrl+Alt+N, „Otwórz w nowym oknie” w bibliotece; ten sam zeszyt tylko w jednym oknie; bez testu na urządzeniu)_
- [x] NAV-009 Odciągnięcie za ostatnią stroną dodaje nową stronę _(gest zaczęty na końcu dokumentu; opór, pasek postępu, haptyka; pion i poziom; opcja w Ustawieniach → Wejście; bez testu na urządzeniu)_

## F. Pliki, kopie, wymiana (FIL)
- [x] FIL-001 Zapis automatyczny (debounce) + zapis przy wyjściu, odporność na crash (zapis atomowy)
- [~] FIL-002 Eksport PDF (adnotacje wektorowe), PNG (strona/zakres), SVG (wektor), Markdown/TXT z pól tekstowych _(PDF/SVG sprawdzone; PNG i Markdown z edytora bez testu na urządzeniu; Markdown tylko pola tekstowe)_
- [~] FIL-003 `.mnotes` (ZIP: manifest JSON + strony + zasoby), import/eksport, udostępnianie (Share) _(`.mnotes` zaimplementowane; Share bez testu na urządzeniu)_
- [~] FIL-004 Folder synchronizacji (SAF) — lustro w otwartych plikach dla Syncthing/Nextcloud/rsync; kopia ręczna i automatyczna _(folder synchronizacji (SAF, lustro PDF/MD/.mnotes) zaimplementowany; brak testu zeszytów zablokowanych i zagnieżdżonych folderów)_
- [~] FIL-005 Drukowanie (PrintManager) _(PrintManager zaimplementowany — brak testu na urządzeniu)_
- [x] FIL-006 Przeciągnij i upuść obrazy/PDF do aplikacji _(edytor: obrazy; biblioteka: PDF/obrazy/.mnotes; bez testu na urządzeniu)_

## G. Bezpieczeństwo i prywatność (SEC)
- [x] SEC-001 Brak uprawnienia INTERNET, brak telemetrii (zrobione w manifeście)
- [~] SEC-002 Blokada aplikacji i zeszytów: biometria + PIN, auto-blokada, FLAG_SECURE (opcja) _(zaimplementowane (blokada aplikacji, FLAG_SECURE) — brak testu na urządzeniu)_
- [x] SEC-003 Odzyskiwanie po awarii (pliki tymczasowe, kopie `.bak` stron) _(uszkodzona strona → `.corrupt` + odtworzenie z `.bak` + komunikat; bez testu na urządzeniu)_

## H. Skróty i „linuksowa” wygoda (CNV / LNX)
- [x] LNX-010 Pełna tabela skrótów (Ctrl+Z/Y, Ctrl+C/X/V/D, Ctrl+A, Del, Ctrl+F, Ctrl+N, Ctrl+Shift+N, Ctrl+E, Ctrl+S, Ctrl+P, Ctrl+±/0, PgUp/PgDn, F11, F2, Esc, litery narzędzi P/H/E/L/T, 1–9 presety, [ ] grubość, X zamiana)
- [x] LNX-011 Paleta poleceń (Ctrl+Shift+P / Ctrl+K) z wyszukiwaniem wszystkich akcji i ich skrótami
- [~] LNX-012 Pasek menu (Plik, Edycja, Widok, Wstaw, Strona, Narzędzia, Pomoc) na dużych ekranach _(pasek menu zaimplementowany; render potwierdzony, rozwijane menu do sprawdzenia na urządzeniu)_
- [~] LNX-013 Menu kontekstowe prawym przyciskiem w bibliotece i edytorze; podpowiedzi z klawiszami _(menu kontekstowe w edytorze i bibliotece; podpowiedzi skrótów w paletach i menu)_
- [x] LNX-014 Nawigacja klawiaturą po bibliotece i dialogach _(strzałki/Home/End/Shift+strzałki, Enter, F2, Del, Backspace w bibliotece — siatka i lista, z przewijaniem do kursora; Tab w dialogach domyślnie z Compose; bez testu na urządzeniu)_
- [~] LNX-015 Edycja skrótów (przypisanie własnych) _(edytowalna zakładka „Skróty” + dialog przechwytywania; do sprawdzenia na urządzeniu)_
- [x] LNX-016 Tryb „biurkowy”: zadokowany panel stron przy szerokim oknie ≥ 840 dp (Ctrl+F9 / Ustawienia) _(biblioteka-edytor-panel; bez testu na urządzeniu)_

## I. Ponad GoodNotes (EXT)
- [x] EXT-001 Paleta poleceń (jw.), zakładki szybkie, eksport SVG
- [ ] EXT-002 Zapis nagrania audio zsynchronizowany z pisaniem (P3) _(brak (P3))_
- [x] EXT-003 Okno powiększenia (zoom window, P2) _(Ctrl+Alt+Z; pisanie w powiększeniu u dołu ekranu, `ZoomRegion`; bez testu na urządzeniu)_
- [~] EXT-004 Kopia do folderu synchronizacji jako otwarte pliki (jw.) _(jw. (FIL-004))_

## J. Estetyka „Apple-grade” (AES) — cel: co najmniej tak piękne jak GoodNotes / Apple Notes
- [x] AES-001 Zakreślacz jak prawdziwy marker: `BlendMode.MULTIPLY` (API ≥ 29; poniżej: kolor wstępnie rozjaśniony + `PorterDuff.MULTIPLY`), końce okrągłe, ciemny papier → `SCREEN` (α·0,84), nowa pastelowa paleta 10 kolorów (OKLCH, w gamucie sRGB), łagodzenie zbyt ciemnych barw _(render/PageRenderer.styleHighlighter; SVG: mix-blend-mode multiply/screen; PDF wektorowy; bez testu na urządzeniu)_
- [ ] AES-002 Audyt jakości kreski: pióro/pędzel/ołówek (tekstura), kontrast i wygładzanie, końcówki
- [ ] AES-003 Papier i siatka: delikatniejsze linie, proporcje, odcienie papieru (jak Apple Notes/GoodNotes)
- [ ] AES-004 Pływający pasek narzędzi, ikony, animacje i haptyka na poziomie systemowych aplikacji
- [ ] AES-005 Biblioteka: okładki zeszytów, typografia, odstępy, ciemny motyw
- [ ] AES-006 Weryfikacja wizualna na emulatorze/urządzeniu (zrzuty porównawcze z GoodNotes/Apple Notes)

## Poza zakresem (świadomie)
- [-] Rozpoznawanie pisma → tekst, formuły → LaTeX (EDT-046/047), OCR wyszukiwanie w piśmie odręcznym — brak lokalnego silnika offline w zależnościach; wróci, gdy będzie model on-device.
- [-] Konta, chmura producenta, współpraca w czasie rzeczywistym (sprzeczne z „offline, bez kont”).
