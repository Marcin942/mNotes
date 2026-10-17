---
name: samsung-notes-do-mnotes
description: Zamienia notatki z Samsung Notes (plik .sdocx albo "PDF zgodny z Notatkami") na zeszyt mNotes (.mnotes) z natywnymi, edytowalnymi kreskami (pióro, pędzel, zakreślacz) i takim samym tłem (kolor papieru, kratka). Używaj, gdy użytkownik wrzuca plik .sdocx lub PDF wyeksportowany z Samsung Notes i prosi o przeniesienie notatek do mNotes, o plik .mnotes albo o edytowalną wersję pisma odręcznego. Zwykły PDF z Samsung Notes (sam raster) tym skillem nie da się edytować.
---

# Samsung Notes do zeszytu mNotes

Wynik to plik `.mnotes` (jedna notatka = jeden zeszyt) i raport odstępstw. Zasada nadrzędna właściciela: **notatka ma wyglądać jak najwierniej oryginałowi i być maksymalnie edytowalna**. Dlatego pismo trafia do mNotes jako kreski (nie obraz), a gdy mNotes nie ma dokładnego odpowiednika, skrypt wybiera najbliższy i zapisuje to w raporcie.

## Co się przenosi (etap pierwszy)

- **Pociągnięcia**: pióro (InkPen2) → pióro wieczne, pędzel (BrushPen) → pędzel, zakreślacz (Marker) → zakreślacz. Kolor, rozmiar, kształt linii i zmienna grubość są odtwarzane z danych pliku.
- **Tło strony**: kolor papieru i wzór (kratka z właściwym rozstawem; linie i kropki przybliżone).
- Układ stron i ich kolejność.

**Jeszcze nie przenosi się** (trafia do raportu jako „Pominięto”): pola tekstowe, obrazy, kształty, tabele, wzory, nagrania głosu, tło z PDF-a, własny obraz tła, warstwy jako osobne warstwy. Nie obiecuj użytkownikowi niczego z tej listy.

## Które pliki się nadają

| Plik od użytkownika | Co z nim zrobić |
|---|---|
| `.sdocx` (natywny plik Samsung Notes) | Najlepszy: pełne dane wektorowe. |
| PDF "zgodny z Notatkami" (Creator „Samsung Electronics”) | Też pełne dane: PDF niesie osadzony `.sdocx`. Skrypt sam go wyjmuje. |
| Standardowy PDF z Samsung Notes | Sam raster, kresek w nim nie ma. Skrypt odmawia z komunikatem. Poproś o `.sdocx` albo PDF zgodny z Notatkami (w Samsung Notes: Udostępnij → Zapisz jako plik → Plik Samsung Notes / PDF z możliwością edycji w Notatkach). |

Gdy użytkownik wysłał trzy warianty tej samej notatki, użyj `.sdocx`, a PDF zgodny z Notatkami zostaw jako zapas.

## Zasady, których nie wolno łamać

- Zawsze uruchamiaj skrypty. Nie dekoduj formatu "na oko" i nie pisz własnego kodu do `.mnotes`.
- Nie edytuj plików skilla. Jedno wejście = jeden zeszyt; wiele plików = osobne zeszyty.
- **Nie dopowiadaj tego, czego raport nie potwierdza.** Wszystko, co skrypt pominął albo przybliżył, wypisz użytkownikowi.
- Nie dodawaj `--dark`, jeśli użytkownik nie powiedział, że notatka w aplikacji Samsunga ma ciemne tło. Ciemny tryb nie jest wykrywany automatycznie, a `--dark` to heurystyka (patrz niżej).
- Gotowy plik trafia do `/mnt/user-data/outputs/` (jeśli taki katalog istnieje), inaczej do bieżącego katalogu.

## Krok 0. Znajdź skrypty i pliki wejściowe

```bash
SK=$(dirname "$(find / -path '*samsung-notes-do-mnotes/scripts/convert.py' 2>/dev/null | head -1)")
echo "$SK"
ls /mnt/user-data/uploads 2>/dev/null
```

`$SK` to folder `scripts`. Gdy jest puste, napisz użytkownikowi, że skill nie jest poprawnie zainstalowany, i zakończ. Konwersja wymaga tylko Pythona 3 (biblioteka standardowa). Podgląd (krok 2) wymaga jeszcze Pillow i numpy: `pip install pillow numpy`.

## Krok 1. Konwersja

```bash
python3 "$SK/convert.py" "/mnt/user-data/uploads/NOTATKA.sdocx" -o /mnt/user-data/outputs/NOTATKA.mnotes
```

Skrypt wypisuje raport i zapisuje go obok wyniku (`NOTATKA.raport.txt`). Opcje (tylko na życzenie):

| Opcja | Znaczenie |
|---|---|
| `--title "Tytuł"` | tytuł zeszytu (domyślnie z nazwy pliku, bez daty i numerka) |
| `--dark` | ciemny papier mNotes i odwrócona jasność pisma (patrz „Tło ciemne”) |
| `--paper RRGGBB` | wymuś kolor papieru |

## Krok 2. Sprawdź wynik (obowiązkowo)

1. Przeczytaj raport. Każdą linię „Pominięto” i „Wzór tła … nie był sprawdzony” zanotuj do odpowiedzi.
2. Wyrenderuj podgląd i **obejrzyj każdą stronę** (narzędziem do oglądania obrazów):

   ```bash
   python3 "$SK/preview.py" /mnt/user-data/outputs/NOTATKA.mnotes /tmp/sn_podglad
   ```

   Sprawdź: tło (kolor, kratka), czy pismo jest na swoim miejscu, czy kolory się zgadzają, czy zakreślacz nie zakrywa tekstu. Podgląd jest przybliżeniem (bez wygładzania i zbiegania końców), nie rendererem aplikacji, więc nie oceniaj po nim drobnych różnic grubości.
