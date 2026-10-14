---
name: zadania-do-mnotes
description: Zamienia PDF-y z listami zadań (np. od wykładowcy) na zeszyt mNotes (.mnotes) albo PDF. Na początku oryginalne strony w A5, potem strony A5 w kratkę, na każdej obrazek z treścią jednego zadania albo podpunktu. Odpowiedź zawiera też spis treści zadań w bloczku kodu Markdown (do wklejenia w czat). Używaj, gdy użytkownik wrzuca PDF z zadaniami i prosi o plik .mnotes, notatki do rozwiązywania zadań, zeszyt z zadaniami albo o pocięcie zadań na strony.
---

# Zadania z PDF do zeszytu mNotes

Wynik to plik `.mnotes` (domyślnie) albo `.pdf`. Układ:

1. **Oryginalne strony** z wejściowego PDF, przeskalowane do A5 (A4 → A5 bez utraty grafiki wektorowej).
2. **Strony zadań**: papier A5 pionowo, biały, w kratkę. Na górze obrazki z treścią:
   - zadanie bez podpunktów: **1 obrazek** (cała treść zadania),
   - zadanie z podpunktami: **osobna strona na każdy podpunkt**, na niej **2 obrazki**: treść zadania przed podpunktami (to samo na każdej stronie tego zadania) oraz treść podpunktu. Obrazki są osobnymi elementami, więc w mNotes można każdy usunąć albo przestawić.
   Obrazki mają pełną szerokość kartki (420 pt), są przyklejone do góry; reszta strony to kratka na rozwiązanie.

Oprócz pliku w odpowiedzi jest **spis treści zadań w bloczku kodu Markdown** (jeden PDF = jeden bloczek), który użytkownik kopiuje do czata, żeby od razu zadawać pytania (krok 6).

Nie edytuj żadnych plików skilla. Wszystko robią skrypty z folderu `scripts/` obok tego pliku.

## Zasady, których nie wolno łamać

- Zawsze uruchamiaj skrypty (nie wycinaj zadań „na oko”, nie pisz własnego kodu do `.mnotes`).
- Nie wymyślaj treści zadań. Obrazki to wycinki z oryginalnego PDF.
- Jedno wejściowe PDF = jeden zeszyt. Wiele PDF-ów = wiele osobnych zeszytów (powtórz kroki 2–6 dla każdego).
- Spis zadań (krok 6) przepisuj **z obrazów**, nie z warstwy tekstowej PDF: ta bywa zniekształcona (wzory, polskie litery). Nie rozwiązuj zadań i nie dopisuj nic od siebie.
- Przed oddaniem pliku **zawsze** obejrzyj podglądy (krok 3) i porównaj z listą zadań. Plik oddajesz dopiero, gdy się zgadza.
- Nie zmieniaj nazw katalogów wyników. Gotowy plik trafia do `/mnt/user-data/outputs/` (jeśli taki katalog istnieje), inaczej do bieżącego katalogu.

## Krok 0. Znajdź skrypty i plik wejściowy

```bash
SK=$(dirname "$(find / -path '*zadania-do-mnotes/scripts/analyze.py' 2>/dev/null | head -1)")
echo "$SK"
ls /mnt/user-data/uploads 2>/dev/null
```

`$SK` to folder `scripts`. Plik PDF użytkownika leży zwykle w `/mnt/user-data/uploads/`. Gdy `$SK` jest puste, napisz użytkownikowi, że skill nie jest poprawnie zainstalowany, i zakończ.

## Krok 1. Sprawdź środowisko

```bash
python3 "$SK/check_env.py"
```

- `WYNIK: gotowe` → idź dalej.
- Brak Pillow: `pip install pillow`. Brak renderowania lub tekstu z PDF: `pip install pymupdf` (albo `apt-get install -y poppler-utils`).
- „skalowanie PDF: brak” oznacza, że oryginalne strony **nie zostaną zmniejszone do A5** (mNotes pokazuje tło PDF w skali 1:1, więc A4 byłby ucięty), a wynik `.pdf` w ogóle nie powstanie. Zawsze wykonaj wtedy `pip install pypdf` i sprawdź `check_env.py` jeszcze raz. Dopiero gdy się nie da, zbuduj mimo to i **powiedz użytkownikowi wprost**, że oryginały są w dużym rozmiarze.

