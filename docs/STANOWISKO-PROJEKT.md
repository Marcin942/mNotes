# Stanowisko od nowa (2d-A1) — projekt

Uwagi autora (28.10, po teście na tablecie): między dokumentami jest przestrzeń, zoom działa dziwnie
(notatka lekko wibruje), ruch staje od razu po oderwaniu palca. **Stanowisko ma działać jak jeden dokument**, którego
część stron leży w jednym rzędzie, a część w drugim.

## 1. Dlaczego od nowa, a nie łatka

Stary `ui/StationCanvas.kt` (2b-A7) to N osobnych `NoteView`, każdy z własnym powiększeniem i przesunięciem,
ułożonych przez Compose jeden pod drugim:

- **przerwa** między rzędami to stałe 10 dp, a w dokumencie odstęp stron to 14 pt × zoom plus marginesy rzędów 2 × 20 pt
  — przy każdym zoomie inaczej;
- **zoom** synchronizowany sprzężeniem zwrotnym: rząd A zmienia swój zoom, zgłasza go (`onZoomChange`), pole przelicza
  przewinięcie, a pozostałe rzędy „doganiają” (`followZoom`, próg 1e-4) wokół *własnego* środka, nie wokół palców.
  Każda klatka szczypania to kilka przeliczeń w różnej kolejności, a wysokości rzędów zaokrąglane do pikseli
  (`roundToInt`) przesuwają treść o ±1 px — stąd drganie;
- **pion** przewijał osobny `detectVerticalDragGestures` bez inercji, poziom — `OverScroller` rzędu. Ruch po skosie
  rozpadał się na dwa niezależne mechanizmy.

Każdy z tych błędów wynika z tego, że stan widoku jest rozproszony po N widokach. Naprawa: **jeden stan widoku dla
całego stanowiska**, z którego wszystko inne jest wyliczane, i ruch płynący tylko w jedną stronę (model → widoki).

## 2. Model: `core/layout/Workspace`

Czysta logika (bez Androida), testowana na JVM.

**Pasy (lanes).** Dokument stanowiska to pas. W układzie **poziomym** (strony obok siebie) pas to rząd: oś stron = x,
oś wspólna = y. W układzie **pionowym** pas to kolumna: oś stron = y, oś wspólna = x. Dalej: *m* — oś stron, *c* — oś
wspólna (poprzeczna).

**Przestrzeń stanowiska (pt).** Każdy pas ma swój `DocLayout` (w kierunku stanowiska). W osi wspólnej pasy leżą tak,
jakby były kolejnymi stronami jednego dokumentu:

```
P₀ = pad                              (pad = DocLayout.pad = 20 pt, jak brzeg dokumentu)
Pᵢ₊₁ = Pᵢ + Sᵢ + gap                  (Sᵢ = największa strona pasa w osi wspólnej, gap = DocLayout.gap = 14 pt)
całość C = P_last + S_last + pad
przesunięcie pasa Oᵢ = Pᵢ − pad       (punkt pasa w osi wspólnej: c_stanowiska = c_pasa + Oᵢ)
```

Odstęp stron sąsiednich pasów = `gap`, dokładnie jak odstęp stron w dokumencie, przy każdym zoomie (skaluje się
razem z treścią). Przy krawędziach `pad`, jak w jednym dokumencie.

**Stan widoku** (jedyne źródło prawdy):

- `zoom` z (px/pt) — jeden dla wszystkich pasów;
- `cross` k (px) — przesunięcie wspólne: ekran_c = c_stanowiska · z + k;
- `main[i]` (px) — przesunięcie pasa i w jego osi stron: ekran_m = m_pasa · z + main[i].

Macierz pasa i (to, czym rysuje `NoteView`): poziomo `tx = main[i]`, `ty = k + Oᵢ·z`; pionowo `tx = k + Oᵢ·z`,
`ty = main[i]`.

