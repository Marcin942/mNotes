# 02 — Edytor i narzędzia (prefiks `EDT`)

Zakres: wszystko, co dzieje się na powierzchni pisania (`NoteView`) i w pasku narzędzi: wejście z rysika/palca, pióra, gumka, lasso, kształty, linijka, tekst, obrazy, narzędzia pomocnicze, presety. Ustawienia ogólne aplikacji (układ paska, motywy, gesty dotykowe, domyślne strony) są w `05-personalizacja.md`; klucze `kropkowane.nazwy` poniżej są wspólne z tamtym katalogiem (tam zasięg G/Z/N i miejsce w UI). Format pliku i eksport: `07`. Wydajność i QA: `09`.

Legenda: P0 = v1, P1 = zaraz po v1, P2 = miło mieć, P3 = kiedyś. Nakład S/M/L/XL jak w `README.md`. `[RYZYKO]` = duża niepewność, `[MODEL]` = ma wpływ na format danych już w v1.

## Tabela szybka

| ID | Nazwa | P | Nakład | Znaczniki |
|---|---|---|---|---|
| EDT-076 | Rysowanie na żywo: nakładka wektorowa + predykcja ruchu | P0 | L | RYZYKO |
| EDT-070 | Odrzucanie dłoni (palm rejection) | P0 | M | RYZYKO |
| EDT-071 | Zachowanie palca i tryb „tylko rysik” | P0 | S | |
| EDT-072 | Przycisk S Pen: mapowanie akcji | P0 | M | RYZYKO (Air command) |
| EDT-073 | Hover: kursor/pierścień rozmiaru i podpowiedzi | P1 | S | |
| EDT-074 | Kalibracja nacisku i test urządzenia | P2 | S | |
| EDT-075 | Mysz, touchpad, DeX | P1 | S | |
| EDT-077 | Rysiki inne niż S Pen (końcówka gumki, USI/Wacom) | P2 | S | |
| EDT-001 | Pióro kulkowe | P0 | M | |
| EDT-002 | Pióro wieczne | P0 | M | |
| EDT-003 | Pędzel (zwężane końce) | P0 | M | |
| EDT-004 | Zakreślacz | P0 | M | |
| EDT-005 | Wygładzanie i stabilizator (per pióro) | P0 | M | strojenie |
| EDT-006 | Krzywa nacisku (gamma / min / max) | P0 | S | |
| EDT-007 | Graficzny edytor krzywej nacisku | P1 | M | |
| EDT-008 | Ołówek (ziarno, pochylenie) | P2 | M | |
| EDT-009 | Próbnik pióra (scribble pad) | P1 | S | |
| EDT-010 | Gumka obiektowa | P0 | M | |
| EDT-011 | Gumka pikselowa (częściowa) | P0 | L | RYZYKO |
| EDT-012 | Opcje gumki (cele, rozmiar, podgląd, powrót) | P1 | S | |
| EDT-013 | Wyczyść stronę | P0 | S | |
| EDT-014 | Gumka lasso | P2 | S | |
| EDT-080 | Presety piór/zakreślaczy/gumek (sloty) | P0 | M | MODEL |
| EDT-081 | Szybka zmiana koloru i grubości (popover) | P0 | M | |
| EDT-082 | Zapamiętywanie narzędzia per zeszyt | P1 | S | |
| EDT-083 | Przełączanie z poprzednim narzędziem | P0 | S | |
| EDT-084 | Pipeta | P1 | S | |
| EDT-085 | Zaznacz wszystko w kolorze / masowa zmiana koloru | P2 | S | |
| EDT-086 | Menu radialne pod piórem | P2 | M | |
| EDT-020 | Lasso swobodne | P0 | M | |
| EDT-021 | Lasso prostokątne | P1 | S | |
| EDT-022 | Uchwyty: przesuwanie, skalowanie, obrót | P0 | M | |
| EDT-023 | Pasek kontekstowy zaznaczenia | P0 | M | |
| EDT-024 | Schowek wewnętrzny: wytnij/kopiuj/wklej/duplikuj | P0 | M | |
| EDT-025 | Zmiana koloru/grubości/krycia zaznaczenia | P0 | S | |
| EDT-026 | Przenoszenie zaznaczenia między stronami + autoprzewijanie | P1 | M | |
| EDT-027 | Kolejność warstw i blokada elementów | P1 | S | MODEL |
| EDT-028 | Grupowanie | P3 | M | MODEL |
| EDT-029 | Wyrównywanie, rozmieszczanie, prowadnice przyciągania | P2 | M | |
| EDT-030 | Auto-kształty „rysuj i przytrzymaj” (zestaw podstawowy) | P0 | L | RYZYKO |
| EDT-031 | Auto-kształty rozszerzone | P1 | M | |
| EDT-032 | Dopasowanie kształtu w trakcie przytrzymania, przyciąganie kątów | P1 | S | |
| EDT-033 | Narzędzie „Kształty” (paleta, wypełnienie, styl linii, groty) | P1 | L | MODEL |
| EDT-034 | Edytowalne kształty (uchwyty wierzchołków) | P2 | M | MODEL |
| EDT-035 | Linijka / krawędź prosta | P1 | M | |
| EDT-036 | Przyrządy kreślarskie: ekierka, kątomierz, cyrkiel | P3 | L | |
| EDT-037 | Siatka przyciągania podczas rysowania | P2 | M | |
| EDT-040 | Pole tekstowe | P0 | L | RYZYKO |
| EDT-041 | Formatowanie podstawowe tekstu | P0 | S | |
| EDT-042 | Listy i checklisty | P1 | M | |
| EDT-043 | Tło i obramowanie pola tekstowego | P2 | S | |
| EDT-044 | Własne czcionki (import TTF/OTF) | P2 | M | |
| EDT-045 | Pismo odręczne → tekst w polach (Stylus Handwriting API) | P1 | S | |
| EDT-046 | ~~Rozpoznawanie wzoru → LaTeX z zaznaczenia~~ — WYCOFANE (daleka przyszłość) | — | — | — |
| EDT-047 | ~~Obiekt wzoru (render KaTeX)~~ — WYCOFANE (daleka przyszłość) | — | — | — |
| EDT-048 | Formatowanie zaawansowane tekstu | P1 | M | |
| EDT-050 | Wstaw obraz z galerii (Photo Picker) | P0 | M | |
| EDT-051 | Wstaw zdjęcie z aparatu | P1 | S | |
| EDT-052 | Skaner dokumentu (korekta perspektywy) | P2 | L | |
| EDT-053 | Edycja obrazu: przycięcie, obrót, odbicie, krycie, blokada | P1 | M | |
| EDT-054 | Wklej obraz/tekst ze schowka systemu | P1 | S | |
| EDT-055 | Naklejki, emoji i „Elementy” (zapisane wycinki) | P2 | M | |
| EDT-056 | Obraz jako podkład strony | P1 | S | |
| EDT-060 | Wskaźnik laserowy | P1 | S | |
| EDT-061 | Laser na hoverze (bez dotykania ekranu) | P1 | S | |
| EDT-062 | Narzędzie Ręka | P1 | S | |
| EDT-063 | Okno powiększenia (zoom window) | P2 | L | |
| EDT-064 | Taśma / ukrywanie odpowiedzi do nauki | P2 | M | MODEL |
| EDT-065 | Wstaw / usuń odstęp | P1 | M | |
| EDT-066 | Długie przytrzymanie rysika wybiera obiekt | P1 | S | |
| EDT-090 | Warstwy (`layer`) i rejestr typów elementów w modelu | P0 | S | MODEL |

---

## 0. Założenia wspólne (czytaj przed implementacją)

### 0.1 Model kreski i jednostki

