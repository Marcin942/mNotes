# Wstawianie tekstu: przepływ (3c-A12)

Uzupełnia EDT-040/041/049 z `02-edytor-narzedzia.md`. Opisuje, jak to działa teraz.

## Przepływ

1. **Narzędzie tekstu, stuknięcie w puste miejsce:** od razu otwiera się okno z kursorem w polu i klawiaturą.
   Lewy-górny róg nowego pola trafia w miejsce stuknięcia.
2. **Narzędzie tekstu, stuknięcie w istniejący tekst:** to samo okno, w trybie edycji tego tekstu.
   Ręka i lasso najpierw zaznaczają, bo przeciąganie przesuwa pole. Drugie stuknięcie w zaznaczony tekst go edytuje.
3. **Listwa okna (zawsze widoczna):** pogrubienie, kursywa, do 4 kolorów i rozmiar „A− 14 A+”.
   - Pierwszy kolor jest bieżący, pozostałe to ostatnio używane z zestawów pióra.
   - Rozmiar skacze po typowych wartościach: 8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 40, 48, 64, 80, 96 pt.
4. **„Więcej” (strzałka na końcu listwy):** podkreślenie, przekreślenie, listy, wyrównanie, czcionki z próbką, suwak
   rozmiaru, pełny wybór koloru, dopasowanie rozmiaru i tło pola. Domyślnie zwinięte.
5. **Zamknięcie okna:**
   - „Gotowe” zapisuje. Pusty tekst istniejącego pola je usuwa.
   - „Anuluj” odrzuca zmiany.
   - Stuknięcie obok okna albo „wstecz”:
     - nowy tekst zapisuje się, gdy coś wpisano;
     - istniejący zapisuje się, gdy zmieniono treść albo wygląd;
     - wyczyszczone pole nie jest usuwane, bo przypadkowe stuknięcie obok niczego nie kasuje.
6. **Pamięć:** czcionka, rozmiar i kolor ostatnio zapisanego tekstu stają się domyślne dla następnego
   (`Settings.textFamily/textSize/textColor`).
7. **Zaznaczone pole:** ma cztery uchwyty boków w kształcie podłużnych pigułek wzdłuż krawędzi.
   - Lewy i prawy zmieniają szerokość, a tekst przelewa się na nowo.
   - Górny i dolny zmieniają wysokość.
   - Przeciągnięcie środka przesuwa pole.

## Logika i testy

- `core/text/QuickText.kt`: zawiera trzy funkcje.
  - `step`: kroki rozmiaru.
  - `swatches`: kolory na listwie.
  - `keepOnDismiss`: decyzja przy stuknięciu obok okna.
- `QuickTextTest`: testy JVM tych trzech funkcji.
- `MDialog(onOutside = …)`: osobna reakcja na stuknięcie obok okna. Pozostałe okna zachowują się jak dotąd (domyślnie to samo co „Anuluj”).