**Ograniczenia (clamp)** jak w jednym dokumencie (`NoteView.clampX/clampY`): oś wspólna — całość C·z mniejsza od okna
→ wyśrodkowana, inaczej k ∈ [okno − C·z, 0]; oś stron pasa — tak samo z długością pasa `docWidth`/`docHeight`.

**Pas pod punktem.** Granice pasów na ekranie leżą w połowie odstępu: pas i zajmuje w osi wspólnej
[(Pᵢ − gap/2)·z + k, (Pᵢ + Sᵢ + gap/2)·z + k]; pierwszy sięga do −∞, ostatni do +∞ (dotyk na marginesie należy do
skrajnego pasa).

## 3. Transformacje ekran ↔ (dokument, strona, punkt strony)

- `laneAt(sx, sy)` — pas pod punktem ekranu (wg granic wyżej).
- `toDoc(i, sx, sy)` = ((sx − txᵢ)/z, (sy − tyᵢ)/z); `toScreen(i, dx, dy)` odwrotnie.
- `locate(sx, sy)` → (pas, strona, x, y na stronie): strona przez `DocLayout.nearest/pageAt` pasa.
- **Przeniesienie elementu** z pasa a, strony p do pasa b, strony q tak, by nie drgnął na ekranie:
  przesunięcie (Δx, Δy) pt = punkt ekranu źródła przeliczony do układu strony q minus jego położenie na stronie p
  (`Workspace.relocate`). Zoom jest wspólny, więc wystarcza przesunięcie, bez skalowania.

## 4. Gesty

Kontener `editor/WorkspaceView` (jeden `ViewGroup`) dostaje dotyk i przy ACTION_DOWN wybiera pas pod palcem; cały gest
idzie do `NoteView` tego pasa (narzędzia, lasso, menu działają bez zmian). Pas, którego dotknięto, staje się aktywny.

`NoteView` dalej rozpoznaje, czy palec pisze, czy nawiguje (te same ustawienia palca, odrzucanie dłoni, stuknięcia,
przytrzymanie). **Na stanowisku nie zmienia jednak sam swojej macierzy** — każdą zmianę widoku zgłasza kontenerowi
(`LaneHost`):

- `pan(pas, dx, dy)` — składowa w osi stron przesuwa tylko ten pas, składowa wspólna — całe stanowisko, **naraz** w tym
  samym wywołaniu (ruch po skosie jest swobodny jak w dokumencie). Zwraca, ile ruchu w osi stron zmieściło się do
  końca pasa — reszta to odciągnięcie „nowa strona” jak w dokumencie;
- `zoomAbout(px, py, f)` — patrz §6;
- `fling(pas, vx, vy)` — patrz §5;
- `animateTo(pas, z, tx, ty)` — przejście do strony, dopasowanie, podwójne stuknięcie: cel pasa przeliczony na stan
  stanowiska (pozostałe pasy skalują się wokół środka okna);
- `shift(pas, dx, dy)` — przesunięcie bez odciągnięcia (zmiana kolejności stron, wzrost tablicy).

Kontener zmienia model i **w tej samej klatce** wpisuje każdemu pasowi gotową macierz (`setViewport(z, tx, ty)`), bez
wywołań zwrotnych. Nie ma „widok A ustawia, widok B dogania”.

Dotknięcie w dowolnym pasie zatrzymuje trwający rzut (jak w dokumencie).

## 5. Inercja (fling): `core/layout/Fling`

Te same wzory i parametry co `android.widget.OverScroller`, którym leci jeden dokument (krzywa „spline” Androida):

```
tarcie        f = ViewConfiguration.getScrollFriction() (0,015)
współczynnik  g = 9,80665 · 39,37 · (gęstość · 160) · 0,84
spowolnienie  D = ln 0,78 / ln 0,9 ≈ 2,358,  punkt przegięcia 0,35
l = ln(0,35 · |v| / (f · g))
czas T = e^(l / (D − 1))  [s],   droga L = f · g · e^(D / (D − 1) · l)
```

