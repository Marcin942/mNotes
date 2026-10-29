# Geometria (2b-A8, NAU-3, 3c-B2)

Narzędzie do konstrukcji „jak cyrklem i linijką”. Najpierw wybierasz konstrukcję na listwie, potem wskazujesz na stronie
jej wejścia: punkty, odcinki, okręgi, trójkąt albo obiekt. Karta u góry mówi, co wskazać dalej. Wynik to zwykłe
elementy w kolorze pióra: wynik jest rysowany ciągłą kreską, linie pomocnicze przerywaną, a punkty dostają nazwy. Całość
cofa się jednym krokiem.

## Wskazywanie (3c-B2)

- **Przyciąganie.** Punkt łapie się na:
  - kropkach i wierzchołkach,
  - środkach odcinków,
  - środkach okręgów,
  - przecięciach dwóch odcinków, odcinka z okręgiem albo dwóch okręgów,
  - samym kształcie, czyli punkcie na odcinku albo na okręgu.

  Zasięg to 22 dp ekranu (dawniej 14 dp i tylko kropki oraz wierzchołki). Przy podobnej odległości kropka i wierzchołek
  wygrywają z przecięciem, a przecięcie ze środkiem. Punkt na kształcie łapie się tylko wtedy, gdy obok nie ma żadnego
  innego punktu i tylko w połowie zasięgu. Dzięki temu wolny punkt tuż obok odcinka zostaje wolny.
- **Przy podniesieniu.** Wejście wybiera się dopiero przy podniesieniu palca albo rysika. Dopóki dotykasz, widać:
  - podświetlony punkt z rodzajem w dymku, np. „przecięcie” albo „środek odcinka”;
  - pogrubiony odcinek lub okrąg, jeśli to jest wejście;
  - **lupę** 2,5× nad palcem. Przy górnej krawędzi lupa przechodzi pod palec;
  - **podgląd wyniku** w kolorze akcentu, gdy to ostatnie wejście konstrukcji. Okrąg cyrklem rośnie wtedy za palcem.

  Przy wskoczeniu na nowy punkt telefon lekko stuka (wibracja).
- **Rysik w zawisie** pokazuje to samo, czyli punkt i podgląd, bez lupy i bez dotykania ekranu.
- Dwa dotknięcia (pinch) przerywają wskazywanie bez wyboru wejścia.

## Konstrukcje

Listwa jest podzielona na grupy:

1. **Okręgi i punkty:** okrąg ze środka przez punkt, okrąg przez 3 punkty, okrąg o promieniu odcinka (przeniesienie
   cyrklem), środek okręgu, podział odcinka na równe części (chipy: na 2–10; przy podziale na 2 punkt dostaje nazwę M).
2. **Proste i kąty:** odcinek przez dwa punkty, symetralna, prostopadła i równoległa przez punkt, styczna w punkcie
   okręgu, kąt (łuk i miara), kąt o danej mierze przy punkcie, dwusieczna.
3. **Przecięcia:** prostych, prostej i okręgu, dwóch okręgów (z prostą przez punkty przecięcia), styczne z punktu.
4. **Trójkąt i wielokąty:** okrąg opisany i wpisany, wysokości, środkowe, wielokąt foremny na odcinku (chipy: 3–12
   boków).
5. **Przekształcenia (kopia obiektu):** odbicie względem prostej, obrót (kąt z chipów albo wpisany), odbicie względem
   punktu, przesunięcie o wektor (początek, koniec, obiekt), jednokładność (chipy: ×(−2), ×(−1), ×(−½), ×½, ×1,5, ×2,
   ×3).

Szczegóły:
- **Styczna w punkcie.** Punkt obok okręgu jest rzutowany na okrąg. Promień do punktu styczności jest rysowany
  przerywaną linią, a punkt styczności dostaje nazwę T.
