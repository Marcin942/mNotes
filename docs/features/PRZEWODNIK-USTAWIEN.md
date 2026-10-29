# Przewodnik po ustawieniach (3c-A10)

## Cel

mNotes ma bardzo dużo funkcji, a niewytłumaczony nadmiar odstrasza. Przewodnik jest „okładką” aplikacji: przy pierwszym
uruchomieniu w kilka minut ustawia najważniejsze rzeczy **pod tego, kto pisze**, a przy okazji pokazuje, że funkcje
istnieją. Nie jest samouczkiem ani reklamą — każda strona to jedna decyzja, którą od razu widać na żywej próbce.

Zasady:

- **Stopniowe odsłanianie.** Pierwsze uruchomienie: krótka ścieżka 7 stron. Reszta jako „Poznaj więcej” — tematy do
  przejścia kiedykolwiek, z menu. Nic nie wyskakuje drugi raz.
- **Jedna strona = jedna decyzja.** Krótki tytuł, 1–2 zdania prostym językiem, żywa próbka, 2–4 opcje jako duże karty,
  opcja zalecana wyraźnie oznaczona („Zalecane”), Wstecz / Pomiń / Dalej, postęp w kropkach.
- **Wybór = zwykłe ustawienie.** Zapisuje się od razu i działa od razu (motyw zmienia cały ekran przewodnika). W
  Ustawieniach jest dokładnie ten sam przełącznik. „Pomiń” zostawia ustawienie, jakie było.
- **Można przerwać.** Zamknięcie zapamiętuje miejsce; „Przewodnik” w menu wraca na tę samą stronę.
- **Stare instalacje** nie dostają przewodnika na siłę: w bibliotece pojawia się jedna propozycja („Nowy przewodnik po
  ustawieniach — Zacznij / Nie teraz”), znika po wyborze.

## Ścieżki

### Start (pierwsze uruchomienie, 7 stron)

| # | Strona | Decyzja (ustawienie) | Opcje (zalecana **pogrubiona**) | Żywa próbka |
|---|---|---|---|---|
| 1 | Witaj | Do czego głównie? (`setup.use`) | Notatki, Matematyka i nauki ścisłe, Czytanie PDF, Rysowanie — bez zalecanej (to pytanie o Ciebie, nie o ustawienie) | Kartka z pismem w barwach wybranego zastosowania |
| 2 | Ręka | Piszę prawą / lewą ręką (`input.hand`, 3c-A13) | **Prawą**, Lewą | Makieta ekranu: listwa pióra i dłoń przechodzą na wybraną stronę |
| 3 | Rysik i palec | Co robi palec (`input.finger`) | **Przewija i powiększa**, Rysuje, Nic | Pole do pisania: rysik pisze, palec robi to, co wybrano |
| 4 | Strona | Wzór nowych stron (`new.template`) | Gładka, Linie, Kratka, Kropki — zalecana zależy od strony 1 (Matematyka → Kratka, Rysowanie → Gładka, reszta → Linie) | Kartka z wybranym wzorem, można po niej pisać |
| 5 | Jasny / ciemny | Wygląd (`ui.theme` + `pages.theme`) | **Jak w systemie**, Jasny, Ciemny (także strony) | Cały przewodnik zmienia motyw; makieta pokazuje kartkę jasną lub ciemną |
| 6 | Pasek narzędzi | Położenie (`toolbar.position`) | **Góra**, Dół, Z boku po stronie wolnej ręki | Makieta ekranu z paskiem w wybranym miejscu |
| 7 | Gotowe | — | „Zacznij pisać”, tematy „Poznaj więcej” | Karta „Twoje wybory” (ręka, palec, kartka, motyw, pasek) |

### Poznaj więcej (z menu: Biblioteka → menu → Przewodnik, Ustawienia → „Przewodnik po ustawieniach”)

| Temat | Strony (ustawienie) |
|---|---|
| Pisanie i kształty | Prostowanie po przytrzymaniu (`input.hold_to_shape`); Wygładzanie pisma (`pen.smoothing`: mało / średnio / dużo) |
| Gumka i zaznaczanie | Domyślna gumka (`eraser.mode`: obiektowa / pikselowa / lasso gumki); Co zaznacza lasso (`lasso.touch`) |
| Gesty | Podwójne dotknięcie palcem (`input.double_tap`); Cofnij dwoma palcami (`input.two_finger_undo`); Przycisk rysika (`input.pen_button`) |
| Matematyka | Symbole z \alpha, x^2 w tekście (`text.math_unicode`); Podpisy punktów w konstrukcjach (`construct.names`) |
| Widok | Zapas przewijania (`view.scroll_margin`); Mini-mapa (`view.minimap`); Rozmiar paska (`toolbar.size`) |
| Podpowiedzi | Dymki o narzędziach (`ui.tool_hints`); Nazwy przycisków (`ui.tooltips`); Ustawienia z paska — przytrzymany przycisk paska → „Ustawienia tej funkcji” (`ui.hold_settings`, 3c-A17b); Wibracja (`ui.haptics`) |