Położenie w czasie: s(t) = L · (1 − (1 − t/T)^q), q = |v|·T / L — prędkość na starcie równa prędkości palca, maleje
płynnie do zera w chwili T. Dwie osie liczone osobno (jak w `OverScroller`): oś stron pasa pod palcem i oś wspólna.
Granica: oś, która dochodzi do końca (clamp z §2), staje na granicy, druga leci dalej. Zegar wstrzykiwany (czas klatki
z `Choreographer`), więc w testach JVM liczy się na sztucznym zegarze.

Jeden dokument zostaje na `OverScroller` (bez ryzyka regresji); stanowisko liczy tym samym wzorem — ta sama droga
i czas rzutu przy tej samej prędkości. Gdy autor potwierdzi, że stanowisko „czuje się” tak samo, jeden dokument też
może przejść na `Fling` (jedna ścieżka kodu).

## 6. Zoom

Szczypanie: `zoomAbout(px, py, f)` na modelu: z' = clamp(z·f), k' = p_c − (p_c − k)·z'/z, main'[j] = p_m − (p_m −
main[j])·z'/z dla **każdego** pasa, potem clamp. Punkt pod palcami stoi (dokładnie, poza sytuacją, gdy clamp dosuwa
treść do krawędzi — jak w dokumencie), pozostałe pasy skalują się względem tego samego punktu, więc całość wygląda jak
skalowanie jednego dokumentu. Brak sprzężenia zwrotnego i zaokrągleń (pasy nie zmieniają rozmiaru — każdy `NoteView`
zajmuje całe okno, a tylko rysuje swój pas), więc nie ma drgań.

Granice jak w dokumencie: „100 %” stanowiska = pierwszy pas wypełnia okno w osi wspólnej (jak `baseZoom` układu
poziomego/pionowego), min 30 % „100 %”, max `ZoomLimits.maxZoom`. Start: wszystkie pasy mieszczą się w oknie, ale nie
mniej niż 30 % i nie więcej niż „100 %”.

## 7. Rysowanie

- `WorkspaceView` ma N dzieci `NoteView`, **każde na całe okno** (te same współrzędne ekranu co kontener → menu,
  pasek zaznaczenia, kadrowanie i podpowiedzi nie potrzebują przesunięć; znika `hostOffsetY` i błąd z 2c-A7).
- Pas rysuje tło, strony i rysowaną kreskę tylko w swoim prostokącie (`laneRect` = granice z §2 w osi wspólnej, całe
  okno w osi stron); kafelki zamawia tylko dla stron w tym prostokącie (jak dziś `band`, ale też dla układu pionowego).
- Nakładki narzędzi (lasso, przeciągane zaznaczenie, linijka) rysuje bez przycięcia do pasa, a aktywny pas rysuje się
  ostatni — przeciągane zaznaczenie widać nad innymi pasami (2c-A7).
- Mini-mapa tylko w aktywnym pasie (inaczej N map w tym samym rogu).
- Nagłówek pasa (tytuł, ✕) w Compose, położenie z modelu (`laneStart(i)`), odświeżane licznikiem ruchu.

## 8. Kafelki i pamięć

Cache kafelków zostaje osobny dla każdego dokumentu (`PageCache` w `NoteView`). Pas poza ekranem nie rysuje i nie
zamawia kafelków; gdy jest poza ekranem dłużej niż 2 s, kontener każe mu zwolnić kafelki (`trimHidden`). Wspólny
budżet stanowiska to 2c-A6 (osobny punkt).

## 9. Narzędzia, aktywny dokument, przenoszenie

