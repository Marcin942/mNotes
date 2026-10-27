# Tryb ciemny i jasny stron (2b-A9, zastępuje i rozszerza F11)

## Co chce Marcin

Pod „⋮” przełącznik **Jasne / Ciemne / Mieszane**. Każda strona pamięta, czy powstała jako jasna czy ciemna. „Ciemne”
przyciemnia jasne strony, „Jasne” rozjaśnia ciemne, „Mieszane” pokazuje każdą tak, jak powstała. To **nie** ma być
proste odwrócenie kolorów: tło i czerń/biel atramentu zamieniają się miejscami, ale kolory nasycone zostają albo
zmieniają tylko jasność, żeby kontrast do nowego tła był co najmniej taki jak wcześniej. Zakreślacz ma zostać
półprzezroczysty i czytelny (żółty na ciemnym tle nie może świecić na biało). PDF i obrazy — odwrócenie jasności z
zachowaniem odcienia; zdjęcia domyślnie bez zmian. Eksport PDF w oryginale (opcja „jak na ekranie”).

## Dlaczego F11 nie wystarczał

F11 nakładał na całą stronę jedną macierz koloru (odwrócenie + obrót barwy o 180°). Macierz jest liniowa, więc:

- biały papier robi się czarny jak smoła (L = 0), a czarne pismo białe jak ekran — za duży kontrast, męczy oczy;
- nasycone kolory obcinają się w kanałach (czerwony 50 % na białym po odwróceniu wychodzi ciemniejszy niż powinien —
  test `ColorInvertTest` to pokazał przy A2-6);
- zakreślacz (mnożenie na jasnym papierze) po odwróceniu staje się jasną plamą — żółty „świeci”;
- przełącznik był na każdą stronę osobno, bez „wszystko ciemne na wieczór”.

## Model

**Natywny wygląd strony** = jasność jej papieru (`PageSpec.paper`): ciemny papier → strona ciemna. To już jest „pamięć,
jak strona powstała” — przy tworzeniu wybiera się papier, a stare strony mają swój papier zapisany. Nowe pole w pliku
nie jest potrzebne. Strona PDF jest natywnie jasna (papier biały pod PDF-em).

**Wyświetlany wygląd** zależy od trybu (ustawienie aplikacji `pages.theme`, przełącznik w „⋮” i w Ustawieniach):

| tryb | strona pokazana jako ciemna, gdy |
|---|---|
| Mieszane (domyślny) | natywnie ciemna XOR flaga F11 „odwróć tę stronę” (`OPT_INVERT`, bez zmian w pliku) |
| Ciemne | zawsze |
| Jasne | nigdy |

Gdy wyświetlany ≠ natywny, strona idzie przez **przekształcenie** (`core/color/ThemeMap.Transform`) z kierunkiem
(do ciemnego / do jasnego) i papierem źródłowym. Ta sama funkcja `ThemeMap.forPage(paper, inverted, mode)` decyduje o
tym w płótnie, miniaturach, mini-mapie i eksporcie.

## Funkcja koloru (OKLab)

Liczona w OKLab (Ottosson), bo tam jasność L jest postrzegana równomiernie, a odcień (a, b) da się zachować przy zmianie L.
Oznaczenia: Lp — jasność papieru źródłowego, Lq — docelowego, d — o ile atrament był ciemniejszy od papieru (dla
przekształcenia do ciemnego; symetrycznie do jasnego).

- **Papier**: do ciemnego L = 0,24, do jasnego L = 0,97; odcień papieru zostaje jako lekki nalot (chroma × 0,25) —
  kremowy papier daje ciepłą ciemną szarość, a nie czerń. Nie czysta czerń i nie czysta biel: mniej olśnienia.
- **Szarości** (chroma < 0,04: czarny, grafit, biały, linie wzoru): jasność odwrócona liniowo tak, by papier szedł w papier,
  a najciemniejszy atrament w L = 0,94 (do ciemnego) albo najjaśniejszy w L = 0,12 (do jasnego). Czarne pismo robi się
  jasnoszare, nie białe jak ekran.
- **Kolory** (chroma ≥ 0,04): odcień i nasycenie zostają, jasność **tylko tyle, ile trzeba**: L' = max(L, Lq + d) do
  ciemnego (min(L, Lq − d) do jasnego). Czerwony, który na białym miał kontrast 0,37, na ciemnym ma go już więcej — zostaje
  dokładnie ten sam czerwony. Granatowy (za ciemny na ciemnym tle) jaśnieje do jasnego błękitu — ten sam odcień.
  Gdy nowy kolor wychodzi poza sRGB, nasycenie spada, aż się zmieści (odcień zostaje).
- **Zakreślacz**: do ciemnego jasność najwyżej 0,6 (żółty robi się nasyconym, ciemniejszym żółtym — po nałożeniu „screen”
  na ciemny papier daje widoczną, ale nie białą plamę); do jasnego co najmniej 0,8 (pastel pod „multiply”). Krycie bez zmian.
- **Tło PDF i obrazy**: jedna macierz koloru (liniowa, więc działa na kafelku bez kosztu na piksel): odwrócenie jasności
  z obrotem barwy (jak F11), ściśnięte tak, by biel szła w kolor nowego papieru, a czerń w L ≈ 0,9 — strona PDF zlewa się
  z resztą zeszytu zamiast mieć smolisto czarne tło. **Zdjęcia** (obrazy wstawione na stronę) domyślnie bez zmian —
  zdjęcie twarzy w negatywie nic nie daje; opcja „Odwracaj też zdjęcia” dla schematów i skanów.

## Gdzie to działa

- **Kafelki**: przekształcenie jest wypalane w kafelku przy rysowaniu (`PageRenderer.theme`), a nie filtrem na klatkę —
  zmiana trybu czyści pamięć kafelków. Kosztuje jedno przeliczenie strony; przewijanie jest potem tak samo szybkie jak zwykle.
- **Kreska w trakcie**, przesuwane lassem obiekty, mini-mapa, miniatury w panelu stron i okładki — ten sam `Transform`.
- **Pipeta**: próbkuje kolor widoczny; na przekształconej stronie odwraca go przekształceniem w drugą stronę, więc
  narysowany nim atrament wygląda jak próbka.
- **Eksport PDF i druk**: oryginał; opcja „Eksport PDF jak na ekranie” w Ustawieniach → Wygląd.

## Czego świadomie nie robię

- Nie zapisuję wyglądu w pliku zeszytu (tryb to ustawienie aplikacji); stare wersje widzą strony jak dawniej.
- Nie analizuję treści zdjęć (rozpoznawanie „zdjęcie czy schemat”) — opcja globalna wystarczy na start.
- Odwrotność funkcji nie jest dokładna (max/min w regule kolorów) — pipeta jest przybliżeniem, wystarczającym do pisania.

## Testy

`ThemeMapTest`: czerń ↔ biel (nie skrajne), szarości monotonicznie odwrócone, czerwony zostaje czerwony i ma kontrast
≥ wyjściowego, granat jaśnieje z tym samym odcieniem, żółty zakreślacz na ciemnym ma L ≤ 0,6, papier kremowy daje ciepłą
ciemną szarość, macierz obrazu prowadzi biel w papier, tryby wybierają przekształcenie zgodnie z tabelą.
