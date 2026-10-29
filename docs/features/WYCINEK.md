# Wycinek (3c-B4)

Narzędzie do przenoszenia fragmentów stron do zeszytu jako obrazów. Najczęstszy przypadek to slajdy wykładu otwarte obok
własnych notatek (stanowisko): diagram ze slajdu ma trafić do notatek w jednym albo dwóch ruchach.

## Jak się używa

1. Wybierz **Wycinek** na pasku. Ikona to nożyczki w przerywanej ramce; w Ustawieniach → Układ paska nazywa się
   „Wycinek”.
2. Zaznacz obszar na dowolnej stronie, prostokątem albo lassem. Zaznaczać można też w dokumencie otwartym do odczytu.
   Wycinek pojawia się na miejscu wycięcia, w ramce w kolorze akcentu, a obok niego pasek.
3. Wycinek wstawia się na jeden z trzech sposobów:
   - **Przeciągnij** wycinek na stronę drugiego dokumentu albo na inną stronę tego samego. W locie wycinek maleje do
     około 220 dp, żeby było widać, gdzie go kładziesz. Przy krawędzi pasa drugi dokument sam się przewija.
   - **„Wstaw do: <dokument>”** kładzie wycinek na widocznym fragmencie strony celu. Domyślnym celem jest dokument, w
     którym ostatnio pisano; pozostałe są pod strzałką. **„Tutaj”** wstawia do tego samego dokumentu.
   - **Kopiuj** daje PNG w schowku systemu, do wklejenia w innych aplikacjach. **×** odrzuca wycinek.
4. Po wstawieniu narzędzie zostaje Wycinkiem i można od razu wycinać kolejne fragmenty. Wstawiony obraz jest zaznaczony,
   a chwyt jego ramki przesuwa go albo zmienia jego rozmiar, tak jak lasso. Dokument celu staje się aktywny, więc
   **cofnij** od razu zdejmuje wstawienie (obraz i podpis to jeden krok).

Stuknięcie obok wycinka chowa go. Zmiana narzędzia też go chowa.

## Opcje

Opcje są w menu przy ikonie (jak przy lassie) albo na listwie w trybie „Opcje narzędzia + powrót”. Są też w Ustawieniach
→ „Gumka, lasso i wycinek”; wyszukiwarka znajduje je po słowach „wycinek”, „nożyczki”, „slajd”, „snip”.

- **Kształt.** Prostokąt (domyślnie, bo slajdy są prostokątne) daje ostre krawędzie. Lasso daje maskę: poza obrysem
  jest przezroczysto, brzeg jest wygładzony, pętla przecinająca się sama ze sobą działa według reguły parzystości.
- **Podpis źródła** (domyślnie wyłączony). Pod obrazem pojawia się szary tekst 9 pt, np. „slajd 12 · wykład.pdf” dla
  stron PDF albo „str. 3 · Fizyka” dla zwykłych stron.

## Jakość i format

- Obszar jest renderowany z dokumentu: papier, wzór, tło PDF z jego własnego renderu i wszystko, co leży w ramce. To
  nie są piksele ekranu. Skala wynosi 2× bieżącego powiększenia, nie mniej niż 3 i nie więcej niż 6 px/pt, a dłuższy
  bok ma najwyżej 3600 px.
- Wynikiem jest zwykły `ImageEl` (PNG w zasobach zeszytu). Format pliku zeszytu się nie zmienia, a obraz można
  przesuwać, skalować, obracać, kadrować i rozcinać jak każdy inny.
- Wstawiony obraz ma rozmiar 1:1 z kartką źródła. Gdy nie mieści się w 90 % strony celu, jest proporcjonalnie
  zmniejszany. Wklejanie dosuwa go całego do wnętrza strony.
- **Tryb ciemny.** Wycinek zachowuje kolory, w których strona powstała. W trybie ciemnym obraz przechodzi przez to samo
  przekształcenie co inne rysunki (`PageTone.isDrawing`: zrzut albo rysunek, a nie zdjęcie). Dotyczy to też wycinka
  czekającego na wstawienie, który jest pokazywany macierzą strony źródła.

## Kod

- `core/edit/SnipCut.kt` zawiera logikę bez Androida: plan renderu, przeliczenie na piksele, maskę lassa, dopasowanie
  do strony, elementy do wstawienia i podpis.
- `Editor.snipCut`, `insertSnip` i `copySnip` obsługują render w tle, maskę (wiersz po wierszu), zapis zasobu i
  wklejenie (`pasteElements`).
- `NoteView` ma gałąź `Tool.SNIP`: obrys, rysowanie czekającego wycinka, przeciąganie i upuszczanie.
  `LaneHost.dropSnip` przekazuje wycinek do drugiego pasa stanowiska.
- `EditorScreen.SnipBar` to pasek „Wstaw do” / „Tutaj” / „Kopiuj” / ×. `SnipStripItems` to opcje w menu i na listwie.

## Testy

`SnipCutTest` sprawdza:
- prostokąt z dowolnych narożników;
- plan (skala z powiększenia, przycięcie do strony, limit pikseli, za mały obszar);
- przeliczenie na piksele;
- maskę lassa (pole, wygładzony brzeg, ósemka) i maskę prostokąta;
- rozmiar wstawianego obrazu (slajd poziomy do pionowego A4);
- wycinek ze slajdu upuszczony przy rogu strony innego dokumentu: ląduje cały na stronie, z podpisem pod obrazem;
- cofanie wstawienia z podpisem jednym krokiem;
- podpis źródła.

## Do sprawdzenia na tablecie

- Płynność przeciągania wycinka między pasami i przewijanie przy krawędzi.
- Czytelność ikony na pasku.
- Wycinanie palcem przy ustawieniu „palec przewija” (wtedy wycina tylko rysik, tak jak przy lassie).
- Czas renderu dużego wycinka (cały slajd przy dużym powiększeniu).
