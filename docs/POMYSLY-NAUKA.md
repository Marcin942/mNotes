# Pomysły dydaktyczne (2c-A-ost): szkic do decyzji

Stan: **tylko szkic, bez kodu.** Wdrożenie dopiero po decyzji autora. Wpisy NAU-1 … NAU-5 są w
`docs/features/BACKLOG.md`.
Wspólne zasady: logika w `core/` z testami JVM. Format zeszytu się nie zmienia, wolno dopisać najwyżej pole w meta
(jak `ptag`), które stara wersja pominie. Wszystko działa offline.

## NAU-1 „Powtórka” na taśmach

- **Co widzi uczeń.** „⋮” → „Powtórka…” pokazuje listę stron z taśmami, które są dziś do powtórki. Uczeń odsłania
  taśmę, a pod nią pojawiają się dwa przyciski: „Wiedziałem” i „Nie”.
- **Harmonogram.** Leitner, 5 pudełek z przerwami 1/2/4/8/16 dni. „Wiedziałem” przenosi kartę o pudełko wyżej, „Nie”
  cofa ją do pierwszego.
- **Logika.** `core/study/Leitner.kt`: czysta funkcja `(stan, ocena, dziś) → stan`, plus lista kart na dziś.
- **Zapis.** Pole `lt` w meta zeszytu, wzorowane na `ptag`.
- **Klucz karty.** Id taśmy jest nadawane od nowa przy każdym odczycie strony (`PageCodec.kt:293`), więc nie nadaje
  się na klucz. Kluczem będzie id strony plus prostokąt taśmy zaokrąglony do 1 pt. Przesunięcie taśmy tworzy nową kartę
  (świadomie).
- **Testy.** Przejścia między pudełkami, karty na dziś, kodowanie meta, karta, która znika razem z usuniętą taśmą.
- **Koszt:** średni. **Ryzyko:** małe.

## NAU-2 Wykres z wzoru i „Sprawdź mój szkic”

- **Co widzi uczeń.** Uczeń szkicuje wykres w układzie współrzędnych, zaznacza szkic i wpisuje `y = …`. Aplikacja
  nakłada prawdziwy wykres innym kolorem i podaje różnice: miejsca zerowe, ekstrema i średnią odchyłkę w jednostkach
  układu.
- **Logika.** Własny parser: `+ − · / ^`, nawiasy, `sin cos tan ln sqrt abs`, stałe π i e, bez zależności.
  `core/plot/Expr.kt` i `core/plot/SketchCheck.kt`. Układ współrzędnych pod szkicem znajduje `AxesSnap.frames`.
- **Wynik.** Zwykła kreska (łamana) plus pole tekstu z różnicami. Format bez zmian.
- **Testy.**
  - Parser: kolejność działań, błędy z pozycją.
  - Próbkowanie z przerwami, np. `1/x` przy 0.
  - Odchyłka szkicu przesuniętego o stałą.
- **Koszt:** średni. **Ryzyko:** średnie. Szkic musi mieć jedną wartość y dla każdego x, inaczej porównanie nie ma sensu.

## NAU-3 „Żywa” konstrukcja

- **Co widzi uczeń.** Wynik Geometrii pamięta w sesji, z czego powstał (np. O to środek okręgu przez A, B i C).
  Przeciągnięcie A przelicza O i okrąg.
- **Zapis.** Po zamknięciu zeszytu zostają zwykłe elementy. Format bez zmian, zależności żyją tylko w pamięci.
- **Logika.** `core/geom/Live.kt`: graf zależności (wejścia → operacja → wyniki), przeliczanie w kolejności
  topologicznej, zerwanie zależności przy ręcznej edycji wyniku.
- **Testy.** Przesunięcie A przelicza O. Usunięcie wejścia zrywa zależność. Cykl jest niemożliwy z definicji.
- **Koszt:** duży, bo trzeba dotknąć edytora (przeciąganie punktu z podglądem). **Ryzyko:** średnie.

## NAU-4 Odtwarzanie strony krok po kroku

- **Co widzi uczeń.** Suwak po kolejności powstawania elementów i tryb „zatrzymaj i dokończ sam”. W tym trybie strona
  od wybranego kroku jest ukryta, a uczeń pisze dalej na warstwie, którą potem porównuje z oryginałem.
- **Ograniczenie.** Kreski nie mają czasu powstania. Kolejność bierze się z listy elementów, czyli z kolejności
  rysowania (zakreślacz pod pismem, „na spód”). To przybliżenie i trzeba to uczniowi powiedzieć wprost.
- **Logika.** `core/replay/Steps.kt` grupuje kreski w kroki (przerwa w położeniu albo zmiana narzędzia). Testy na
  przykładowej stronie.
- **Koszt:** mały do średniego. **Ryzyko:** małe.

## NAU-5 Strona Cornella

- **Co widzi uczeń.** Styl strony z kolumną pytań po lewej i polem podsumowania na dole. Pytania z kolumny trafiają
  do „Powtórki” (NAU-1) jako karty.
- **Bez zmiany formatu.** Nie powstaje nowy `Template`, bo stara wersja go nie zna. Kolumna i pole to zablokowane
  linie i podpisy na zwykłej stronie, jak w przewodniku (`core/guide`). Pytanie to pole tekstu w kolumnie, a odpowiedź
  to taśma obok.
- **Testy.** Układ mieści się na A4 i A5. Pytania z kolumny są rozpoznawane po położeniu.
- **Koszt:** mały, przy założeniu, że NAU-1 już jest.

## Kolejność, jeśli wszystko

1. NAU-1, bo na nim opiera się NAU-5.
2. NAU-5.
3. NAU-2.
4. NAU-4.
5. NAU-3, najdroższy.