- **Kąt o danej mierze.** Ramię to odcinek, a wierzchołek to punkt, zwykle koniec tego odcinka. Druga półprosta idzie
  przeciwnie do ruchu wskazówek zegara, tak jak kąt na kartce. Kąt ujemny odkłada ją w drugą stronę.
- **Wielokąt foremny.** Wielokąt jest budowany nad odcinkiem AB (dla A z lewej i B z prawej). Trójkąt z tej konstrukcji
  jest od razu trójkątem dla „Okrąg opisany” i pozostałych konstrukcji trójkąta.

## Żywa konstrukcja (NAU-3)

- Wynik pamięta w sesji, z czego powstał. Po przesunięciu wejścia liczy się na nowo. Zapisywane są tylko zwykłe
  elementy, więc format pliku się nie zmienia.
- Punkty z przyciągania (3c-B2) też są żywe:
  - przecięcie zależy od obu kształtów;
  - środek odcinka i punkt na odcinku idą za odcinkiem;
  - punkt na okręgu zachowuje swój kąt.

  Gdy przecięcie zniknie, np. okręgi się rozejdą, wynik zostaje tam, gdzie był. Gdy kształt przestanie być kształtem,
  np. okrąg rozciągnięty w elipsę, zależność się zrywa.
- **Podgląd w trakcie przeciągania (3c-B2).** Przy przesuwaniu, skalowaniu, obracaniu albo edycji punktów wejścia
  przeliczone wyniki są widoczne półprzezroczyście od razu. Dawniej wynik skakał dopiero po puszczeniu. Podgląd niczego
  nie zmienia (`Live.preview`); wynik zapisuje się przy puszczeniu, jak dotąd.

## Kod

- `core/geom/GeoSnap.kt`: punkty przyciągania, ich rodzaje i wagi oraz ponowne odczytanie punktu z bieżących
  elementów (`resolve`).
- `core/geom/GeoTool.kt`: rodzaje (`Kind` z grupą i parametrem), podpowiedzi, wskazywanie (`pickFrom`, `Picked.ids`,
  `resolvePick`) i budowa wyniku (`build` z `Params`).
- `core/geom/Live.kt`: `follow` (zapis) i `preview` (podgląd) na wspólnym `run`.
- `Editor.geoTap`, `Editor.geoProbe` (podgląd pod palcem), `geoParts`, `geoSides`, `geoScale`.
- `NoteView`: `geoProbeAt`, `drawGeoProbe`, `drawGeoLoupe`, `updateLivePreview` i `drawLivePreview`.
- `EditorScreen.GeoStripItems` (grupy z `Kind.group`) i `GeoHint` (chipy parametru).
- `GeoGlyphParts` w `PenGlyphs.kt` (glify nowych konstrukcji).

## Testy

- `GeoSnapTest`:
  - rodzaje punktów i pierwszeństwo;
  - przecięcia odcinków i okręgów, a przedłużenie nie daje przecięcia;
  - ponowne odczytanie punktu po ruchu kształtów;
  - okrąg ze środkiem w przecięciu idzie za odcinkami (Live);
  - podgląd przeciągania równy wynikowi zapisu i bez zrywania zależności;
  - podział, wielokąt i odcinek;
  - styczna, przeniesienie promienia i kąt o mierze;
  - przesunięcie i jednokładność;
  - podpowiedzi.
- `GeoToolTest`, `LiveTest`: wcześniejsze testy bez zmian, także dla wskazywania kropek i wierzchołków.

## Do sprawdzenia na tablecie

- Czy 22 dp to dobry zasięg palcem i rysikiem. Stała `GEO_SNAP_DP` jest w `NoteView`.
- Czytelność i położenie lupy, także przy krawędziach ekranu i na stanowisku z dwoma pasami.
- Płynność podglądu przy dużej stronie z wieloma kreskami (lupa rysuje fragment strony przy każdym ruchu).
- Czytelność glifów ośmiu nowych konstrukcji na listwie.
