# Rościnanie obrazów (przecinanie na części): stan zapisany 2026-10-18

To jest zapis funkcji **„Przetnij obraz na części”**, zrobionej jako pierwsza odpowiedź na prośbę o „przecinanie
obrazów” (BACKLOG EDT-094, scalone jako `b79912a`). Właściciel uznał, że ma potencjał, ale **nie o to mu chodziło**:
potrzebne jest zwykłe **przycinanie** (EDT-095, `docs/features/PRZYCINANIE-OBRAZOW.md`). Rościnanie może wrócić
kiedyś jako **osobne narzędzie**; **teraz** jego przycisk jest schowany (nie ma wejścia z paska zaznaczenia), a kod
zostaje w repozytorium nietknięty, żeby dało się je włączyć jednym wierszem.

## Co robi (tak jak jest w kodzie)

Zaznaczony pojedynczy, niezablokowany obraz → przycisk „Przetnij obraz na części” (ikona `VerticalSplit`) na pasku
zaznaczenia otwiera tryb cięcia (`Editor.cut`). W tym trybie:

- Przeciągnięcie palcem, rysikiem albo myszą **w poprzek obrazu** rysuje linię cięcia. Orientacja wynika z kierunku
  ruchu (przeważa pionowy ruch: linia pionowa), położenie ze środka pociągnięcia. Działa także dla obróconego obrazu
  (linie biegną równolegle do boków obrazu, nie ekranu). Położenie przyciąga się do 1/4, 1/3, 1/2, 2/3, 3/4
  z lekkim stuknięciem (`CLOCK_TICK`).
- Linię można chwycić (tolerancja 16 dp) i przesunąć; wyciągnięcie poza obraz ją usuwa.
- Pasek `CutBar` (góra, pod paskiem narzędzi): „Pionowa” i „Pozioma” dodają linię w środku największej luki,
  „Cofnij linię”, przełącznik „Rozsuń” (odsuwa kawałki o 10 pt), „Przetnij” (zatwierdza), „Anuluj”; podpowiedź, ile
  będzie kawałków.
- Palec poza obrazem i dwa palce przewijają stronę (drugi palec w trakcie rysowania linii przerywa ją).
  Esc anuluje, Backspace nie usuwa obrazu w tym trybie. Cofnięcie/ponowienie kończy sesję.
- Zatwierdzenie zamienia obraz na kawałki **w tym samym miejscu kolejności** jednym `ElementsEdit` (jedno cofnięcie)
  i zaznacza kawałki. Kawałki to niezależne `ImageEl` z tym samym plikiem zasobu i własnym `crop`; dziedziczą krycie,
  blokadę i obrót, można je dalej przecinać (wycinek liczony względem wycinka rodzica). Najmniejszy kawałek: 14 pt.

## Gdzie to leży w kodzie

| Warstwa | Plik | Zawartość |
|---|---|---|
| Model | `core/model/Elements.kt` | `ImageCrop(l,t,r,b)` (ułamki pliku), `ImageEl.crop` |
| Kodek | `core/codec/PageCodec.kt` | flaga `FLAG_IMAGE_CROP = 512` + 4×f32, wstecznie zgodne |
| Logika | `core/edit/ImageCut.kt` | `CutLine`, `toLocal`/`toPage` (strona ↔ ułamki obrazu z obrotem), `snap`, `fits`, `nextPos`, `normalize`, `split` |
| Stan | `editor/Editor.kt` | `CutSession(pageId, imageId)` z `lines` i `spread`; `startCut`, `cancelCut`, `addCutLine`, `undoCutLine`, `cutPieceCount`, `applyCut` |
| Gesty/rysunek | `editor/NoteView.kt` | `Mode.CUT`, `cutDown`/`cutMove`/`cutUp`/`cutAbort`, `drawCut` |
| UI | `ui/EditorScreen.kt` | `CutBar`, wpis w `SelectionBar` (obecnie schowany), Esc/Backspace |
| Test | `app/src/test/.../core/ImageCutTest.kt` | podział, wycinek rodzica, obrót, przyciąganie |

Wycinek `ImageCrop` jest nieniszczący i wspólny z nowym narzędziem przycinania (EDT-095): po rozszerzeniu o kąt
(`ImageCrop.a`, flaga 1024) kawałki z kątem ≠ 0 **nie są przecinane** (`canCutSel` to sprawdza).

## Jak włączyć z powrotem (gdy będzie osobne narzędzie)

W `ui/EditorScreen.kt`, w `SelectionBar`, przy przycisku „Przytnij” dopisać wiersz:

```kotlin
if (!locked && els.size == 1 && els[0] is ImageEl && !ed.readOnly && ed.canCutSel())
    IconBtn(Icons.Rounded.VerticalSplit, T.t("Przetnij obraz na części", "Cut image into pieces"), { ed.startCut() }, size = 36.dp, iconSize = 20.dp)
```

Pozostałe elementy (sesja, tryb, pasek, Esc) działają bez zmian.

## Czego brakuje (pomysły na osobne narzędzie)

- cięcia ukośne i odręczne (dowolny kształt kawałka, np. wycięcie fragmentu po konturze),
- „Przywróć cały obraz” (scalenie kawałków tego samego pliku z powrotem),
- ujęcie kawałków w grupę, żeby przesuwały się razem,
- cięcie obrazu z kątem prostowania (po EDT-095 `ImageCrop.a ≠ 0`),
- test na tablecie: wygoda gestu palcem na dużym obrazie, obrócone obrazy, cofnięcie (nie sprawdzone, tablet jest
  w użyciu).

## Dlaczego to nie było to, o co chodziło

Właściciel chciał przycięcia jak w edytorze Galerii Samsunga: ramka z uchwytami, którą zawęża się do środka, żeby
została widoczna mniejsza część zdjęcia (bez linii pomocniczych), z możliwością ponownej edycji, z prostowaniem
krzywo zrobionego zdjęcia i z eksportem, który zapisuje wyłącznie widoczną część. Przecinanie na części odpowiada na
inną potrzebę (rozdzielenie obrazu na samodzielne elementy).
