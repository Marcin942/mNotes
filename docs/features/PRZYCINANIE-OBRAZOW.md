# Przycinanie obrazów (EDT-095)

Zwykłe przycinanie jak w edytorze Galerii Samsunga, bez linii pomocniczych: ramka z uchwytami, którą zawęża się do
środka, żeby widoczna została mniejsza część zdjęcia. Zastąpiło „przecinanie na części” (EDT-094, odroczone:
`ROSCINANIE-OBRAZOW.md`).

## Co robi

Zaznaczony pojedynczy, niezablokowany obraz → przycisk „Przytnij” na pasku zaznaczenia otwiera tryb przycinania
(`Editor.crop`). Pod ramką widać **cały plik** (poza ramką przyciemniony, tylko w czasie edycji).

- **Ramka:** uchwyty w narożnikach i na środkach boków (bez siatki). Przeciągnięcie uchwytu zmienia ramkę, plik stoi
  w miejscu; przeciągnięcie wnętrza przesuwa ramkę po pliku. Ramka nie wychodzi poza plik, najmniejszy bok to 24 pt.
  Róg lub bok, który dotarł do brzegu pliku, **nie grzęźnie**: `resized` rzutuje cel (palec) na najbliższy dozwolony
  punkt (wielokąt ograniczeń liniowych), więc ruch wzdłuż brzegu nadal przesuwa uchwyt. Przy zablokowanych proporcjach
  ramka zatrzymuje się na ścianie, ale nigdy nie wychodzi poza obraz.
- **Pasek:** zwarty (suwak z kątem, proporcje, Anuluj / Cały obraz / Zastosuj), bez opisów. Leży przy ramce, nie na
  stałe u góry: `CropBarHost` + czysta `core/edit/CardPlacement` wybierają pod / nad / z prawej / z lewej, a gdy obraz
  zajmuje prawie cały widok, miejsce zasłaniające najmniej. Dotychczasowa strona trzyma się, dopóki pasuje.
- **Prostowanie:** suwak ±45° wokół najbliższej wielokrotności 90° (z przyciąganiem do 0°) oraz przyciski obrotu
  o 90°. Plik obraca się pod stałą ramką wokół jej środka. Gdy obrócony plik nie wypełnia ramki, ramka się
  zmniejsza; wraca do zamierzonego rozmiaru po wyprostowaniu (`CropSession.want`).
- **Proporcje:** Dowolne, Oryginał, 1:1, 4:3, 3:2, 16:9 oraz zamiana poziom/pion. „Cały obraz” zdejmuje przycięcie
  i kąt.
- **Zastosuj / Anuluj:** zatwierdzenie to jedno cofnięcie (`ElementsEdit`); anulowanie, Esc oraz cofnij/ponów niczego
  nie zmieniają. Backspace w tym trybie nie usuwa obrazu. Dwa palce i palec poza ramką przewijają stronę.
- **Ponowna edycja:** nieniszczące, bo obraz zachowuje cały plik i tylko `ImageEl.crop`; „Przytnij” znów pokazuje
  cały plik z ramką tam, gdzie była.

## Bezpieczeństwo eksportu

Eksport **nie zawiera odciętych pikseli**:

- **PDF** i **SVG:** zamiast pliku z wycinkiem osadzany jest nowy bitmap powstały z samej widocznej, wyprostowanej
  części (`render/CropBake.kt`, 4 px/pt, nie więcej niż rozmiar naturalny). PDF ustawia `renderer.bakeCrops`;
  bitmapy są zwalniane dopiero po `doc.close()`.
- **PNG:** rasteryzuje stronę, więc jest bezpieczny z natury.
- **Archiwum `.mnotes`** (kopia do edycji) nadal zawiera **pełne pliki**. To kopia zapasowa, nie eksport do
  udostępniania.

## Model i kodek

`ImageCrop(l, t, r, b, a)`: ułamki pliku plus kąt `a` obrotu prostokąta wokół jego środka (dodatni: zgodnie z
ruchem wskazówek zegara na ekranie). Ułamki mogą wychodzić poza 0..1 tylko wtedy, gdy kąt ≠ 0. Kodek MNP1:
`FLAG_IMAGE_CROP = 512` (4×f32), `FLAG_IMAGE_TILT = 1024` (kąt); starsze pliki czytają się bez zmian.

## Gdzie to leży w kodzie

| Warstwa | Plik | Zawartość |
|---|---|---|
| Geometria | `core/edit/CropGeom.kt` | ramka, uchwyty, `resized`, `moved`, `fitted`, `quarter`, `CropPlace`, `applied` |
| Stan | `editor/Editor.kt` | `CropSession`, `startCrop`, `cropResize`, `cropMove`, `cropSetAngle`, `cropQuarter`, `applyCrop` |
| Gesty/rysunek | `editor/NoteView.kt` | `Mode.CROP`, `cropDown/Move/Up/Abort`, `drawCrop` |
| UI | `ui/EditorScreen.kt`, `core/edit/CardPlacement.kt` | `CropBarHost`, `CropBar`, przycisk w `SelectionBar`, Esc/Backspace |
| Rysowanie/eksport | `render/PageRenderer.kt`, `render/CropBake.kt`, `data/Transfer.kt` | rysunek pod kątem, `bake` |
| Testy | `CropGeomTest`, `CardPlacementTest`, `PageCodecTest` | geometria, zaokrąglenia, kodek |

Przecinanie na części (`canCutSel`) nie działa na obrazach z kątem ≠ 0.

## Czego nie ma

- odbicie lustrzane i korekta perspektywy (zdjęcie tablicy pod skosem),
- przycinanie kilku obrazów naraz,
- test na tablecie (jest w użyciu): wygoda uchwytów palcem, duże zdjęcia, obrócone obrazy, jakość po eksporcie.