- Współrzędne w `pt` (1/72"); grubości piór w `pt` (UI domyślnie pokazuje mm: `ui.width_unit` = `mm`, przelicznik 1 mm = 2,8346 pt). Ekran referencyjny Tab S10+: ~266 ppi, czyli 1 pt ≈ 3,7 px przy skali 1:1.
- `StrokeElement` (zalecany nagłówek w blobie `MNP1`): `tool:u8` (1 kulkowe, 2 wieczne, 3 pędzel, 4 zakreślacz, 5 ołówek), `color:u32 ARGB`, `width:f32` (grubość maksymalna przy nacisku 1,0), `flags:u8` (bit0 = mieszanie multiply, bit1 = powstała z auto-kształtu/linijki, bit2 = zablokowana, bit3 = `taper_start`, bit4 = `taper_end` — zwężenia są własnością kreski, żeby kawałki po gumce pikselowej nie dostawały zwężeń w miejscu cięcia), `layer:u8` (patrz EDT-090), `n:u16/u32`, punkty `x,y,p` (`p` = **nacisk efektywny** po krzywej i modulacji prędkości, 0..1).
- Decyzja `[MODEL]`: kanał `p` w pliku to wartość już po krzywej nacisku i modulacji prędkości, więc renderowanie jest czystą funkcją (punkty, nagłówek). Zmiana krzywej nacisku w ustawieniach nie zmienia starych kresek. Na dysku: x,y skwantowane do 1/16 pt (`Short`/delta), `p` jako `u8`; w pamięci `Float`.
- Upraszczanie przy zatwierdzaniu kreski (Ramer–Douglas–Peucker): `eps = max(0,05 pt; 0,04 × width)`, zachowuj lokalne ekstrema nacisku i punkty o zmianie krzywizny; cel: typowa kreska pisma 25–60 punktów. Kreska ma zawsze ≥ 2 punkty (kropka = 2 identyczne punkty, żeby rysował się okrągły znak).

### 0.2 Potok wejściowy (jedna kolejność, żeby wszyscy ją znali)

```
MotionEvent (+historical) → filtr dłoni/dotyku (EDT-070/071) → router narzędzia (przycisk S Pen EDT-072, typ wskaźnika)
  → krzywa nacisku (EDT-006) → modulacja prędkością (tylko wieczne/pędzel) → wygładzanie/stabilizator (EDT-005)
  → mapowanie grubości wg PenSpec (pkt 2.0) → nakładka na żywo (+ogon predykcji tylko do wyświetlenia, EDT-076)
  → ACTION_UP: uproszczenie → StrokeElement → AddStrokeCommand (jedna pozycja undo) → wersja strony++ → brudne kafelki
```

Zasada: **model zawiera wyłącznie punkty z potoku bez ogona predykcji**; predykcja i hover to rzeczy czysto wizualne.

### 0.3 Reguły przełączania narzędzi (jednolite dla całej aplikacji)

| Zdarzenie | Skutek |
|---|---|
| Wybór innego narzędzia przy aktywnym zaznaczeniu | zaznaczenie zatwierdzone i zdjęte (bez wpisu undo); wyjątek: akcje z paska kontekstowego |
| Wybór narzędzia podczas edycji pola tekstowego | edycja kończy się (zatwierdzenie tekstu jako jeden krok undo) |
| Dotknięcie pustego miejsca lasso | zdjęcie zaznaczenia (opcja `lasso.tap_empty_clears` = true) |
| Po wstawieniu obrazu / pola tekstowego | wraca poprzednie pióro, jeśli `behavior.return_to_pen_after_insert` = true [domyślnie] |
| Zmiana zeszytu | narzędzie zostaje (globalne) albo wraca do ostatniego z tego zeszytu (EDT-082) |
| Tryb tylko do odczytu (patrz `04`) | pióra/gumka/lasso/tekst wyszarzone, aktywne: Ręka, Laser, zoom |

### 0.4 Wspólny popover opcji narzędzia

Dotknięcie aktywnego narzędzia (lub długie przytrzymanie / dwukrotne dotknięcie ikony, `toolbar.tool_options_trigger` = `tap_active`) otwiera popover (Compose `Popup`, kotwiczony przy ikonie, maks. 320 dp szerokości):

1. podgląd na żywo (płótno 280×56 dp z fragmentem sinusoidy rysowanym tym samym kodem renderującym, co kreska),
2. grubość: suwak logarytmiczny `w = wmin·(wmax/wmin)^t` + 5 kropek szybkich grubości z tabeli 2.0,
3. kolor: rząd 8 ulubionych/ostatnich + przycisk „więcej” (paleta, HSV/RGB/hex, pipeta — patrz `05`),
4. krycie 10–100% (krok 5), wygładzanie 0–100, przełączniki: nacisk, auto-kształt,
5. „Zapisz jako preset” / „Resetuj do fabrycznych”.

Zmiana w popoverze edytuje **w miejscu aktualnie wybrany preset** (`presets.edit_in_place` = true), więc każdy slot „pamięta” ostatni kolor/grubość.

### 0.5 Tryby wejścia: kto co robi (domyślnie, tablet z S Pen)

| Źródło | Rysuje | Przewija/zoom | Uwagi |
|---|---|---|---|
| S Pen (`TOOL_TYPE_STYLUS`) | tak, aktywnym narzędziem | nie | przycisk S Pen zmienia narzędzie tymczasowo (EDT-072) |
| Palec (1) | nie (`input.finger_mode` = `auto` → przewijanie) | pan | w trybie `draw` rysuje |
| Dwa palce | nie | pinch-zoom + pan | także gdy `finger_mode` = `draw` |
| Mysz (`TOOL_TYPE_MOUSE`) | tak (`input.mouse_mode` = `draw`) | kółko, Ctrl+kółko, środkowy | EDT-075 |
| Końcówka gumki rysika (`TOOL_TYPE_ERASER`) | gumka tymczasowa | nie | EDT-077 |

### 0.6 Do której strony należy element

- Kreska, pole tekstowe, obraz, kształt należą do strony, na której nastąpił `ACTION_DOWN` (tworzenie) lub `ACTION_UP` (upuszczenie zaznaczenia, EDT-026). `DOWN` w odstępie między stronami → strona najbliższa środka kreski.
- Współrzędne nie są obcinane do krawędzi strony, ale **render i eksport przycinają do prostokąta strony** (`clipRect`). Dzięki temu przesunięcie elementu „na zewnątrz” i z powrotem niczego nie traci.
- Zaznaczenie (lasso) i gesty edycji dotyczą jednej strony; przenoszenie między stronami opisuje EDT-026.
- Pierścień gumki i linijka działają na stronie pod nimi; granice stron nie przerywają gestu.

---

## 1. Wejście z urządzenia i rysowanie na żywo

### EDT-076 — Rysowanie na żywo: nakładka wektorowa + predykcja ruchu · P0 · L · [RYZYKO]
**Zależy od:** wspólny viewport (macierz strona→ekran) i `TileCache` (patrz `04`, `09`).
**Co widzi użytkownik:** atrament pojawia się pod końcówką rysika bez wyczuwalnej zwłoki, linia jest gładka na każdym zoomie, a po oderwaniu rysika nie miga ani nie zmienia kształtu (wyjątek: celowe zwężenie końcówki pędzla/pióra).
**Opcje/personalizacja:** `render.motion_prediction` bool [true]; `render.live_antialias` bool [true]; `render.low_latency_mode` enum {`standard`, `front_buffer`} [`standard`; `front_buffer` = P2 po pomiarach, wymaga API 29+, `androidx.graphics:graphics-core`].
**Przypadki brzegowe:**
- Wszystkie próbki `getHistorical*` przetwarzaj w kolejności przed bieżącym zdarzeniem (batching przy 120 Hz+).
- Ogon predykcji tylko wizualny, zastępowany w następnej klatce; po `ACTION_UP` usuwany natychmiast.
- `ACTION_CANCEL` (palec uznany za dłoń, gest systemowy) → kreska odrzucona bez commitu i bez wpisu undo.
- Nowa kreska zaczęta zanim poprzednia „wsiąknie” w kafelki: nakładka trzyma do 8 oczekujących kresek.
- Zmiana macierzy widoku (zoom/pan) w trakcie kreski jest zablokowana (gesty palców ignorowane, patrz EDT-070).
- Kreska >5 000 punktów: geometrię live dziel na kawałki po 1 000–2 000 punktów, żeby nie przebudowywać całej ścieżki co zdarzenie.
- Brak nacisku (palec/mysz): `p` = 0,5 dla kulkowego/zakreślacza; dla wiecznego/pędzla `p` wyznaczone z prędkości (`p = clamp(1 − v/vmax, 0,3…1)`, `vmax` = 2000 dp/s).
**Wskazówka implementacyjna:**
- Na `ACTION_DOWN` wywołaj `view.requestUnbufferedDispatch(event)`; używaj `MotionEventPredictor.newInstance(view)`: `record(event)` dla każdego zdarzenia, `predict()` po przetworzeniu partii.
- Nakładka rysowana w `onDraw` `NoteView` tą samą macierzą co kafelki; `invalidate(l,t,r,b)` tylko dla prostokąta nowych segmentów powiększonego o `width/2 + 2 px`; ścieżka przyrostowa (O(1) na zdarzenie, `Path.rewind()` zamiast nowych obiektów; tablice prymitywne rosnące ×2, zero alokacji w pętli zdarzeń).
- Kreski o stałej grubości (kulkowe, zakreślacz): jeden `Path` rysowany `Paint.STROKE` z okrągłymi końcami i łączeniami. Kreski o zmiennej grubości (wieczne, pędzel): wielokąt zarysu (offset lewy+prawy+okrągłe końce) liczony okienkowo (ostatnie ~8 punktów), a przy commicie raz w całości i cache'owany jako `Path` w elemencie.
- Commit bez migotania: (1) zbuduj niezmienny `StrokeElement`; (2) dodaj komendę undo; (3) zleć render zakrytych kafelków z wysokim priorytetem; (4) **trzymaj nakładkę, dopóki `TileCache` nie opublikuje wersji kafelków ≥ wersji commitu dla wszystkich widocznych zakrytych kafelków** (twardy limit 150 ms); (5) zdejmij nakładkę w tej samej klatce, w której kafelki są podmieniane (jedno `invalidate`).
- Render kafelków na `Dispatchers.Default.limitedParallelism(2)`, wątek UI nigdy nie czeka na render.
**Kryteria akceptacji:**
1. 10 s ciągłego bazgrania (ręcznie lub pętla `adb shell input swipe`) → `dumpsys gfxinfo`: ≥ 99% klatek ≤ 8,3 ms (120 Hz), brak „Slow UI thread” z obsługi dotyku.
2. Wideo 240 fps klatka po klatce: przy `ACTION_UP` położenie i szerokość kreski zmieniają się o ≤ 1 px (brak „skoku” przy commicie).
3. Wyłączenie `render.motion_prediction` usuwa ogon, reszta bez zmian; kreska po commicie jest identyczna jak przed (porównanie zrzutów).

### EDT-070 — Odrzucanie dłoni (palm rejection) · P0 · M · [RYZYKO]
**Zależy od:** EDT-076, EDT-071.
**Co widzi użytkownik:** można oprzeć dłoń na ekranie podczas pisania S Pen — nic się nie rysuje, nie przewija ani nie powiększa. Gdy rysika nie ma w pobliżu, palce działają normalnie.
**Opcje/personalizacja:** `input.palm.mode` enum {`off`, `stylus_proximity`, `contact_size`, `both`} [`both`]; `input.palm.stylus_timeout_ms` 0–2000 [450]; `input.palm.max_contact_mm` 8–40 [20]; `input.palm.edge_dp` 0–48 [12]; `input.palm.cancel_active_on_pen` bool [true].
**Algorytm (stan na wskaźnik):**
```
hover/dotknięcie rysika:           lastStylusMs = now; stylusInRange = true
hover EXIT lub UP rysika:          lastStylusMs = now (timeout liczony od opuszczenia zasięgu)
palec DOWN:
  if mode∈{stylus_proximity,both} && now − lastStylusMs < timeout        → odrzuć pointerId
  if mode∈{contact_size,both} && touchMajor_mm > max_contact_mm          → odrzuć
  if x < edge_dp || x > width − edge_dp (od krawędzi ekranu fizycznego)  → odrzuć
  FLAG_CANCELED (API 33+) na dowolnym zdarzeniu                          → anuluj gest/kreskę tego wskaźnika
odrzucony wskaźnik: połykaj wszystkie jego zdarzenia aż do UP
rysik w zasięgu/dotyka (cancel_active_on_pen): anuluj trwające gesty palców (to dłoń)
```
`touchMajor_mm = touchMajor_px / xdpi × 25,4`.
**Przypadki brzegowe:**
- Dłoń położona PRZED wykryciem hovera: `cancel_active_on_pen` anuluje już trwający pan/zoom palców.
- Urządzenie bez hovera (starsze rysiki) → działa tylko `contact_size` + krawędź; ustaw `stylus_timeout_ms` od ostatniego dotknięcia rysika.
- `touchMajor` = 0 lub stałe → traktuj jako nieznane (bez odrzucania po rozmiarze).
- Gesty dwupalcowe (undo) również podlegają odrzucaniu podczas hovera rysika.
- DeX/mysz: filtr dłoni nie dotyczy `TOOL_TYPE_MOUSE`.
**Wskazówka implementacyjna:** odbieraj hover przez `onHoverEvent`/`onGenericMotionEvent` (zdarzenia `ACTION_HOVER_*` nie przychodzą do `onTouchEvent`). Filtr to osobna, testowalna jednostkowo klasa (`PalmFilter`: wejście — sekwencja zdarzeń z czasem, wyjście — decyzje), żeby testować na JVM bez urządzenia. Telemetria lokalna (tylko licznik w logu debug): ile razy odrzucono / ile razy anulowano kreskę.
**Kryteria akceptacji:** (a) 60 s pisania z dłonią opartą o ekran → 0 przypadkowych kresek, 0 przypadkowych zmian zoomu/scrolla; (b) przy rysiku w kieszeni (poza zasięgiem hover >450 ms) przewijanie jednym palcem działa od razu; (c) test jednostkowy `PalmFilter` na nagranych sekwencjach (hover → palec w trakcie, palec → hover, FLAG_CANCELED) zwraca oczekiwane decyzje.

### EDT-071 — Zachowanie palca i tryb „tylko rysik” · P0 · S
**Zależy od:** EDT-070.
**Co widzi użytkownik:** przełącznik w pasku/ustawieniach: „Palec: przewija / rysuje / auto”.
**Opcje/personalizacja:** `input.finger_mode` enum {`auto`, `scroll`, `draw`} [`auto`]; `input.finger_draw_toggle_in_toolbar` bool [true]; `input.finger_scroll_axis_lock` enum {`none`, `vertical`, `auto`} [`none`] (po zablokowaniu osi: przeciągnięcie palcem o kącie <20° do osi przewija tylko tą osią).
`auto`: jeśli kiedykolwiek wystąpił `TOOL_TYPE_STYLUS` na tym urządzeniu (flaga `device.has_stylus` w prefs) → palec przewija; w przeciwnym razie (telefon bez rysika) → palec rysuje, a przewijanie/zoom robią dwa palce lub narzędzie Ręka.
**Przypadki brzegowe:** w trybie `draw` palec podlega filtrowi dłoni (rozmiar styku); dwa palce zawsze = zoom/pan; trzeci palec (gesty) patrz `06`; rysik ma pierwszeństwo, gdy rysuje palcem (palec ignorowany).
**Wskazówka implementacyjna:** decyzja „kto rysuje” jest w jednym miejscu (`InputRouter.shouldDraw(pointer)`); tryb pokazuj ikoną w pasku (rączka / palec-z-kreską).
**Kryteria akceptacji:** Tab S10+ w trybie `auto`: palec przewija, rysik rysuje; po przełączeniu na `draw` palec rysuje; telefon bez rysika po świeżej instalacji: palec rysuje, dwa palce przewijają.

### EDT-072 — Przycisk S Pen: mapowanie akcji · P0 · M · [RYZYKO]
**Zależy od:** EDT-083, EDT-076.
**Co widzi użytkownik:** przytrzymanie przycisku i rysowanie = tymczasowa gumka (domyślnie); po puszczeniu wraca poprzednie narzędzie. W ustawieniach można przypisać każdemu gestowi przycisku inną akcję.
**Opcje/personalizacja:**
- `input.spen.hold_action` [`eraser_temp`], `input.spen.click_action` [`none`, patrz ryzyko], `input.spen.double_click_action` [`none`].
- Wartości: `none`, `eraser_temp`, `lasso_temp`, `highlighter_temp`, `hand_temp`, `laser_temp`, `previous_tool`, `toggle_eraser`, `undo`, `redo`, `next_preset`, `prev_preset`, `toggle_toolbar`, `open_radial_menu`.
- `input.spen.double_click_window_ms` 200–600 [300] (gdy `double_click_action` ≠ `none`, pojedynczy klik wykonuje się po tym oknie).
**Stan:**
```
DOWN rysika z buttonState & BUTTON_STYLUS_PRIMARY  → narzędzie = hold_action (tymczasowe); wskaźnik w pasku: przerywany obrys
przycisk wciśnięty w trakcie kreski                  → ignoruj do końca kreski (bez zmiany narzędzia w locie)
UP                                                    → przywróć narzędzie sprzed DOWN
ACTION_BUTTON_PRESS + RELEASE w hoverze <350 ms, bez dotyku → „klik” → click_action
```
**Przypadki brzegowe:** na urządzeniach Samsung pojedyncze kliknięcie przycisku w hoverze bywa przechwytywane przez systemowe „Air command” — zdarzenia mogą nie dotrzeć do aplikacji [RYZYKO, zweryfikować na S10+]; dlatego domyślnie tylko `hold_action` jest aktywne, a w onboardingu jest ekran „Test przycisku S Pen” (pokazuje, które zdarzenia dochodzą) oraz podpowiedź, jak wyłączyć Air command (Ustawienia → Funkcje zaawansowane → S Pen). `BUTTON_STYLUS_SECONDARY` (inne rysiki) mapowane tak samo jak primary, jeśli jest.
**Wskazówka implementacyjna:** odczyt `event.buttonState` na `ACTION_DOWN` i `event.actionButton` na `ACTION_BUTTON_PRESS`; maszyna stanów w osobnej klasie (`PenButtonStateMachine`) testowana jednostkowo; „tymczasowe” = stos narzędzi (`ToolStack`, wspólny z EDT-083).
**Kryteria akceptacji:** przytrzymanie przycisku + przeciągnięcie po napisie kasuje (domyślne ustawienia), puszczenie przywraca pióro z tymi samymi kolorem i grubością; zmiana `hold_action` na `lasso_temp` działa od następnej kreski bez restartu.

### EDT-073 — Hover: kursor/pierścień rozmiaru i podpowiedzi · P1 · S
**Zależy od:** EDT-076.
**Co widzi użytkownik:** zbliżony rysik pokazuje pierścień o rzeczywistej szerokości kreski (min. 8 dp) w kolorze pióra (krycie 70%); dla gumki obrys rozmiaru gumki; dla tekstu/obrazu celownik; nad przyciskami paska — podpowiedź po 500 ms.
**Opcje/personalizacja:** `input.spen.hover_cursor` enum {`off`, `dot`, `ring`} [`ring`]; `input.spen.hover_tooltips` bool [true]; `input.spen.hover_distance_max` 0,3–1,0 [0,9] (`AXIS_DISTANCE`).
**Przypadki brzegowe:** brak hovera na urządzeniu → nic nie pokazuj; pierścień nie rysuje się podczas dotyku (kreska ją zastępuje); przy zoomie rozmiar = grubość × zoom.
**Wskazówka implementacyjna:** rysuj w nakładce `NoteView` osobno od kafelków, dirty rect = stary + nowy pierścień; `onHoverEvent` zwraca `true` tylko dla rysika.
**Kryteria akceptacji:** pierścień podąża za rysikiem z opóźnieniem ≤ 1 klatki; po wyłączeniu opcji nic nie jest rysowane i brak wywołań `invalidate` z hovera.

### EDT-074 — Kalibracja nacisku i test urządzenia · P2 · S
**Zależy od:** EDT-006, EDT-009.
**Co widzi użytkownik:** w Ustawieniach → Rysik kreator: „Dotknij lekko”, „Naciśnij najmocniej”, pasek żywego nacisku 0–100%, histogram.
**Opcje/personalizacja:** wynik zapisuje `input.pressure.min` i `input.pressure.max`; przycisk „Przywróć domyślne”.
**Przypadki brzegowe:** urządzenie zwraca stały nacisk (np. 1,0 dla palca/pióra bez nacisku) → komunikat „To urządzenie nie raportuje nacisku”, pióra przełączają się na modulację prędkością.
**Wskazówka implementacyjna:** zbierz p dla lekkiego dotyku (percentyl 10) i mocnego (percentyl 95); `min = p10`, `max = p95`.
**Kryteria akceptacji:** po kalibracji pełny zakres szerokości wiecznego pióra osiągalny normalnym naciskiem dłoni użytkownika.

### EDT-075 — Mysz, touchpad, DeX · P1 · S
**Zależy od:** EDT-076.
**Co widzi użytkownik:** w DeX/na Chromebooku mysz rysuje lewym przyciskiem, kółko przewija, Ctrl+kółko powiększa wokół kursora, środkowy przycisk przesuwa, prawy otwiera menu kontekstowe; kursor zmienia kształt zależnie od narzędzia.
**Opcje/personalizacja:** `input.mouse_mode` enum {`draw`, `pan`} [`draw`]; `input.mouse_right_action` enum {`context_menu`, `eraser_temp`} [`context_menu`]; `input.wheel_zoom_step_pct` 5–25 [10]; `input.wheel_scroll_dp` 40–300 [120].
**Przypadki brzegowe:** gest pinch na touchpadzie (ChromeOS/DeX) bywa dostarczany jako `ACTION_SCROLL` z `META_CTRL_ON` albo jako zwykłe zdarzenia wielodotykowe — oba traktuj jako zoom (sprawdzić na DeX); mysz nie ma nacisku (`p` jak w EDT-076).
**Wskazówka implementacyjna:** `onGenericMotionEvent` dla `ACTION_SCROLL`; `View.onResolvePointerIcon` zwraca `PointerIcon.TYPE_CROSSHAIR`/`TYPE_HANDWRITING` (API 34)/`TYPE_ARROW`.
**Kryteria akceptacji:** w emulatorze desktopowym (DeX-like) wszystkie wymienione gesty działają; Ctrl+kółko zachowuje punkt pod kursorem w miejscu.

### EDT-077 — Rysiki inne niż S Pen (końcówka gumki, USI/Wacom) · P2 · S
**Zależy od:** EDT-070, EDT-072.
**Co widzi użytkownik:** rysik z „ogonem” gumki automatycznie kasuje po odwróceniu; rysiki USI/Wacom działają jak S Pen (nacisk, hover, przyciski).
**Opcje/personalizacja:** `input.spen.eraser_tip_auto` bool [true]; `input.tilt_enabled` bool [true] (pochylenie używane tylko przez ołówek EDT-008).
**Przypadki brzegowe:** inny zakres/rozdzielczość nacisku → kalibracja EDT-074; `TOOL_TYPE_ERASER` bez hovera.
**Wskazówka implementacyjna:** router traktuje `TOOL_TYPE_ERASER` jak `eraser_temp`, `AXIS_TILT`/`AXIS_ORIENTATION` zapisuj tylko, gdy ołówek aktywny.
**Kryteria akceptacji:** emulowane zdarzenie `TOOL_TYPE_ERASER` uruchamia gumkę z ustawieniami gumki, bez zmiany wybranego narzędzia w pasku.

---

## 2. Pióra i zakreślacz

### 2.0 Tabela parametrów piór (`PenSpec`) — wartości fabryczne
Szerokość w mm (1 mm = 2,8346 pt) podana jako wartość maksymalna przy nacisku 1,0. Wzór grubości w punkcie: `w = width × f(p)`, gdzie `f(p) = lerp(minFactor, 1, p^γw)` (jeśli nacisk włączony; inaczej `f = 1`), po czym `w *= (1 − k·v̂)` dla modulacji prędkością (`v̂` = prędkość znormalizowana 0..1, `vmax` = 2000 dp/s) i mnożnik zwężeń końców.

| Parametr | Kulkowe (1) | Wieczne (2) | Pędzel (3) | Zakreślacz (4) | Ołówek (5, P2) |
|---|---|---|---|---|---|
| Domyślna grubość | 0,5 mm = 1,42 pt | 1,0 mm = 2,83 pt | 3,0 mm = 8,50 pt | 5,0 mm = 14,17 pt | 0,7 mm = 1,98 pt |
| Zakres suwaka | 0,1–5 mm | 0,25–10 mm | 0,5–30 mm | 1,4–20 mm | 0,3–8 mm |
| 5 szybkich grubości (mm) | 0,25 / 0,35 / 0,5 / 0,8 / 1,2 | 0,5 / 0,75 / 1,0 / 1,5 / 2,5 | 1 / 2 / 3 / 5 / 8 | 3 / 5 / 8 / 12 / 16 | 0,4 / 0,7 / 1 / 2 / 3 |
| Nacisk | wył. (opcja; po włączeniu `minFactor` 0,55) | wł. | wł. | wył. | wł. |
| `minFactor` (p=0) | 0,55 | 0,28 | 0,08 | 1,0 | 0,35 |
| `γw` | 1,0 | 1,0 | 1,3 | — | 1,0 |
| Modulacja prędkością `k` | 0 | 0,20 | 0,10 | 0 | 0 |
| Zwężenie początku | brak | brak | długość 1,0×w, start 0,25 | brak | brak |
| Zwężenie końca | brak | 1,5×w do 0,45, tylko jeśli nacisk nie spadł naturalnie | 3,0×w do 0,05 | brak | 2×w do 0,5 |
| Limit zwężeń | — | — | ≤ 25% / ≤ 35% długości kreski | — | ≤ 30% |
| Końce (cap) | okrągłe | okrągłe | okrągłe | okrągłe (opcja: kwadratowe) | okrągłe |
| Mieszanie | normalne | normalne | normalne | multiply (na ciemnym papierze, luminancja <0,4: normalne z alfa 45%) | normalne + tekstura |
| Krycie domyślne | 100% | 100% | 100% | 50% | 85% |
| Wygładzanie `smoothing` | 20 | 30 | 40 | 35 | 15 |
| Auto-kształt | tak | tak | nie | tak (linia, prostokąt) | nie |

Kolory fabryczne presetów i palety: `05-personalizacja.md`. Zakreślacz bazuje na nasyconym kolorze (np. `#FFEB3B`) wyświetlanym przy krycie 50%.

Specyfikacja piór jest **danymi** (`PenSpec` jako obiekt w kodzie), nie osobnymi klasami: dodanie pióra = nowy wiersz tabeli. Zaawansowane nadpisania (`adv` w presecie: `minFactor`, `gamma`, `taperStart`, `taperEnd`, `speedK`, `cap`, `blend`) to P2 (patrz `05`).

### EDT-001 — Pióro kulkowe · P0 · M
**Zależy od:** EDT-076, EDT-005, EDT-080.
**Co widzi użytkownik:** równa, czysta linia o stałej szerokości, zaokrąglone końce; klasyczne pióro do codziennych notatek.
**Opcje/personalizacja:** grubość 0,1–5 mm [0,5]; kolor; krycie 10–100% [100]; wygładzanie 0–100 [20]; nacisk wł./wył. [wył.]; auto-kształt [wł.]. (Preset: `tool=ballpoint`.)
**Przypadki brzegowe:** bardzo krótka kreska (kropka) rysuje się jako okrąg o średnicy = grubość; przy zoomie 800% linie nadal gładkie (wektor, nie bitmapa); krycie <100% i samonakładanie w obrębie jednej kreski nie ciemnieje (jeden wypełniony kształt).
**Wskazówka implementacyjna:** `Paint.STROKE`, `StrokeCap.ROUND`, `StrokeJoin.ROUND` po wygładzonej ścieżce (kwadratowe Béziery przez środki odcinków); w kafelku ta sama funkcja co w nakładce, żeby wygląd się nie zmieniał po commicie.
**Kryteria akceptacji:** kreska poziomych 100 pt przy zoomie 100% ma szerokość 1,42 pt ± 0,05 pt (pomiar w eksportowanym PDF); kropka nie zniekształca się; zrzut kafelka i nakładki identyczny z dokładnością ≤ 1 px.

### EDT-002 — Pióro wieczne · P0 · M
**Zależy od:** EDT-076, EDT-006, EDT-005.
**Co widzi użytkownik:** linia zmienia grubość z naciskiem i lekko z prędkością (szybciej = cieniej), płynne wejścia i wyjścia; wrażenie prawdziwego pióra.
**Opcje/personalizacja:** grubość maks. 0,25–10 mm [1,0]; kolor; krycie [100]; wygładzanie [30]; nacisk [wł., `gamma` globalna]; modulacja prędkością 0–50% [20] (`pen.fountain.speed_thinning`); auto-kształt [wł.].
**Przypadki brzegowe:** nagły skok nacisku (S Pen raportuje skokowo) → wygładź `p` filtrem 1€ (osobne parametry: `mincutoff` 5 Hz, `beta` 0,02) przed mapowaniem szerokości, żeby nie było „schodków” grubości; bez nacisku (palec) patrz EDT-076.
**Wskazówka implementacyjna:** wielokąt zarysu z przesunięciami prostopadłymi do stycznej, końce okrągłe; wygładzanie normalnych (średnia z 3 sąsiadów), żeby wewnętrzne narożniki nie „skręcały się”; `Path` liczony raz i cache'owany w elemencie.
**Kryteria akceptacji:** przy stałym naciskowi p=0,5 szerokość = 0,64 × width ± 3%; przy p=1,0 = width; wolna linia vs szybka (1500 dp/s) różni się szerokością o ~20%; brak artefaktów (pętli/„motylków”) przy ostrych zawróceniach (test: spirala i litera „e” 6 pt).

### EDT-003 — Pędzel (zwężane końce) · P0 · M
**Zależy od:** EDT-002.
**Co widzi użytkownik:** ekspresyjna linia: mocna zmiana szerokości z naciskiem, miękkie zwężone początki i końce (kaligrafia, lettering, szkice).
**Opcje/personalizacja:** grubość maks. 0,5–30 mm [3,0]; krycie [100]; wygładzanie [40]; zwężenie początku/końca wł./wył. [wł./wł.] (`pen.brush.taper_start`, `pen.brush.taper_end`); kolor.
**Przypadki brzegowe:** kreska krótsza niż suma zwężeń → zwężenia skalowane proporcjonalnie (limit 25%/35% długości); końcowe zwężenie widoczne dopiero po commicie (na żywo kreska kończy się „tępo”) — dopuszczalne, skok ≤ 1 klatka; na żywo można pokazać zwężenie wynikające z naturalnego spadku nacisku.
**Wskazówka implementacyjna:** ta sama ścieżka co EDT-002 z mnożnikiem `taper(s)`, gdzie `s` = odległość wzdłuż kreski; `taper_end` liczony od końca po zatwierdzeniu.
**Kryteria akceptacji:** kreska 50 pt/3 mm: początek zaczyna się od ~25% szerokości i osiąga 100% po ~8,5 pt, koniec zwęża się do ≤ 5% na ostatnich ~25 pt; po commicie brak migotania (EDT-076).

### EDT-004 — Zakreślacz · P0 · M
**Zależy od:** EDT-076, EDT-001.
**Co widzi użytkownik:** półprzezroczysty, szeroki pisak, pod którym tekst pozostaje czytelny (mnożenie barw); jedna kreska nie ciemnieje sama o siebie.
**Opcje/personalizacja:** grubość 1,4–20 mm [5,0]; krycie 20–100% [50]; kolor (paleta zakreślaczy w `05`); końce okrągłe/kwadratowe (`pen.highlighter.cap`) [okrągłe]; auto-prosta linia po przytrzymaniu [wł.]; `pen.highlighter.keep_under_ink` bool [false] — gdy true, zakreślacz zawsze pod atramentem (warstwa, EDT-090).
**Przypadki brzegowe:** papier ciemny (luminancja <0,4): multiply zanika → przełącz na mieszanie normalne z alfa 45%; zakreślacz nad obrazem/PDF: multiply względem treści pod spodem; dwie osobne kreski nachodzące na siebie ciemnieją (jak prawdziwy zakreślacz), jedna kreska — nie.
**Wskazówka implementacyjna:** kreska to pojedyncza wypełniona ścieżka rysowana z `alpha` i `BlendMode.MULTIPLY` (API 29+) / `PorterDuffXfermode(MULTIPLY)` (API 26–28); w nakładce na żywo też multiply względem kafelków (rysuj nakładkę po kafelkach tym samym trybem).
**Kryteria akceptacji:** czarny tekst pod żółtym zakreślaczem pozostaje czarny (±3 poziomy), tło strony białej daje ~`#FFF494` dla `#FFEB3B` przy 50%; kreska z pętlą w obrębie jednej kreski ma jednolitą jasność.

### EDT-005 — Wygładzanie i stabilizator (per pióro) · P0 · M
**Zależy od:** EDT-076.
**Co widzi użytkownik:** suwak „Wygładzanie” 0–100 w popoverze pióra; wyższe wartości dają bardziej płynne, „zasznurowane” linie kosztem lekkiego opóźnienia.
**Opcje/personalizacja:** `smoothing` w presecie 0–100 (domyślne wg tabeli 2.0); `smoothing.mode` globalnie enum {`filter`, `filter_rope`} [`filter_rope`]; przełącznik „Wygładzaj po zakończeniu kreski” (`smoothing.post_pass` bool [false], P2).
**Algorytm (punkt startowy do strojenia na urządzeniu):**
- Etap A — filtr 1€ (One Euro) na (x,y) z czasami z `eventTime`: dla `s=0` wyłączony; dla `s>0`: `mincutoff = 30 Hz × 0,05^(s/100)` (s=20 → 16,5 Hz, s=50 → 6,7 Hz, s=100 → 1,5 Hz), `beta = 0,02`, `dcutoff = 1,0 Hz`. Szybki ruch automatycznie zmniejsza wygładzanie, więc opóźnienie rośnie tylko przy wolnym ruchu.
- Etap B — „lina” (tylko `s>60`): efektywny koniec pióra podąża za rysikiem w odległości `r = (s − 60)/40 × 14 dp` (w przestrzeni ekranu, żeby odczucie nie zależało od zoomu).
- Pierwsze 2 punkty (~8 ms) przechodzą bez filtra (brak „ogona na starcie”). Po `ACTION_UP` dołóż surowy ostatni punkt i dociągnij linę w ≤ 3 punktach (kreska kończy się tam, gdzie rysik).
- Środkowa linia renderowana jako kwadratowe Béziery przez środki odcinków (C1); po commicie opcjonalny przebieg Catmull-Rom → sześcienne Béziery dla wieczne/pędzel.
**Przypadki brzegowe:** niska częstotliwość zdarzeń (60 Hz) — parametry liczone z `dt`, nie z liczby próbek; kreski kilkupunktowe nie są wygładzane (min. 4 punkty); wygładzanie nie może przesunąć punktu o więcej niż `r + 2 dp` względem surowego.
**Wskazówka implementacyjna:** filtr w osobnej klasie `PointFilter` (czysta funkcja `process(x,y,t)`), testy jednostkowe na sztucznych sekwencjach (sinusoida + szum ±1 px).
**Kryteria akceptacji:** `s=0` → punkty identyczne z wejściem (±0,01 pt); `s=50`: szum ±1 px na wolnej prostej linii spada ≥ 50% (RMS), szybka kreska 1000 dp/s odbiega od surowej ≤ 1,5 dp; opóźnienie końca kreski ≤ 8 ms dla `s ≤ 30`.

### EDT-006 — Krzywa nacisku (gamma / min / max) · P0 · S
**Zależy od:** EDT-076.
**Co widzi użytkownik:** w Ustawieniach → Rysik i w popoverze pióra: suwak „Czułość nacisku” z podglądem kreski; trzy presety: Miękka / Normalna / Twarda.
**Opcje/personalizacja:** `input.pressure.enabled` bool [true]; `input.pressure.gamma` 0,4–2,5 [1,0] (`p' = p^γ`; Miękka = 0,7, Twarda = 1,5); `input.pressure.min` 0–0,3 [0,03] (martwa strefa: poniżej → 0); `input.pressure.max` 0,5–1,0 [1,0] (powyżej → 1,0); nadpisanie per preset: `pressure.gamma` opcjonalnie.
Formuła: `p_norm = clamp((p − min)/(max − min), 0, 1)`; `p' = p_norm^γ`.
**Przypadki brzegowe:** sprzęt raportujący nacisk >1,0 → obetnij; urządzenia z `p` tylko 0/1 → patrz EDT-074.
**Wskazówka implementacyjna:** krzywa liczona w `PressureMapper` przed filtrem; wartość przechowywana w kresce jest już `p'` (patrz 0.1).
**Kryteria akceptacji:** dla `γ=0,7` i p=0,5 wynik 0,616 ± 0,01; zmiana krzywej nie zmienia istniejących kresek; podgląd w popoverze odzwierciedla ustawienie natychmiast.

### EDT-007 — Graficzny edytor krzywej nacisku · P1 · M
**Zależy od:** EDT-006, EDT-009.
**Co widzi użytkownik:** wykres 0–1 × 0–1 z dwoma przeciąganymi punktami kontrolnymi (jak CSS `cubic-bezier`), żywy podgląd kreski pod wykresem i wskaźnik bieżącego nacisku rysika.
**Opcje/personalizacja:** `input.pressure.curve` string `"bezier(x1,y1,x2,y2)"` [`"bezier(0,0,1,1)"`]; presety: Liniowa, Miękka `(0.2,0.6,0.5,1)`, Twarda `(0.5,0,0.8,0.5)`, S-krzywa; przycisk „Wyzeruj”; per preset opcjonalne nadpisanie.
**Przypadki brzegowe:** krzywa musi być monotoniczna (ogranicz `x1,x2` do [0,1] i dopuszczaj `y` w [0,1]); wartości w tablicy LUT 256 elementów, żeby koszt był stały.
**Wskazówka implementacyjna:** generuj LUT[256] przy zmianie ustawienia; `PressureMapper` używa LUT (interpolacja liniowa).
**Kryteria akceptacji:** krzywa liniowa daje wynik identyczny z `γ=1`; zapis/odczyt string-a bez utraty precyzji do 3 miejsc.

### EDT-008 — Ołówek (ziarno, pochylenie) · P2 · M
**Zależy od:** EDT-002, EDT-077.
**Co widzi użytkownik:** miękka grafitowa linia z ziarnem papieru; przy mocnym pochyleniu rysika szerokie cieniowanie.
**Opcje/personalizacja:** grubość 0,3–8 mm [0,7]; krycie [85]; `pen.pencil.grain` 0–100 [50]; `pen.pencil.tilt_shading` bool [true]; wygładzanie [15].
**Przypadki brzegowe:** wyższy koszt renderu (tekstura): w kafelkach `BitmapShader` z teksturą 128×128 px, w nakładce uproszczony wariant; eksport PDF jako ścieżka z maską lub rasteryzacja kreski — udokumentować w `07`; bez tilt (palec/mysz) — brak cieniowania.
**Wskazówka implementacyjna:** alfa modulowana szumem (wzór deterministyczny od pozycji strony, żeby wygląd nie zmieniał się po przerenderowaniu kafelka); tilt z `AXIS_TILT` mapowany na szerokość ×(1..3).
**Kryteria akceptacji:** ziarno identyczne po przerenderowaniu kafelka i przy innym zoomie (wzór w pt, nie w px); czas rysowania kafelka z ołówkiem ≤ 2× kulkowego.

### EDT-009 — Próbnik pióra (scribble pad) · P1 · S
**Zależy od:** EDT-001..006.
**Co widzi użytkownik:** w ustawieniach pióra pole „Wypróbuj” (wysokość 160 dp), w którym można bazgrać wybranym piórem z bieżącymi ustawieniami (nacisk, wygładzanie, krzywa), z paskiem nacisku i przyciskiem „Wyczyść”. Nic nie trafia do zeszytu.
**Opcje/personalizacja:** `scribble.show_pressure_bar` bool [true]; `scribble.show_speed` bool [false].
**Przypadki brzegowe:** pole musi korzystać z tego samego kodu renderującego co `NoteView` (nie osobna implementacja), żeby podgląd był wiarygodny.
**Wskazówka implementacyjna:** instancja `NoteView` w trybie „jednorazowa strona” bez zapisu; ewentualnie lekki `StrokeCanvas` współdzielący `PenRenderer`.
**Kryteria akceptacji:** zmiana wygładzania/krzywej w ustawieniach zmienia wygląd kreski w próbniku natychmiast, bez opuszczania ekranu.

---

## 3. Gumka

### 3.0 Reguły wspólne gumki

| Tryb (`eraser.mode`) | Co robi | Zapis zmiany (jedna komenda `EraseCommand` na gest) |
|---|---|---|
| `stroke` | usuwa całą kreskę dotkniętą pierścieniem | `removed = [kreski]` |
| `pixel` | wycina tylko fragment pod pierścieniem; reszta zostaje jako 1–N kresek | `removed = [oryginał]`, `added = [kawałki]` na tej samej pozycji kolejności rysowania |

- `eraser.size_pt` = **średnica** pierścienia w pt strony: 4–80 [18] (1,4–28 mm; domyślnie 6,4 mm). Szybkie rozmiary: 8 / 18 / 40 pt. Rozmiar skaluje się z zoomem (jak gumka na kartce); `eraser.size_screen_constant` = true utrzymuje stały rozmiar na ekranie.
- Pierścień: obrys 1,5 dp `#757575` z białą obwódką 1 dp, wypełnienie `#757575` 8%; rysowany w nakładce (hover i dotyk).
- Jeden gest (DOWN → UP) = jeden krok cofania. Gumka działa na stronie pod pierścieniem (przeciągnięcie przez granicę stron obejmuje obie strony w jednym kroku).
- Gumka nigdy nie rusza: podkładu strony (szablon, strona PDF), elementów zablokowanych (EDT-027), taśm (EDT-064).
- Przycisk S Pen i „ogon” rysika uruchamiają **aktualnie wybrany preset gumki** (EDT-072, EDT-077).
- Domyślny tryb: `pixel` (GoodNotes też domyślnie kasuje częściowo). Plan B, gdyby EDT-011 nie wszedł do v1: domyślnie `stroke`.

### EDT-010 — Gumka obiektowa · P0 · M
**Zależy od:** EDT-076, EDT-080, EDT-090.
**Co widzi użytkownik:** pierścień o wybranym rozmiarze; przeciągnięcie przez kreskę usuwa ją w całości, od razu (bez czekania na puszczenie). Samo stuknięcie kreski też ją usuwa.
**Opcje/personalizacja:** `eraser.mode` = `stroke`; `eraser.size_pt` 4–80 [18]; `eraser.filter` enum {`all`, `highlighter_only`, `no_highlighter`} [`all`]; `eraser.erase_objects` bool [false] — gdy true, gumka obiektowa usuwa także pola tekstowe, obrazy, kształty, wzory (nigdy zablokowane ani taśmy).
**Przypadki brzegowe:**
- Trafienie: odległość środka pierścienia od osi kreski < `r + w/2` (r = promień pierścienia, w = lokalna szerokość kreski), czyli pierścień widocznie zachodzi na atrament.
- Gumka porusza się szybciej niż napływają zdarzenia: testuj kapsułę (odcinek między kolejnymi pozycjami + promień), nie tylko punkty zdarzeń; uwzględniaj próbki historyczne.
- Kreski zablokowane i kreski pod zablokowanym elementem w trybie `erase_objects` są pomijane.
- Gumka na stronie PDF: kasuje tylko adnotacje, nigdy treść PDF.
**Wskazówka implementacyjna:** `StrokeIndex` — siatka jednorodna 64×64 pt budowana per wersja strony (komórka → lista indeksów kresek; przy copy-on-write odświeżaj tylko dotknięte komórki). Zapytanie po bbox kapsuły, dokładny test odcinek–odcinek dopiero dla kandydatów. Usunięcie = nowa niezmienna lista elementów; do przeliczenia trafiają tylko kafelki przecinające bbox usuniętych kresek.
**Kryteria akceptacji:** (1) strona z 5 000 kresek, 3 s przeciągania: średni koszt obsługi zdarzenia ≤ 1 ms (log debug), brak klatek > 16 ms z winy gumki; (2) usunięta kreska znika najpóźniej 2 klatki po zdarzeniu; (3) undo przywraca wszystkie usunięte kreski na tych samych pozycjach kolejności; (4) kreska zablokowana nie znika.

### EDT-011 — Gumka pikselowa (częściowa) · P0 · L · [RYZYKO]
**Zależy od:** EDT-010, EDT-090.
**Co widzi użytkownik:** wycieranie kawałka kreski; usuwany jest tylko fragment pod pierścieniem, końce pozostałych części są okrągłe, szerokość i nacisk zachowane. Działa też na zakreślaczu; filtr „tylko zakreślacz” (EDT-012) pozwala wycierać żółte tło nie ruszając pisma.
**Opcje/personalizacja:** jak EDT-010 + `eraser.cut_overshoot` 0–1 [0,6] (jak bardzo cięcie wychodzi poza pierścień dla szerokich kresek; patrz niżej).
**Algorytm (zdarzenie gumki z pozycji c0 do c1):**
```
R(k) = r + cut_overshoot · w(k)/2            // w(k) = lokalna szerokość kreski w punkcie k
kandydaci = StrokeIndex.query(bbox(kapsuła c0–c1) + max(R))
dla każdej kandydatki, dla każdego odcinka [Pk, Pk+1]:
    próbkuj ścieżkę gumki co ≤ r/2 i dla każdej próbki rozwiąż równanie kwadratowe odcinek–okrąg(R)
    suma przedziałów t∈[0,1], na których oś kreski jest „pod gumką"
wytnij przedziały; w punktach cięcia interpoluj x, y, p, w
odrzuć kawałki o długości < max(0,6 pt; 0,35·w) lub < 2 punktach
flagi kawałków: pierwszy dziedziczy taper_start, ostatni taper_end, środkowe żadnego
kawałki zajmują miejsce oryginału w kolejności rysowania
```
Cięcie dla `R = r + w/2` daje czystą okręgłą lukę, ale przy szerokich kreskach (zakreślacz 14 pt) usuwa wyraźnie więcej niż widać w pierścieniu; `R = r` zostawia wystające końcówki. Kompromis 0,6 — **dostroić na urządzeniu**.
**Przypadki brzegowe:**
- Pętle zamknięte (litera „o”) przecięte dwa razy dają 2 kawałki (nie sklejamy).
- Cięcie w pobliżu końca kreski zostawia kawałek krótszy od progu → znika (brak „pyłu” z okruchów).
- Kreska pochodząca z auto-kształtu/linijki: nadal zwykła polilinia, ciąć można; flaga `bit1` zostaje.
- Pierwszy kontakt (stuknięcie) działa jak gest o długości 0: usuwa dokładnie koło pod pierścieniem.
- Zakreślacz z multiply: kawałki zachowują tryb mieszania; przy cięciu nie ma podwójnego nakładania, bo każdy kawałek jest osobnym wypełnionym kształtem.
- Gest długi (> 10 s, tysiące kawałków): scalaj w pamięci mapę `oryginał → kawałki` i licz diff względem stanu sprzed gestu dopiero przy UP.
**Wskazówka implementacyjna:** logika w czystym module `StrokeEraser` (wejście: lista kresek, kapsuła; wyjście: diff) testowanym na JVM; podczas gestu wersja strony rośnie najwyżej raz na klatkę (przeciąganie koalescencjonowane), render kafelków tylko w prostokącie zmian. Dla `p` i `w` interpolacja liniowa. Test fuzz: 10 000 losowych kresek i losowych kapsuł → brak NaN, brak kresek z < 2 punktami, żaden nowy punkt poza bbox oryginału ± 0,1 pt, suma długości kawałków + wycięć = długość oryginału ± 0,5 pt.
**Kryteria akceptacji:** (1) przeciągnięcie gumki (r = 9 pt) przez środek prostej kulkowej kreski 100 pt daje 2 kawałki, luka 18–22 pt; (2) undo przywraca 1 oryginał z identyczną tablicą punktów (porównanie bajtowe); (3) na stronie z 2 000 kresek gest trwający 5 s nie spada poniżej 90 fps średnio na Tab S10+ (`gfxinfo`); (4) test fuzz przechodzi.

### EDT-012 — Opcje gumki (cele, rozmiar, podgląd, powrót) · P1 · S
**Zależy od:** EDT-010, EDT-011.
**Co widzi użytkownik:** popover gumki z: przełącznikiem „Cała kreska / Fragment”, suwakiem rozmiaru + 3 szybkimi rozmiarami, filtrem celu („Wszystko / Tylko zakreślacz / Bez zakreślacza”), przyciskiem „Wyczyść stronę” (EDT-013).
**Opcje/personalizacja:** `eraser.filter` [`all`]; `eraser.filter_color` string `#RRGGBB` lub null [null] — kasuj tylko kreski tego koloru (np. wyczyść wszystkie czerwone poprawki; P2); `eraser.auto_return` enum {`never`, `after_stroke`, `after_idle_2s`} [`never`] — powrót do poprzedniego narzędzia po jednym przeciągnięciu / po 2 s bez dotyku; `eraser.size_screen_constant` bool [false]; `eraser.pressure_scales_size` bool [false] — rozmiar ×0,6…×1,6 zależnie od nacisku (P2); `eraser.show_ring` bool [true]; `eraser.hover_highlight` bool [false] — w trybie `stroke` podświetl kreskę, którą usunie dotyk (P2).
**Przypadki brzegowe:** `filter_color` porównuje kolor bez alfa, z tolerancją 0 (dokładny kolor); zmiana filtra w trakcie gestu wchodzi od następnego gestu; `after_idle_2s` nie działa podczas wyciszonego ekranu/hover.
**Wskazówka implementacyjna:** filtr = predykat na `StrokeElement` stosowany w zapytaniu `StrokeIndex` (przed testem geometrii); parametry gumki są częścią presetu gumki (EDT-080), nie osobnym stanem.
**Kryteria akceptacji:** w trybie `highlighter_only` kreska kulkowa pod żółtym zakreślaczem pozostaje nietknięta po zamalowaniu całości; `after_stroke` wraca do poprzedniego narzędzia z tym samym presetem.

### EDT-013 — Wyczyść stronę · P0 · S
**Zależy od:** EDT-010, EDT-090.
**Co widzi użytkownik:** w popoverze gumki i w menu strony: „Wyczyść stronę”; po użyciu Snackbar „Strona wyczyszczona · Cofnij” (5 s). Bez okna potwierdzenia — wystarcza cofanie.
**Opcje/personalizacja:** `eraser.clear_page_scope` enum {`ink`, `ink_and_objects`} [`ink`]: `ink` kasuje kreski; `ink_and_objects` także tekst, obrazy, kształty, wzory (nigdy podkład, nigdy zablokowane). W menu strony dodatkowa pozycja „Wyczyść wszystko (bez zablokowanych)”.
**Przypadki brzegowe:** strona pusta → pozycja nieaktywna; jedna komenda `ClearPageCommand` trzyma referencje do usuniętych elementów (niezmienne, bez kopiowania); przy PDF kasuje adnotacje.
**Wskazówka implementacyjna:** nowa wersja strony z pustą listą (lub tylko zablokowanymi); undo przywraca poprzednią listę (O(1)).
**Kryteria akceptacji:** wyczyszczenie strony z 10 000 kresek trwa < 50 ms i jest w pełni cofalne.

### EDT-014 — Gumka lasso · P2 · S
**Zależy od:** EDT-011, EDT-020.
**Co widzi użytkownik:** w trybie gumki przełącznik „Obwiedź i usuń”: narysowana pętla kasuje wszystko w środku (w trybie `pixel` przycina kreski dokładnie po krawędzi pętli, w trybie `stroke` usuwa kreski w całości trafione pętlą).
**Opcje/personalizacja:** `eraser.lasso_enabled` bool [false]; filtry jak w EDT-012.
**Przypadki brzegowe:** pętla niedomknięta zamykana odcinkiem; sam pierścień gumki przestaje być widoczny podczas obrysu.
**Wskazówka implementacyjna:** ten sam moduł co EDT-011, tylko region = wielokąt zamiast kapsuły (przycinanie poliliny do zewnętrza wielokąta, interpolacja w punktach przecięcia).
**Kryteria akceptacji:** kreska przecinająca pętlę zostaje przycięta w punkcie przecięcia (± 0,5 pt); undo przywraca stan sprzed obrysu.

---

## 4. Presety i szybkie przełączanie

### EDT-080 — Presety piór/zakreślaczy/gumek (sloty) · P0 · M · [MODEL]
**Zależy od:** EDT-001..004, EDT-010, EDT-011.
**Co widzi użytkownik:** w pasku narzędzi pod aktywnym narzędziem (pióro / zakreślacz / gumka) rząd **slotów presetów**: kółko w kolorze preset-u, a jego średnica odzwierciedla grubość (kolor + grubość widoczne na jeden rzut oka). Stuknięcie slotu wybiera preset (narzędzie, kolor, grubość, krycie, wygładzanie). Stuknięcie aktywnego slotu otwiera popover (0.4). Długie przytrzymanie: Zmień nazwę · Duplikuj · Przenieś (przeciąganie) · Usuń. Slot „+” zapisuje bieżące ustawienia jako nowy preset.
**Pola presetu:** `id`, `name` (opcjonalna; domyślnie opis „Pióro wieczne · 1,0 mm”), `tool` ∈ {`ballpoint`, `fountain`, `brush`, `pencil`, `highlighter`, `eraser`}, `color`, `width_pt`, `opacity` 10–100, `smoothing` 0–100, `pressure` bool, `shape_assist` bool, `adv` (opcjonalne nadpisania: `minFactor`, `gamma`, `taperStart`, `taperEnd`, `speedK`, `cap`, `blend`, `curve`); dla gumki: `mode`, `size_pt`, `filter`, `filter_color`. Pełny przykład JSON: `05-personalizacja.md` (Model ustawień).
**Fabryczne zestawy (wartości domyślne, `[DEFAULT]`):**

| Rodzina | Slot | Nazwa | Narzędzie | Kolor | Grubość | Krycie | Wygładz. | Nacisk |
|---|---|---|---|---|---|---|---|---|
| Pióro | 1 (wybrany na starcie) | Czarne kulkowe | ballpoint | `#000000` | 0,5 mm | 100 | 20 | wył. |
| Pióro | 2 | Niebieskie wieczne | fountain | `#1565C0` | 1,0 mm | 100 | 30 | wł. |
| Pióro | 3 | Czerwone kulkowe | ballpoint | `#D32F2F` | 0,5 mm | 100 | 20 | wył. |
| Pióro | 4 | Zielone kulkowe | ballpoint | `#2E7D32` | 0,5 mm | 100 | 20 | wył. |
| Pióro | 5 | Czarny pędzel | brush | `#000000` | 3,0 mm | 100 | 40 | wł. |
| Zakreślacz | 1 | Żółty | highlighter | `#FFEB3B` | 5,0 mm | 50 | 35 | wył. |
| Zakreślacz | 2 | Zielony | highlighter | `#69F0AE` | 5,0 mm | 50 | 35 | wył. |
| Zakreślacz | 3 | Różowy | highlighter | `#FF80AB` | 5,0 mm | 50 | 35 | wył. |

| Rodzina | Slot | Nazwa | Tryb | Rozmiar | Filtr |
|---|---|---|---|---|---|
| Gumka | 1 | Mała | pixel | 8 pt | all |
| Gumka | 2 (wybrany na starcie) | Średnia | pixel | 18 pt | all |
| Gumka | 3 | Obiektowa | stroke | 18 pt | all |
| Gumka | 4 | Tylko zakreślacz | pixel | 24 pt | highlighter_only |

**Opcje/personalizacja:** `presets.edit_in_place` bool [true] (zmiany w popoverze zapisują się w aktywnym slocie; po wyłączeniu zmiana jest „tymczasowa”, slot pokazuje kropkę i przycisk „Zapisz do slotu”); liczba presetów w rodzinie 1–12 (`presets.max_per_family` = 12; rząd przewija się poziomo, gdy się nie mieści); `toolbar.strip_style` enum {`dot_width`, `dot`, `swatch_name`} [`dot_width`]; „Przywróć fabryczne” (z potwierdzeniem) dla rodziny; eksport/import presetów jako JSON przez SAF (P2).
**Przypadki brzegowe:** usunięcie ostatniego presetu rodziny zablokowane; preset wskazujący nieznane `tool` (z nowszej wersji) wczytywany jako `ballpoint` z zachowaniem oryginalnego JSON-a przy zapisie; preset z kolorem alfa < 100 w polu `color` i polem `opacity` — efektywne krycie = iloczyn.
**Wskazówka implementacyjna:** presety w jednym kluczu `presets.v1` (JSON, `schema` w korzeniu) w `SharedPreferences`; **kreski nie odwołują się do presetów** (są samowystarczalne: tool, kolor, szerokość, flagi), więc edycja/usunięcie presetu nigdy nie zmienia istniejącego atramentu `[MODEL]`. `PenSpec` (tabela 2.0) dostarcza wartości domyślne dla pól nieobecnych w presecie.
**Kryteria akceptacji:** (1) po świeżej instalacji widoczne dokładnie powyższe sloty; (2) zmiana koloru slotu 2 zachowuje się po restarcie; (3) usunięcie slotu i „Cofnij” w Snackbarze przywraca go na tej samej pozycji; (4) stare kreski nie zmieniają wyglądu po edycji presetu.

### EDT-081 — Szybka zmiana koloru i grubości (popover) · P0 · M
**Zależy od:** EDT-080.
**Co widzi użytkownik:** zmiana koloru lub grubości w maksymalnie **dwóch dotknięciach**: stuknięcie aktywnego slotu → popover (0.4) z: rzędem 8 kolorów (ulubione, potem ostatnie), 5 kropkami szybkich grubości, suwakiem, przyciskiem „więcej kolorów” (pełna paleta, HSV/RGB/hex, pipeta — `05`). Popover zamyka się przy pierwszym dotyku rysikiem na stronie.
**Opcje/personalizacja:** `popover.dismiss_on_draw` bool [true]; `popover.width_slider_scale` enum {`log`, `linear`} [`log`]; `colors.recent_count` 0–16 [8]; długie przytrzymanie koloru → „Dodaj do ulubionych”. Skróty: `[` / `]` — grubość o jeden krok z listy szybkich grubości, `1`…`9` — preset N (klawiatura zewnętrzna, tabela w `06`).
**Przypadki brzegowe:** „ostatnie” uzupełniane przy **zatwierdzeniu kreski** kolorem spoza palety/ulubionych (nie przy samym wybraniu); kolory z alfa < 100 zapisywane jako kolor + krycie; zmiana w trakcie trwającej kreski niemożliwa (popover blokuje się podczas dotyku rysikiem).
**Wskazówka implementacyjna:** stan narzędzia jako niezmienny `ToolState` w `StateFlow`; `NoteView` czyta migawkę przy `ACTION_DOWN`, więc zmiana w trakcie kreski jest niemożliwa i nie wymaga synchronizacji.
**Kryteria akceptacji:** od dotknięcia slotu do pierwszej kreski w nowym kolorze ≤ 2 dotknięcia i ≤ 1 s; popover nie drży ani nie migocze przy zmianie koloru (jedno przeliczenie podglądu na klatkę).

### EDT-082 — Zapamiętywanie narzędzia per zeszyt (i opcjonalnie własne presety zeszytu) · P1 · S
**Zależy od:** EDT-080.
**Co widzi użytkownik:** po powrocie do zeszytu zastajesz to samo narzędzie, preset i pozycję w widoku (nauka w jednym kolorze, matematyka w drugim). Opcjonalnie zeszyt ma **własny zestaw presetów** (np. niebieski „fizyka”).
**Opcje/personalizacja:** `behavior.remember_tool_per_notebook` bool [true]; `presets.scope` enum {`global`, `notebook`} [`global`] (G/Z) — przy `notebook` pierwsze otwarcie kopiuje bieżące presety globalne do zeszytu, dalsze edycje są lokalne; „Użyj presetów globalnych” w menu zeszytu.
**Przypadki brzegowe:** preset usunięty globalnie, ale użyty w zeszycie z własnymi presetami — zeszyt trzyma własną kopię; import zeszytu z `.mnotes` przenosi presety zeszytu.
**Wskazówka implementacyjna:** kolumna `state_json` (TEXT) w tabeli zeszytów: `{"tool":"fountain","presetId":"p_7f3a","presets":{…}|null}`; ta sama kolumna służy NAV „wróć tam, gdzie skończyłem”. Zapis przy `onPause` i przy zmianie narzędzia (debounce 1 s).
**Kryteria akceptacji:** zeszyt A (pióro niebieskie), B (zakreślacz) — przełączanie między nimi przywraca odpowiednio narzędzie i preset, także po zabiciu procesu.

### EDT-083 — Przełączanie z poprzednim narzędziem · P0 · S
**Zależy od:** EDT-080.
**Co widzi użytkownik:** przycisk „⇄” w pasku (opcjonalny) oraz skrót `X` zamieniają bieżące narzędzie z poprzednim (pióro ↔ gumka ↔ lasso) z zachowaniem presetów. Tymczasowe nadpisania (przycisk S Pen, ogon gumki) zawsze wracają do narzędzia sprzed nadpisania.
**Opcje/personalizacja:** `toolbar.show_swap_button` bool [false]; akcja `previous_tool` przypisywalna do przycisku S Pen i gestów (EDT-072, `06`).
**Przypadki brzegowe:** poprzednie narzędzie usunięte z paska → pióro; głębokość historii 2 (bieżące + poprzednie); tymczasowe nadpisanie nie zmienia „poprzedniego”.
**Wskazówka implementacyjna:** `ToolStack` = para (`base`, `previous`) + lista nadpisań tymczasowych (`overrides`); efektywne narzędzie = ostatnie nadpisanie albo `base`.
**Kryteria akceptacji:** pióro → gumka → `X` → pióro z tym samym presetem; przytrzymanie przycisku S Pen i puszczenie nie psuje „poprzedniego”.

### EDT-084 — Pipeta · P1 · S
**Zależy od:** EDT-081.
**Co widzi użytkownik:** w pełnej palecie przycisk „Pipeta”: dotknięcie i przeciąganie po stronie pokazuje lupę (średnica 80 dp, 8× powiększenie, hex pod kursorem), puszczenie ustawia kolor presetu.
**Opcje/personalizacja:** `colors.eyedropper_source` enum {`page`, `ink_only`} [`page`] — z całej wyrenderowanej strony albo tylko z atramentu (ignoruje szablon/PDF).
**Przypadki brzegowe:** pobieranie z półprzezroczystego zakreślacza daje kolor po zmieszaniu (przy `ink_only` — kolor kreski); odczyt z kafelka jeszcze niezrenderowanego → komunikat „Poczekaj chwilę”.
**Wskazówka implementacyjna:** `NoteView.sampleColorAt(x, y)` czyta piksel z bitmapy aktualnego kafelka (bez `PixelCopy`); lupa to prosta nakładka z `Bitmap.createBitmap` 11×11 px powiększoną bez wygładzania.
**Kryteria akceptacji:** próbkowanie czerwonego pisma zwraca ten sam hex co kolor kreski (± 1 poziom) przy `ink_only`.

### EDT-085 — Zaznacz wszystko w kolorze / masowa zmiana koloru · P2 · S
**Zależy od:** EDT-020, EDT-025.
**Co widzi użytkownik:** w menu „⋯” lasso: „Zaznacz wszystko w tym kolorze (strona)” oraz „Zmień kolor wszystkich… (strona)”. Przydatne do prostej korekty (np. wszystkie czerwone poprawki → granat).
**Opcje/personalizacja:** `lasso.select_same_color_tolerance` 0–30 [8] (odległość w RGB w skali 0–255).
**Przypadki brzegowe:** zakres „cały zeszyt” poza zakresem tego wpisu (P3: operacja wsadowa na wielu stronach z jednym, grupowym undo).
**Wskazówka implementacyjna:** zaznaczenie po predykacie koloru z `StrokeIndex` całej strony; zmiana koloru jako zwykła `RestyleCommand`.
**Kryteria akceptacji:** na stronie z 3 odcieniami czerwieni tolerancja 8 wybiera tylko jeden z nich.

### EDT-086 — Menu radialne pod piórem · P2 · M
**Zależy od:** EDT-072, EDT-080.
**Co widzi użytkownik:** wywołane przyciskiem S Pen (klik) lub długim dotknięciem dwoma palcami; wokół punktu dotknięcia pojawia się pierścień 6–8 pozycji (promień 120 dp): pióro, zakreślacz, gumka, lasso, cofnij, ponów, kolor (kolejny preset), pasek narzędzi. Wybór przez przeciągnięcie w kierunku pozycji i puszczenie.
**Opcje/personalizacja:** `radial.trigger` enum {`off`, `spen_click`, `two_finger_hold`} [`off`]; `radial.items` lista identyfikatorów akcji (te same co w EDT-072) [`pen`, `highlighter`, `eraser`, `lasso`, `undo`, `next_preset`]; `radial.size_dp` 160–280 [240].
**Przypadki brzegowe:** niedostępne zdarzenia przycisku (Air command, EDT-072) → wyzwalacz dwoma palcami; menu nie wychodzi poza ekran (przesuwa środek).
**Wskazówka implementacyjna:** rysowane w nakładce `NoteView` (nie Compose), żeby pojawiało się bez opóźnienia; hit-test po kącie.
**Kryteria akceptacji:** menu pojawia się w ≤ 100 ms od wyzwalacza; wybór pozycji nie zostawia kreski na stronie.

---

## 5. Lasso i zaznaczanie

### 5.0 Maszyna stanów zaznaczenia

```
Idle ─DOWN poza zaznaczeniem→ LassoDrawing ─UP→ hit-test → Selected(ids) | Idle (pusta pętla)
Selected ─DOWN na uchwycie→ Scaling/Rotating ─UP→ TransformCommand
Selected ─DOWN wewnątrz bbox→ Moving ─UP→ TransformCommand
Selected ─stuknięcie poza→ Idle   (jeśli lasso.tap_empty_clears)
Selected ─DOWN poza bbox + ruch→ nowe lasso (tryb replace/add)
```

Zaznaczenie to nakładka `Selection(pageId, ids, matrix, bounds)`. Podczas manipulacji zaznaczone elementy są **wyłączone z kafelków** i rysowane w nakładce z macierzą transformacji (na `RenderNode`/`Picture` zarejestrowanym raz, więc koszt klatki nie zależy od liczby kresek); przy puszczeniu powstają nowe niezmienne elementy ze współrzędnymi przekształconymi **zawsze od oryginałów** (bez dryfu akumulacji) i jedna komenda cofania.

### EDT-020 — Lasso swobodne · P0 · M
**Zależy od:** EDT-076, EDT-090, EDT-022.
**Co widzi użytkownik:** obrysowujesz pętlę (przerywana linia „marszerujące mrówki” 1,5 dp, kolor akcentu); po puszczeniu pętla domyka się automatycznie, zaznaczone elementy dostają ramkę z uchwytami i pasek kontekstowy (EDT-023).
**Opcje/personalizacja:** `lasso.select_mode` enum {`inside_50`, `touch`, `fully_inside`} [`inside_50`] — element jest zaznaczony, gdy ≥ 50% jego długości (kreski) lub powierzchni (pola/obrazu) leży wewnątrz / pętla go dotyka / jest w całości wewnątrz; `lasso.targets` zestaw {`ink`, `highlighter`, `text`, `images`, `shapes`, `formulas`, `tape`} [wszystkie oprócz `tape`] — checkboxy w popoverze; `lasso.tap_empty_clears` bool [true]; `lasso.tap_selects_object` bool [true] — stuknięcie w pole tekstowe/obraz/kształt zaznacza je, w kreskę (promień 10 dp) zaznacza kreskę; `lasso.new_selection_mode` enum {`replace`, `add`} [`replace`] (z klawiaturą: Shift = `add`); `lasso.line_color` [kolor akcentu].
**Przypadki brzegowe:**
- Wypełnienie pętli regułą **nonzero** (kółko narysowane dwa razy nadal zaznacza, ósemka obejmuje obie pętle).
- Pętla krótsza niż 6 dp po przekątnej bbox traktowana jak stuknięcie.
- Test `inside_50` dla kreski: próbkowanie co 2 pt (maks. 64 próbek); dla pól/obrazów siatka 8×8 w prostokącie elementu (z uwzględnieniem obrotu).
- Zaznaczenie obejmuje tylko stronę, na której zaczęto pętlę (0.6); elementy zablokowane pomijane (zaznaczalne tylko stuknięciem → chip „Odblokuj”).
- „Zaznacz wszystko na stronie” (Ctrl+A / menu „⋯”).
**Wskazówka implementacyjna:** pętla zbierana z filtrem odległości 2 dp (bez wygładzania); hit-test: najpierw `StrokeIndex` po bbox pętli, potem punkt-w-wielokącie (ray casting) na próbkach. Pętla rysowana w nakładce; całość < 2 ms dla 5 000 kresek na stronie.
**Kryteria akceptacji:** (1) stuknięcie w pustym miejscu zdejmuje zaznaczenie; (2) pętla wokół słowa zaznacza wszystkie jego kreski i żadnej z wiersza poniżej (kreski przecięte w mniej niż 50%); (3) zaznaczenie 500 kresek i przeciąganie utrzymuje ≥ 90 fps.

### EDT-021 — Lasso prostokątne · P1 · S
**Zależy od:** EDT-020.
**Co widzi użytkownik:** w popoverze lasso przełącznik „Swobodne / Prostokąt”; prostokąt rysowany przeciągnięciem po przekątnej.
**Opcje/personalizacja:** `lasso.shape` enum {`freeform`, `rectangle`} [`freeform`]; opcjonalnie dwie osobne ikony w pasku (`toolbar` w `05`).
**Przypadki brzegowe:** prostokąt o szerokości lub wysokości < 6 dp traktowany jak stuknięcie; te same tryby `select_mode`.
**Wskazówka implementacyjna:** ta sama ścieżka co EDT-020 z wielokątem 4-punktowym.
**Kryteria akceptacji:** prostokąt wokół akapitu zaznacza go tak samo jak pętla o tym samym obrysie.

### EDT-022 — Uchwyty: przesuwanie, skalowanie, obrót · P0 · M
**Zależy od:** EDT-020, EDT-090.
**Co widzi użytkownik:** ramka zaznaczenia z 8 uchwytami (rogi: skalowanie proporcjonalne, środki boków: rozciąganie) i uchwytem obrotu 28 dp nad górną krawędzią; przeciągnięcie wewnątrz ramki przesuwa. Podczas obrotu widać kąt (np. „37°”), przyciąganie do wielokrotności 15° (miękkie, ±3°) z lekkim tyknięciem haptycznym.
**Opcje/personalizacja:** `lasso.handle_size_dp` 8–24 [12]; `lasso.handle_hit_dp` 32–64 [44]; `lasso.rotate_snap_deg` 0–45 [15] (0 = wyłączone); `lasso.allow_stretch` bool [true]; `lasso.scale_stroke_width` bool [true] (szerokość kresek skaluje się z `sqrt(|sx·sy|)`); `lasso.drag_with_finger` bool [true] (palec w trybie przewijania przeciąga zaznaczenie zamiast przewijać, gdy dotyk zaczyna się wewnątrz ramki); strzałki klawiatury: 1 pt (Shift: 10 pt).
**Przypadki brzegowe:**
- Pola tekstowe i obrazy przechowują `x, y, w, h, rot` — transformacja aktualizuje te pola; kreski dostają przekształcone punkty (nie mają własnego obrotu).
- Rozciąganie pola tekstowego zmienia **szerokość** (zawijanie), nie rozmiar czcionki; skalowanie rogiem skaluje czcionkę.
- Zaznaczenie mieszane (kreski + obraz + tekst) transformuje wszystko względem środka bbox zaznaczenia.
- Zaznaczenie bardzo małe (< 24 dp): uchwyty rozsuwają się na zewnątrz ramki (hit area nie nachodzi na wnętrze).
- Zaznaczenie wychodzące poza stronę: dozwolone (render przycięty, 0.6).
**Wskazówka implementacyjna:** macierz afiniczna 2×3 składana z gestu; zawsze licz od oryginalnych współrzędnych; kwantyzacja do 1/16 pt dopiero przy zapisie. Obrót kreski o dowolny kąt zachowuje szerokość (obrót nie zmienia `w`).
**Kryteria akceptacji:** (1) obrót o 90° i z powrotem daje współrzędne zgodne z oryginałem ± 0,1 pt; (2) undo po złożonym geście (przesuń + obróć + skaluj w jednym dotknięciu) przywraca stan sprzed gestu jednym krokiem; (3) uchwyty trafiane palcem z odległości ≤ 22 dp od środka uchwytu.

### EDT-023 — Pasek kontekstowy zaznaczenia · P0 · M
**Zależy od:** EDT-020, EDT-024, EDT-025.
**Co widzi użytkownik:** pływający pasek nad ramką (pod nią, jeśli brak miejsca; zawsze w widocznej części ekranu): **Kopiuj · Wytnij · Duplikuj · Usuń · Kolor · Grubość · ⋯**. W „⋯”: Na wierzch / Na spód (EDT-027), Zablokuj (EDT-027), Odbij poziomo / pionowo, Dopasuj do kształtu (EDT-030), Wzór → LaTeX (EDT-046, tylko gdy model dostępny), Kopiuj jako obraz, Udostępnij jako PNG (`07`), Dodaj do Elementów (EDT-055), Wyrównaj (EDT-029), Zaznacz wszystko w kolorze (EDT-085). Pasek znika na czas przeciągania i wraca po puszczeniu.
**Opcje/personalizacja:** `lasso.context_bar_items` lista z kolejnością i widocznością [Kopiuj, Wytnij, Duplikuj, Usuń, Kolor, Grubość, ⋯]; `lasso.context_bar_position` enum {`above`, `below`, `docked_toolbar`} [`above`] — `docked_toolbar` przenosi akcje do głównego paska (nie zasłaniają pisma).
**Przypadki brzegowe:** zaznaczenie częściowo poza ekranem → pasek przy widocznej części ramki; podczas pinch-zoom/scrolla pasek ukryty do 100 ms po zatrzymaniu; w trybie tylko do odczytu pasek niedostępny; „Kolor/Grubość” ukryte dla zaznaczenia samych obrazów (pojawia się „Krycie”).
**Wskazówka implementacyjna:** Compose `Popup` z offsetem liczonym z macierzy `NoteView` (przeliczanie ≤ 1 raz na klatkę); stan zaznaczenia jako `StateFlow<SelectionUi>` publikowany z widoku.
**Kryteria akceptacji:** każda akcja z paska to jeden krok cofania; pasek nie zasłania ramki ani uchwytów (odsunięcie ≥ 12 dp); nie powoduje spadku płynności przy scrollu (ukryty).

### EDT-024 — Schowek wewnętrzny: wytnij/kopiuj/wklej/duplikuj · P0 · M
**Zależy od:** EDT-020, EDT-090.
**Co widzi użytkownik:** Kopiuj/Wytnij/Wklej działają między stronami, zeszytami i oknami aplikacji; po wklejeniu elementy są od razu zaznaczone, gotowe do przeciągnięcia. „Duplikuj” tworzy kopię przesuniętą o (16, 16) pt.
**Opcje/personalizacja:** `clipboard.paste_position` enum {`viewport_center`, `original_offset`, `last_touch`} [`viewport_center`]; `clipboard.duplicate_repeat_offset` bool [true] — kolejne „Duplikuj” po przesunięciu kopii powtarza ostatnie przesunięcie (jak w programach graficznych); `clipboard.system_sync` bool [true] — zapis także do schowka systemowego (patrz niżej).
**Przypadki brzegowe:**
- Schowek wewnętrzny = serializacja elementów **tym samym kodekiem, co blob strony** (`MNP1`): współrzędne względem lewego górnego rogu bbox, obrazy z odwołaniem do zasobu (hash).
- Schowek systemowy: `ClipData` z URI do pliku w cache (FileProvider, własny MIME `application/x-mnotes-elements`) + obraz PNG zaznaczenia (≤ 2048 px dłuższego boku) + `text/plain` dla pól tekstowych. Binder przenosi tylko URI, więc rozmiar nie jest problemem.
- Wklejenie do innego zeszytu: zasoby obrazów kopiowane do magazynu docelowego, jeśli zasoby są per zeszyt; stoper: wklejenie 10 MB obrazu < 300 ms od akcji do widoczności (reszta w tle).
- Wklej do strony tylko do odczytu / zablokowanej → niemożliwe (przycisk nieaktywny).
- Wytnij = kopiuj + usuń jako jeden krok cofania; Wklej = jeden krok.
**Wskazówka implementacyjna:** `ElementClipboard` (singleton w pamięci, ostatnie 1 wpisu); eksport do schowka systemowego w korutynie, żeby nie blokować UI przy dużym zaznaczeniu (PNG renderowany leniwie — URI obrazu rejestrowany po zakończeniu).
**Kryteria akceptacji:** skopiowanie 200 kresek i wklejenie do innego zeszytu zachowuje względne położenie (± 0,1 pt); Ctrl+C / X / V i Ctrl+D działają; wklejenie z pustego schowka nic nie robi (bez wyjątku).

### EDT-025 — Zmiana koloru/grubości/krycia zaznaczenia · P0 · S
**Zależy od:** EDT-020, EDT-081.
**Co widzi użytkownik:** w pasku kontekstowym „Kolor” otwiera mini-paletę (ta sama co przy piórze), „Grubość” — suwak. Zmiana działa na żywo na całym zaznaczeniu; dla obrazów dostępne „Krycie”, dla tekstu kolor tekstu.
**Opcje/personalizacja:** `lasso.recolor_mode` enum {`replace`, `tint_keep_alpha`} [`replace`] — przy `tint_keep_alpha` zmienia się odcień z zachowaniem krycia poszczególnych kresek (przydatne po mieszaniu zakreślaczy).
**Przypadki brzegowe:** zmiana koloru zakreślacza zachowuje multiply; ołówek zachowuje ziarno; zmiana grubości ustawia **bezwzględną** wartość `width` dla wszystkich kresek zaznaczenia (rozkład nacisku w `p` pozostaje); szerokości poza zakresem pióra przycinane do jego limitów.
**Wskazówka implementacyjna:** `RestyleCommand(ids, stare style, nowe style)`; podczas przeciągania suwaka podgląd w nakładce zaznaczenia, jeden commit przy puszczeniu.
**Kryteria akceptacji:** zmiana koloru 300 kresek ≤ 1 klatka podglądu, jeden krok cofania.

### EDT-026 — Przenoszenie zaznaczenia między stronami + autoprzewijanie · P1 · M
**Zależy od:** EDT-022.
**Co widzi użytkownik:** przeciągnięcie zaznaczenia ku krawędzi widoku powoduje płynne przewijanie; upuszczenie nad inną stroną przenosi tam elementy (położenie względem lewego górnego rogu strony zachowane).
**Opcje/personalizacja:** `lasso.autoscroll_edge_dp` 24–120 [56]; `lasso.autoscroll_max_dps` 400–3000 [1200] (prędkość rośnie liniowo od 0 do maksimum na odcinku strefy); `lasso.cross_page_drop` bool [true].
**Przypadki brzegowe:** strony różnych rozmiarów — położenie zachowane w pt, ograniczone tak, by lewy górny róg bbox ≥ (0,0) strony docelowej; upuszczenie w odstępie między stronami → strona o największym przecięciu; autoprzewijanie zatrzymuje się na końcu zeszytu (opcjonalnie dodaje stronę, jeśli `behavior.auto_add_page` — `06`).
**Wskazówka implementacyjna:** `MoveToPageCommand` modyfikuje dwie strony i jest jednym krokiem cofania; zaznaczenie podczas przeciągania jest nakładką widoku, niezależną od strony.
**Kryteria akceptacji:** przeciągnięcie bloku 100 kresek ze strony 1 na stronę 3 i cofnięcie przywraca obie strony identycznie (porównanie bajtowe blobów).

### EDT-027 — Kolejność warstw i blokada elementów · P1 · S · [MODEL]
**Zależy od:** EDT-090, EDT-023.
**Co widzi użytkownik:** w „⋯”: **Na wierzch / Na spód / Do przodu / Do tyłu** oraz **Zablokuj / Odblokuj**. Zablokowany element nie daje się zaznaczyć pętlą, wymazać ani przypadkowo przesunąć (stuknięcie pokazuje chip „Zablokowane · Odblokuj”).
**Opcje/personalizacja:** `images.lock_after_insert` bool [false]; `pen.highlighter.keep_under_ink` bool [false] (zakreślacz trafia do pasma „pod”, EDT-090).
**Przypadki brzegowe:** kolejność jest lokalna w paśmie (EDT-090); „Na wierzch” nie przenosi elementu ponad taśmy; blokada zapisana we `flags` elementu (bit2).
**Wskazówka implementacyjna:** zmiana kolejności to przeniesienie w liście elementów (komenda z indeksami przed/po).
**Kryteria akceptacji:** zablokowany obraz pozostaje na miejscu przy pętli obejmującej całą stronę i przy gumce `erase_objects`.

### EDT-028 — Grupowanie · P3 · M · [MODEL]
**Zależy od:** EDT-027.
**Co widzi użytkownik:** „Grupuj / Rozgrupuj” w „⋯”; zaznaczenie jednego elementu grupy zaznacza całą grupę, transformacje działają na całość.
**Opcje/personalizacja:** brak.
**Przypadki brzegowe:** grupa nie może łączyć elementów z różnych stron; kopiowanie grupy zachowuje grupę (nowy identyfikator).
**Wskazówka implementacyjna:** pole `group` (varint, 0 = brak) w nagłówku każdego elementu **już w v1** `[MODEL]`; interfejs później.
**Kryteria akceptacji:** blob z `group` ≠ 0 zachowuje wartość w pętli zapis–odczyt; stary czytnik ignoruje pole bez błędu.

### EDT-029 — Wyrównywanie, rozmieszczanie, prowadnice przyciągania · P2 · M
**Zależy od:** EDT-022.
**Co widzi użytkownik:** przy przeciąganiu zaznaczenia pojawiają się różowe prowadnice (`#FF4081`, 1 dp), gdy krawędź lub środek zaznaczenia zbliży się do krawędzi/środka innego elementu albo do środka/marginesów strony; zaznaczenie „przykleja się”. W „⋯ → Wyrównaj”: lewo/środek/prawo, góra/środek/dół, rozmieść poziomo/pionowo (≥ 3 elementy).
**Opcje/personalizacja:** `snap.enabled` bool [true]; `snap.threshold_dp` 4–20 [8]; `snap.to_elements` bool [true]; `snap.to_page` bool [true]; wyłączenie chwilowe: przytrzymanie Alt (klawiatura) lub drugi palec.
**Przypadki brzegowe:** zaznaczenia z setkami kresek — prowadnice liczone dla bbox zaznaczenia, nie dla każdej kreski; kandydaci tylko z elementów w widocznym obszarze ± 1 ekran.
**Wskazówka implementacyjna:** przy starcie przeciągania zbierz posortowane tablice linii X i Y (krawędzie, środki) i szukaj binarnie; prowadnice rysowane w nakładce.
**Kryteria akceptacji:** przeciąganie obrazu do wyrównania z krawędzią innego obrazu przykleja go z dokładnością 0,1 pt; wyłączenie `snap.enabled` usuwa przyciąganie.

<!-- KONIEC-CZESCI-B1 -->