Każdy temat kończy się powrotem do listy tematów; przejrzany temat ma znaczek ✓. Tematy są kartami nawigacji (strzałka ›),
nie opcjami. Kolejność zależy od strony 1: Matematyka → najpierw „Matematyka”, PDF → „Gumka i zaznaczanie”, Rysowanie →
„Pisanie i kształty”.

## Teksty stron (PL; EN w kodzie obok, jak w całej aplikacji przez `T.t`)

1. **Witaj w mNotes** — „Kilka krótkich pytań, po jednym na stronę. Wszystko zmienisz później w Ustawieniach.” Pytanie:
   „Do czego głównie będziesz używać mNotes?”
2. **Którą ręką piszesz?** — „Przyciski przy brzegu kartki ustawimy po stronie, której nie zasłania dłoń.”
3. **Rysik i palec** — „Rysik zawsze pisze. Wybierz, co ma robić palec — spróbuj na kartce w podglądzie.”
4. **Jaka kartka?** — „Wzór nowych stron. Każdą stronę możesz potem zmienić osobno.”
5. **Jasno czy ciemno?** — „W ciemnym trybie kartki też mogą być ciemne, a kolory pisma zostają czytelne. Zmiana działa od razu.”
6. **Gdzie pasek narzędzi?** — „Na dużym ekranie wygodnie u góry; z boku zostawia więcej wysokości na kartkę.”
7. **Gotowe** — „Możesz zacząć pisać. Resztę funkcji pokażemy, kiedy zechcesz — wybierz temat poniżej albo wróć tu z menu.”

Tematy „Poznaj więcej” mają teksty w `core/guide/SetupGuide.kt` (jedno źródło dla aplikacji, testów i podglądów).

## Wygląd

- Spójny z aplikacją (kolory `mint`, karty jak w Ustawieniach), oba motywy, akcent użytkownika.
- Szeroki ekran (szerszy niż wyższy i ≥ 560 dp): tekst i opcje z lewej (do 480 dp), próbka z prawej. Wąski (telefon, tablet pionowo):
  tytuł, opis, próbka, opcje jedna pod drugą, przyciski przypięte na dole.
- Karty opcji min. 56 dp wysokości, przyciski min. 48 dp; przejścia między stronami 200 ms (zanik + lekki przesuw).
- Bez żargonu: „palec przewija”, nie „tryb wejścia dotykowego”.

## Próbki

Makiety (ręka, pasek, motyw, kartka) są w `core/guide/SetupScenes.kt` jako lista prostych kształtów z rolami kolorów —
ta sama scena rysuje się w Compose (aplikacja) i w AWT (`tools/PreviewHarness`, podglądy `docs/podglad/przewodnik-ustawienia-*.png`;
pełny zestaw: `MNOTES_PREVIEW_DIR=… --tests '*PreviewHarness*'`, wybór do docs: dodatkowo `MNOTES_PREVIEW_ONLY=setup-docs`),
więc podgląd pokazuje dokładnie to, co aplikacja. Pole do pisania na stronie 3 jest prawdziwe: rysik pisze, palec
zachowuje się według wyboru (przewija kartkę, rysuje albo nic). Pozostałe próbki pokazują bieżący wybór i zmieniają się
razem z nim.

## Logika (testy JVM: `SetupGuideTest`)

- Kolejność: start = 7 stron, ostatnia „Gotowe”; tematy w ustalonej kolejności.
- Wybór opcji zapisuje jej ustawienia; bieżąca opcja = ta, której wszystkie ustawienia mają obecne wartości.
- „Pomiń” nie zmienia ustawienia; „Pomiń przewodnik” kończy ścieżkę startu (nie wyskoczy znowu).
- „Wstecz” nie schodzi poniżej pierwszej strony; stan (ścieżka, strona, przejrzane tematy) zapisuje się i wczytuje.
- Pierwsze uruchomienie: przewodnik sam tylko w nowej instalacji i tylko raz; stara instalacja → propozycja w bibliotece.
- Zalecana opcja wzoru zależy od zastosowania ze strony 1; strona 1 nie ma zalecanej.
- Gdy obecne ustawienie nie pasuje do żadnej karty (zmienione w Ustawieniach), strona mówi o tym pod kartami.
- Każde ustawienie użyte w przewodniku ma powiązanie z `Settings` (test na źródle).
