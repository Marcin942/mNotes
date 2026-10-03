# mNotes — specyfikacja funkcji (żywy dokument)

Folder zawiera specyfikację tego, co mNotes powinno umieć: pełnoekranowa, w pełni offline'owa aplikacja do pisania odręcznego (wiele zeszytów, S Pen, Galaxy Tab S10+), „trochę jak GoodNotes”, ale **płynniejsza, wygodniejsza i bardziej konfigurowalna**. Pliki powstają równolegle z kodem i są aktualizowane. Każdy plik da się czytać samodzielnie.

Dla agenta implementującego: zacznij od `BACKLOG.md` (ID, priorytety, zależności, jednozdaniowe wskazówki), a szczegóły i kryteria akceptacji bierz z plików tematycznych. Postęp odhaczaj w `STATUS.md` (własność agenta głównego) i/lub w checkboxach `BACKLOG.md`.

## Pliki

| Plik | Zawartość | Prefiks ID | Stan |
|---|---|---|---|
| `README.md` | ten indeks, legenda, konwencje | — | gotowy |
| `02-edytor-narzedzia.md` | wszystkie narzędzia edytora i ich opcje (pióra, gumka, lasso, kształty, linijka, tekst, obrazy, laser, ręka, taśma, S Pen, palm rejection) | `EDT` | planowany |
| `05-personalizacja.md` | KATALOG PERSONALIZACJI: tabela ustawień (klucz, typ, zakres, domyślna, zasięg, miejsce w UI), model danych (JSON presetów i paska narzędzi), macierz GoodNotes vs mNotes | `CUS` | planowany |
| `BACKLOG.md` | skonsolidowany, priorytetyzowany backlog ze stałymi ID + non-goals + fale wdrożenia | wszystkie | planowany |
| `01-biblioteka.md` | biblioteka i organizacja: zeszyty, foldery, okładki, widoki, tagi, kosz, wyszukiwanie, import, onboarding | `LIB` | planowany |
| `03-strony-i-szablony.md` | strony, rozmiary, szablony z dokładną geometrią, zarządzanie stronami, adnotacje PDF | `PAG` | planowany |
| `04-nawigacja-i-widok.md` | zoom/pan, tryby przewijania, miniatury, spis treści, zakładki, linki, multi-window, tryb prezentacji | `NAV` | planowany |
| `06-wygoda-i-gesty.md` | gesty, skróty klawiszowe (pełna tabela), cofanie/ponawianie, haptyka, widgety, dostępność, odzyskiwanie po błędach | `CNV` | planowany |
| `07-pliki-i-wymiana.md` | eksport PDF/PNG/tekst, `.mnotes`, auto-backup do SAF, przywracanie, udostępnianie, schowek, druk | `FIL` | planowany |
| `08-bezpieczenstwo-i-prywatnosc.md` | blokady, FLAG_SECURE, szyfrowanie (Keystore), bezpieczne usuwanie, integralność danych | `SEC` | planowany |
| `09-wydajnosc-i-jakosc.md` | budżety wydajności, pomiary, przypadki brzegowe, scenariusze QA (adb) | `PRF` | planowany |
| `10-pomysly-ponad-goodnotes.md` | wyróżniki „ponad GoodNotes”, z oceną kosztu | `EXT` | planowany |

Numeracja plików jest historyczna (kolejność w zleceniu); kolejność czytania przy implementacji: `README` → `BACKLOG` (fale wdrożenia) → `02` → `05` → `03` → `04` → `01` → `06` → `07` → `08` → `09` → `10`.

## Legenda priorytetów

| Kod | Znaczenie |
|---|---|
| **P0** | Musi być w v1. Spójny zestaw do jednego dużego przebiegu implementacji jednego programisty. Ryzykowne pozycje P0 oznaczone `[RYZYKO]`. |
| **P1** | Zaraz po v1 (v1.1–v1.3). Często tanie, bardzo odczuwalne. |
| **P2** | Miło mieć. Wymaga większej pracy lub dotyczy rzadszych scenariuszy. |
| **P3** | Kiedyś / może nigdy. Zapisane, żeby nie zgubić pomysłu i żeby model danych nie zamykał drogi. |

## Legenda nakładu (jeden programista, z testami)

| Kod | Orientacyjnie |
|---|---|
| **S** | do ~0,5 dnia |
| **M** | 1–3 dni |
| **L** | 1–2 tygodnie |
| **XL** | ponad 2 tygodnie |

## Znaczniki specjalne