3. Gdy liczba stron albo kresek wygląda źle (np. pusta strona, a w oryginale jest pismo), nie oddawaj pliku: napisz, co zobaczyłeś.

## Krok 3. Odpowiedź dla użytkownika

Podaj plik (załącznik z `/mnt/user-data/outputs/`) i krótko: ile stron, ile kresek. Potem wypisz **odstępstwa z raportu** zwykłym językiem (np. „na 3 stronach są pola tekstowe, które nie zostały przeniesione”). Powiedz, jak otworzyć: w mNotes „Importuj” → plik `.mnotes`. Nie wklejaj całego raportu, tylko to, co ma znaczenie dla wyglądu notatki.

## Jak odwzorowane są pióra (kalibracja)

Wartości zmierzono na dwóch próbkach (jasna i ciemna notatka) przez porównanie z rastrem PDF zgodnego z Notatkami (nakładanie masek kresek, IoU 0,84-0,87 dla stron z piórem, ok. 0,65 dla stron z samymi pędzlami). To przybliżenie, nie dokładny model.

- **Jednostki**: strona 1600×2262 jednostek Samsunga = 600×848 pt, czyli 1 jednostka = 0,375 pt.
- **InkPen2 → pióro wieczne.** Grubość **zależy od prędkości pisania, nie od nacisku**: wolno (do 0,6 jedn./ms) pełny rozmiar, ok. 1,1 → 0,88, ok. 1,75 → 0,66, ok. 2,5 → 0,60, szybko (od 6,5) → 0,55 rozmiaru (tabela `INK_SPEED_TABLE`, interpolacja liniowa). Szerokość maksymalna = rozmiar × 0,375 pt.
- **BrushPen → pędzel.** Szerokość/rozmiar ≈ 0,09 + 1,17 × nacisk; w mNotes szerokość maksymalna = rozmiar × 0,375 × 1,2, a zapisany współczynnik grubości (0,04-1) wynika z tej krzywej.
- **Marker → zakreślacz.** Stała szerokość = rozmiar × 0,375 pt. Zakreślacz mNotes miesza się z papierem mnożeniem z krytością 0,5, więc kolor jest przeliczany tak, by wynik na papierze miał ten sam odcień co w Notatkach (z uwzględnieniem koloru papieru).
- **Nieznane pióro** → długopis o stałej szerokości (raport to zaznacza).
- Próbki kreski są rzedzone (odstęp ≥ 0,3 pt; przy pędzlu i piórze też przy zmianie grubości), bo mNotes i tak nie rozróżnia gęstszych.

## Tło

- Papier: kolor z nagłówka strony (np. `E6E6E6` w jasnej próbce, `FCFCFC` w ciemnej).
- Wzór tła Samsunga numer 5 (średnia kratka) jest **sprawdzony**: rozstaw 38,25 pt, linia ok. `C8C8C8`, grubość 1 pt. Siatka mNotes jest wyśrodkowana na stronie, a w Notatkach zaczyna się przy lewym górnym rogu, więc skrypt **powiększa stronę** (w próbce do ok. 612×875 pt), żeby linie wypadły w tych samych miejscach; kreski zostają na swoich współrzędnych od lewego górnego rogu.
- Wzory 1-4 i 6-9 (linie, wąska/szeroka kratka, kropki) są ustawione według tego samego rozstawu przez współczynnik, ale **nie były sprawdzone na próbce**: raport ostrzega, a w odpowiedzi trzeba o tym napisać.
- Własny obraz tła, tło z PDF-a i wzory 10-16 nie są przenoszone: strona ma gładki papier, raport to mówi.

### Tło ciemne

W pliku nie ma wprost znacznika „ciemna notatka” (w próbce papier ma kolor `FCFCFC`, a ciemność zapisuje tylko aplikacja Samsunga). `--dark` ustawia ciemny papier mNotes (`1E1F22`) i odwraca jasność kolorów pisma z zachowaniem barwy (czarne → białe). To heurystyka i może się różnić od wyglądu w aplikacji Samsunga; mNotes sam nie odwraca kolorów pisma na ciemnym papierze, dlatego robi to skrypt. Powiedz o tym użytkownikowi, gdy używasz `--dark`.

## Gdy coś pójdzie źle

- „Ten PDF nie zawiera notatki Samsung Notes” → to standardowy PDF z samym rastrem; poproś o `.sdocx` albo PDF zgodny z Notatkami.
- „Kreska nie do odczytania” w raporcie → pojedyncza kreska pominięta (zepsuta albo nieznana odmiana formatu); reszta jest przeniesiona. Podaj użytkownikowi liczbę.
- Wyjątek Pythona przy odczycie strony → prawdopodobnie nowsza wersja formatu. Nie zgaduj: opisz błąd użytkownikowi i podaj, co się udało (np. samo `python3 "$SK/sdocx.py" PLIK.sdocx` pokazuje nagłówki stron).

## Samotest (opcjonalnie)

`python3 "$SK/selftest.py"` buduje syntetyczny `.sdocx` (i PDF zgodny), przechodzi całą konwersję i sprawdza wynik; kończy się `SAMOTEST: OK`.