## Krok 2. Analiza

```bash
python3 "$SK/analyze.py" "/mnt/user-data/uploads/NAZWA.pdf" --out /tmp/zm_NAZWA
```

Skrypt wypisuje listę zadań i podpunktów z zakresami (strona, y w pt) oraz ostrzeżenia, zapisuje `plan.json` i podglądy `podglad-strN.png` (kolorowe ramki: niebieska = treść zadania, pozostałe = podpunkty, czerwona linijka co 50 pt).

Opcje (tylko gdy coś jest nie tak, nie dawaj ich domyślnie):

| Opcja | Kiedy |
|---|---|
| `--title "Tekst"` | inny tytuł zeszytu niż nazwa pliku |
| `--no-subs` | podpunkty mają być zignorowane (każde zadanie = jedna strona) |
| `--task-regex "REGEX"` | numeracja zadań nietypowa i skrypt jej nie wykrył |
| `--sub-regex "REGEX"` | jw. dla podpunktów |

## Krok 3. Sprawdź analizę (obowiązkowo)

1. Przeczytaj wydruk. Czy liczba i numery zadań zgadzają się z PDF? Czy podpunkty są w zadaniach, w których powinny być? Czy nie ma „(pusty!)” ani ostrzeżeń?
2. **Obejrzyj każdy `podglad-strN.png`** (narzędziem do oglądania obrazów). Sprawdź: ramka każdego zadania obejmuje całą jego treść (wzory, rysunki, ostatni wiersz), nie łapie nagłówka ani stopki, nie ucina wzoru na granicy.
3. Gdy wszystko się zgadza → krok 4. Gdy nie → popraw `plan.json` zgodnie z `references/plan-reczny.md` (przesuń granicę, scal, usuń fałszywe zadanie, dodaj brakujące), sprawdź poprawiony plan poleceniem `python3 "$SK/preview.py" plan.json --out /tmp/zm_podglad`, obejrzyj nowe podglądy PNG i dopiero wtedy przejdź do kroku 4.

Typowe przypadki i co z nimi zrobić:

- Skan lub PDF bez tekstu (analyze mówi „brak tekstu”) → plan ręczny: `references/plan-reczny.md`.
- Dwie kolumny na stronie → plan ręczny (skrypt zakłada jedną kolumnę).
- Zadania bez numerów, tylko tytuły → `--task-regex`.
- Podpunkty zagnieżdżone (a)(i) → traktuj podpunkt najwyższego poziomu jako podpunkt, resztę zostaw w jego treści (nie rozbijaj).

## Krok 4. Zbuduj plik

Domyślnie `.mnotes`:

```bash
python3 "$SK/build.py" /tmp/zm_NAZWA/plan.json --format mnotes
```

Gdy użytkownik chce PDF: `--format pdf`. Oba naraz: `--format both`.

Przydatne opcje (tylko na życzenie): `--name "Tytuł"`, `--out-dir KATALOG`, `--no-originals` (bez oryginalnych stron), `--top-inset PT` (odstęp obrazków od góry), `--gap PT` (odstęp między obrazkami), `--dpi 300`.

Skrypt sam sprawdza plik (odczytuje strony i zasoby). Na końcu wypisuje listę stron. Jeśli widzisz „OSTRZEŻENIE: … wystają”, treść jednego zadania jest wyższa niż strona A5: powiedz o tym użytkownikowi (nie zmieniaj skali sam).

## Krok 5. Kontrola wyniku

Dla `.pdf`: wyrenderuj 3 strony (pierwszą, pierwszą stronę zadań, ostatnią) i obejrzyj:

```bash
pdftoppm -r 60 -f 1 -l 3 -png "/mnt/user-data/outputs/NAZWA.pdf" /tmp/zm_kontrola
```

Dla `.mnotes` kontrola jest wbudowana w `build.py` (OK + lista stron); dodatkowo możesz wyrenderować wycinki z planu (krok 3).

## Krok 6. Spisz zadania do bloczku kodu

Dla każdego wejściowego PDF-a (po zbudowaniu zeszytu) zapisz obrazki zadań i przepisz z nich treść:

```bash
python3 "$SK/wycinki.py" /tmp/zm_NAZWA/plan.json --out /tmp/zm_NAZWA/wycinki
```

Skrypt zapisuje po jednym PNG na zadanie (`01-Zadanie-1.png`, …; cała treść: wstęp i wszystkie podpunkty). **Obejrzyj każdy obraz po kolei** (narzędziem do oglądania obrazów) i spisz jego treść według reguł:

- Jeden PDF = **jeden bloczek kodu** ` ```markdown ` … ` ``` `. Gdy wejściowych PDF-ów jest kilka, każdy ma osobny bloczek, a nad nim linia z nazwą pliku (np. `**domowe1.pdf**`). Nie łącz plików w jeden bloczek.
- Jeśli treść zawiera potrójny znak backtick ` ``` `, otocz bloczek czterema (` ```` `).
- Na początku bloczku: `# <tytuł zeszytu>`. Potem dla każdego zadania nagłówek `## <etykieta zadania z PDF>` (np. `## Zadanie 1.3`), pod nim treść wstępu.
- Podpunkty jako lista: `- (a) treść`, `- (b) treść` (etykiety tak jak w PDF, bez dublowania etykiety w treści).
- Wzory w LaTeX: `$…$` w tekście, `$$…$$` dla wzorów w osobnej linii. Znaki specjalne piszesz poprawnie: `\nabla`, `\oint`, `\bar{n}`, `\infty`, `\varepsilon`.
- Polskie litery zawsze poprawne (ą ć ę ł ń ó ś ź ż), nawet gdy tekst z PDF ma tam inne znaki.
- Wierność oryginałowi: te same słowa, ten sam porządek, te same gwiazdki (`*`) i oznaczenia trudności. Nie skracaj, nie parafrazuj, nie rozwiązuj, nie dopisuj wskazówek. Rysunki i wykresy opisz jednym zdaniem w nawiasie kwadratowym: `[rysunek: …]`.
- Gdy czegoś nie umiesz odczytać: `[nieczytelne]`. Nie zgaduj.
- Jeśli plan ma 0 zadań (skan bez planu ręcznego) i nie da się spisać, napisz to wprost zamiast bloczku.

Ten krok wykonaj nawet przy wyniku `.pdf`.

## Krok 7. Odpowiedź dla użytkownika

Podaj plik (link/załącznik z `/mnt/user-data/outputs/`) i krótko: ile stron oryginału, ile stron zadań, które zadania mają podpunkty. Wypisz każde ostrzeżenie i każdą ręczną poprawkę planu. Powiedz, jak otworzyć: w mNotes „Importuj” → wybrać plik `.mnotes`.

Potem **w samej odpowiedzi** (nie tylko w pliku) wklej bloczki z kroku 6, żeby dało się je skopiować. Dodaj jedno zdanie, że spis jest przepisany z obrazów i warto rzucić okiem na wzory, zanim trafi do czata.

## Gdy coś pójdzie źle

Zobacz `references/rozwiazywanie-problemow.md`. Po błędzie nie zgaduj: przeczytaj komunikat, napraw przyczynę i uruchom to samo polecenie. Jeśli po trzech próbach nadal nie działa, opisz użytkownikowi, co próbowałeś, i daj to, co się udało (np. samą analizę z podglądami).

## Samotest (opcjonalnie)

`python3 "$SK/selftest.py"` buduje syntetyczny PDF i przechodzi cały proces; kończy się `SAMOTEST: OK`.