- `[RYZYKO]` — pozycja o dużej niepewności technicznej (wydajność, API urządzenia, złożona geometria). Plan B opisany we wpisie.
- `[MODEL]` — funkcja może przyjść później, ale **model danych (blob „MNP1”, tabele SQLite, klucze ustawień) musi mieć na nią miejsce już w v1**, żeby uniknąć migracji. Główny agent: zerknij na te pozycje przed zamrożeniem formatu.
- `[DEFAULT]` — wartość domyślna z tabeli jest decyzją projektową (nie do dyskusji bez powodu), pozostałe liczby to punkt startowy do strojenia na urządzeniu.

## Schemat ID

`PREFIKS-NNN` (trzy cyfry, rosnąco, nigdy nie używane ponownie; porzucone pozycje zostają w `BACKLOG.md` jako `WYCOFANE`).

| Prefiks | Obszar | Plik źródłowy |
|---|---|---|
| `LIB` | biblioteka i organizacja | `01-biblioteka.md` |
| `EDT` | edytor i narzędzia | `02-edytor-narzedzia.md` |
| `PAG` | strony, szablony, PDF jako strony | `03-strony-i-szablony.md` |
| `NAV` | nawigacja i widok | `04-nawigacja-i-widok.md` |
| `CUS` | personalizacja / ustawienia | `05-personalizacja.md` |
| `CNV` | wygoda, gesty, skróty, dostępność | `06-wygoda-i-gesty.md` |
| `FIL` | pliki, eksport, backup, wymiana | `07-pliki-i-wymiana.md` |
| `SEC` | bezpieczeństwo i prywatność | `08-bezpieczenstwo-i-prywatnosc.md` |
| `PRF` | wydajność, jakość, QA | `09-wydajnosc-i-jakosc.md` |
| `EXT` | pomysły ponad GoodNotes | `10-pomysly-ponad-goodnotes.md` |

Każdy ID użyty w dowolnym pliku występuje w `BACKLOG.md` (to kontrola spójności; jeśli znajdziesz sierotę, dopisz ją do backlogu małym `Edit`).

## Format wpisu w plikach szczegółowych

```
### ID — Nazwa · P? · nakład · [znaczniki]
**Zależy od:** ID, ID
**Co widzi użytkownik** …
**Opcje/personalizacja** (z domyślnymi; klucze ustawień w nawiasach `…`)
**Przypadki brzegowe** …
**Wskazówka implementacyjna** … (zgodna z architekturą: Compose na chrome, NoteView jako jedyny viewport, kafelki, nakładka wektorowa na żywo, niezmienne elementy, komendy undo/redo)
**Kryteria akceptacji** … (testowalne)
```

## Konwencje jednostek i nazw

| Co | Konwencja |
|---|---|
| Współrzędne dokumentu | punkty `pt` = 1/72 cala (A4 = 595,28 × 841,89 pt), `Float` |
| Grubości piór | w `pt` (0,5 mm ≈ 1,42 pt); wygładzanie i próg czasu w `ms`; odległości UI w `dp` |
| Kolory | `#RRGGBB` lub `#AARRGGBB` (alfa z przodu, jak w Androidzie); w JSON jako string |
| Czas | milisekundy (`ms`), znaczniki czasu jako epoch ms (`Long`) |
| Klucze ustawień | `kropkowana.przestrzen.snake_case`, np. `toolbar.position`, `pen.ballpoint.smoothing`; płaskie klucze w `SharedPreferences`, struktury (presety, układ paska) jako JSON w jednym kluczu |
| Zasięg ustawienia | `G` = globalne · `Z` = per zeszyt (nadpisuje G) · `N` = per narzędzie/preset · `S` = per strona · `U` = per urządzenie (np. tablet vs telefon) |
| Ekran referencyjny | Galaxy Tab S10+ 12,4″, 2800×1752 px, ~266 ppi, 120 Hz, S Pen (nacisk 4096 poziomów, przycisk, hover); telefon 6,1″ jako drugi punkt odniesienia |
| Teksty UI | domyślnie polski (`values/strings.xml`), angielski w `values-en/`; w specyfikacji nazwy po polsku, identyfikatory po angielsku |

## Zasady utrzymania (dla autorów tej specyfikacji)

1. `STATUS.md` należy do agenta głównego, nie edytujemy go.
2. `BACKLOG.md` po utworzeniu modyfikujemy wyłącznie małymi `Edit` po ponownym odczytaniu (agent główny odhacza tam pola).
3. Nowa funkcja = nowy ID w pliku tematycznym **i** linia w `BACKLOG.md`.
4. Liczby (domyślne wartości, zakresy) są wiążące jako propozycja startowa; te oznaczone `[DEFAULT]` to decyzje projektowe, a pozostałe wolno dostroić na urządzeniu (zmianę zapisz we wpisie, żeby specyfikacja nie rozjechała się z kodem).