Bez zmian w zasadzie z 2b-A7: aktywny = pod ostatnim dotknięciem (`Station.activate`), pióro i narzędzie wspólne,
cofnij/ponów per dokument. Zaznaczenie przeciągnięte lassem poza swój pas: kontener szuka pasa pod środkiem
(`Workspace.locate`) i wkleja tam elementy w tym samym miejscu ekranu (`dropIn` z punktem ekranu — teraz bez
przeliczania przesunięć widoków). Wspólne cofanie przeniesienia to 2c-A5.

## 10. Układ stanowiska

Jeden kierunek dla wszystkich pasów: domyślnie jak pierwszy dokument (pionowy → kolumny, poziomy i „strona po
stronie” → rzędy), zmiana w menu „⋮” → Układ (na stanowisku zmienia kierunek stanowiska, nie zapisuje się w
zeszytach). Po zamknięciu dołożonych dokumentów każdy wraca do swojego układu (`Editor.forceLayout(null)`).

## 11. Zapis stanowisk (zgodny wstecz z 2b-A7)

`Workbench.Station` w JSON: dotychczasowe `n`, `ids`, `s` (przewinięcie wspólne, px ≥ 0), `z` (zoom) — znaczenie bez
zmian; nowe, opcjonalne: `m` (kierunek: 0 pionowy, 1 poziomy; brak → jak pierwszy dokument), `o` (przesunięcia pasów
w osi stron, px; brak → początek), `a` (indeks aktywnego pasa; brak → 0). Stary zapis bez nowych pól wczytuje się
(test ze stałą JSON sprzed zmiany), nowy zapis czytany przez starą wersję traci tylko nowe pola.

## 12. Co znika

`ui/StationCanvas.kt` (pole Compose z rzędami) → `ui/WorkspaceCanvas.kt` (Compose: `AndroidView(WorkspaceView)`,
nagłówki pasów, okna wyboru i zapisanych stanowisk). Z `NoteView`: `hostOffsetY`, `baseZoomOverride`, `startZoom`,
`band`, `onPanY`, `onFlingY`, `onZoomChange`, `followZoom`, `zoomAnchorY`. Z `core`: `Workbench.geometry/clampScroll/
keepAnchor/locate/rowHeight`, `DualRows.follow` (+ ich testy, zastąpione testami `Workspace`). `DualCanvas` już nie ma.

## 13. Testy JVM (`WorkspaceTest`, `FlingTest`, `WorkbenchTest`)

Odstęp pas–pas == strona–strona przy 3 zoomach; mapowanie tam i z powrotem; zoom wokół punktu (punkt stoi < 0,5 px
w obu pasach) i brak oscylacji przy sekwencji szczypnięć; fling na sztucznym zegarze (prędkość → droga, prędkość
maleje monotonicznie do zera, zatrzymanie na granicy); poziomy ruch rusza jeden rząd, pionowy wszystkie, skos oba
naraz; N = 3; układ pionowy lustrzanie; przeniesienie elementu między dokumentami w tym samym miejscu ekranu; stary
zapis stanowiska bez nowych pól.

## 14. Czego nie da się sprawdzić tutaj

Bez tabletu: płynność, „feel” rzutu, wydajność kafelków dwóch PDF-ów, zachowanie przy szybkim szczypaniu na
prawdziwym ekranie.

## Dlaczego kontener z pasami, a nie jeden `NoteView` na N dokumentów

`NoteView` to ~3700 wierszy narzędzi (pióro, gumki, lasso, krzywe, kadrowanie, geometria, tablica), wszystkie
zbudowane wokół jednego dokumentu i jednego `Editor`. Przepisanie ich na N dokumentów to ryzyko regresji w trybie
jednego dokumentu, którego używa się najczęściej. Kontener daje to, czego wymaga autor — jeden stan widoku, jedną
macierz (powiększenie i przesunięcie wspólne wyliczane z jednego modelu w jednej klatce), odstęp jak między stronami,
inercję w obu osiach — a każdy dokument zachowuje swoje narzędzia, cofanie i cache. Pas nie ma własnego stanu widoku:
dostaje gotową macierz z modelu.
