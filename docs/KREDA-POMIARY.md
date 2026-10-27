# Kreda: pomiary na prawdziwych zdjęciach (FAZA 2b, 2b-B2)

Agent B, 27.10.2026. Marcin: „kreda jest dalej do poprawy”. Zamiast poprawiać na oko — pomiar na wszystkich 24
zdjęciach z `kreda/blackboard/` (czytanych przez `KREDA_DIR`, do repozytorium nie trafiają), przed i po zmianach.

## Jak mierzone

`KredaPomiaryTest` (testLab, pomijany bez `KREDA_DIR`): zdjęcie podpróbkowane przy czytaniu do 1600 px, cały potok
(`BoardPipeline.run`, ustawienia domyślne) i wektoryzacja (`ChalkVector.vectorize`). Kolumny: próg auto (górna granica
0,50), **kreda %** = kryjące piksele wyniku wśród widocznych (wynik jest przycięty do tablicy), **kresek**, **średnia
długość kreski** w px wyniku, **fragmenty** = odsetek kresek krótszych niż 10 px, **czas** potoku + wektoryzacji na JVM
(laptop; tablet będzie wolniejszy). Uruchomienie:

```
KREDA_DIR=/home/piotr/Projects/Marcin/kreda/blackboard KREDA_OUT=/tmp/kreda ./gradlew --offline \
  :app:testLabDebugUnitTest --tests '*KredaPomiaryTest*'
```

(`KREDA_OUT` zapisuje podgląd: zdjęcie z narożnikami tablicy i przyciemnionym „nie-tablicą” obok krycia kredy, a w
`*-slady.png` mapę pisma z granicami przycięcia; ostatnia kolumna tabeli to odsetek maski odcięty jako ściana.) Progi jakości pilnuje osobno `RealPhotoTest`
(4 pierwsze zdjęcia: kreda 1–25 %, rozrzut tła < 0,2, ≥ 10 kresek, ≥ 70 % punktów kresek na jasnej kredzie zdjęcia).

## Co zmienione (każdy krok do wyłączenia w panelu i w Ustawienia → Laboratorium)

1. **Usuń listwy, ramy i ścianę** (`Chalk.dropThick`): jasny obszar, którego rdzeń jest dalej od tła niż 0,4 %
   szerokości obrazu **i** ma rozpiętość ≥ 8 % boku, znika razem z otoczką. Pierwsza wersja bez warunku rozpiętości
   zjadała pogrubione litery na rozmytym zdjęciu (`20260326_140000`: „δ≠R”, „[HA]⁺A⁻”) — poprawione i przetestowane
   (`compactBoldBlobIsALetterNotAFrame`).
2. **Dociągaj bladą kredę do wyraźnej** (histereza w `Chalk.toAlpha`): piksel jaśniejszy od 0,6 × próg zostaje, jeśli
   łączy się z pikselem ponad progiem; krycie liczy się wtedy od niższego progu. Blade części liter wracają,
   odosobnione smugi po gąbce nie (`faintPartOfALetterStaysIsolatedSmudgeGoes`).
3. **Usuń drobinki** (`Chalk.despeckle`): spójne plamki kryjące < 6 px (przy 1600 px, skalowane z polem obrazu).
4. **Łączenie przerwanych kresek** (`ChalkVector.bridge`): końce dwóch łamanych bliżej niż dwie grubości kreski
   (≤ 12 px) i zwrócone do siebie (≤ 35°) łączą się; kreski równoległe i „T” nie (`brokenChalkLineBecomesOneStroke`,
   `sideBySideAndPerpendicularStrokesStaySeparate`).
5. Wcześniej w fazie 2 (B2-6): sąsiedztwo m, ostrogi, łączenie w węzłach po kącie, grubość każdej kreski osobno,
   kreski na kredzie zdjęcia (94–100 % punktów na kredzie zamiast 28–51 %).

## Wyniki: przed → po

Średnio (24 zdjęcia):

| | kreda % | kresek | śr. długość px | fragmenty % | czas ms |
|---|---|---|---|---|---|
| przed | 6.6  |  485  |  31.4  |  21  |  506 |
| po | 3.9  |  468  |  29.6  |  19  |  524 |

Każde zdjęcie:

| zdjęcie | próg przed → po | kreda % przed → po | kresek przed → po | śr. długość px przed → po | fragmenty < 10 px % przed → po | czas ms przed → po |
|---|---|---|---|---|---|---|
| 20260317_142449.jpg | 0.50 → 0.50 | 6.7 → 2.9 | 513 → 492 | 26.8 → 25.2 | 23 → 18 | 1258 → 1160 |
| 20260318_152335.jpg | 0.33 → 0.33 | 5.8 → 2.9 | 416 → 409 | 31.1 → 28.6 | 23 → 20 | 655 → 796 |
| 20260318_152745.jpg | 0.29 → 0.29 | 4.2 → 2.2 | 329 → 330 | 33.4 → 28.1 | 23 → 22 | 519 → 515 |
| 20260318_152749.jpg | 0.47 → 0.47 | 4.0 → 1.9 | 236 → 239 | 31.8 → 27.5 | 26 → 25 | 381 → 360 |
| 20260318_153153.jpg | 0.25 → 0.25 | 6.6 → 2.9 | 328 → 326 | 30.0 → 25.0 | 34 → 29 | 374 → 423 |
| 20260318_153200.jpg | 0.44 → 0.44 | 5.4 → 3.9 | 528 → 507 | 35.1 → 34.5 | 20 → 18 | 455 → 704 |
| 20260318_153635.jpg | 0.40 → 0.40 | 4.1 → 1.8 | 384 → 373 | 26.7 → 24.6 | 18 → 18 | 494 → 554 |
| 20260318_153647.jpg | 0.45 → 0.45 | 6.2 → 4.5 | 591 → 583 | 27.9 → 26.7 | 25 → 21 | 515 → 554 |
| 20260318_154119.jpg | 0.50 → 0.50 | 3.6 → 3.7 | 554 → 553 | 27.6 → 28.5 | 17 → 15 | 387 → 380 |
| 20260318_154441.jpg | 0.50 → 0.50 | 7.8 → 1.9 | 405 → 392 | 37.2 → 28.4 | 13 → 15 | 517 → 494 |
| 20260318_155207.jpg | 0.44 → 0.44 | 5.4 → 3.3 | 484 → 482 | 31.5 → 29.8 | 15 → 14 | 457 → 513 |
| 20260318_155215.jpg | 0.43 → 0.43 | 3.1 → 3.3 | 415 → 413 | 24.1 → 25.4 | 20 → 17 | 327 → 346 |
| 20260318_155221.jpg | 0.43 → 0.43 | 2.7 → 2.5 | 397 → 393 | 27.3 → 27.8 | 16 → 15 | 350 → 365 |
| 20260318_155524.jpg | 0.44 → 0.44 | 5.8 → 4.8 | 312 → 308 | 41.5 → 41.2 | 24 → 21 | 473 → 521 |
| 20260323_135040.jpg | 0.50 → 0.50 | 10.2 → 6.8 | 608 → 570 | 29.2 → 29.6 | 21 → 19 | 367 → 428 |
| 20260323_135048.jpg | 0.50 → 0.50 | 8.6 → 4.9 | 785 → 729 | 33.6 → 32.8 | 15 → 14 | 535 → 567 |
| 20260324_154544.jpg | 0.50 → 0.50 | 8.2 → 3.6 | 486 → 458 | 31.2 → 31.2 | 26 → 21 | 541 → 564 |
| 20260326_140000.jpg | 0.50 → 0.50 | 14.8 → 9.4 | 708 → 654 | 25.5 → 25.6 | 33 → 30 | 473 → 527 |
| 20260326_144419.jpg | 0.32 → 0.32 | 6.3 → 3.2 | 450 → 435 | 38.1 → 36.5 | 10 → 11 | 516 → 538 |
| 20260326_161035.jpg | 0.42 → 0.42 | 7.9 → 4.4 | 481 → 467 | 37.1 → 34.7 | 19 → 16 | 493 → 461 |
| 20260326_161039.jpg | 0.38 → 0.38 | 7.7 → 4.3 | 472 → 440 | 39.7 → 35.2 | 20 → 17 | 494 → 493 |
| 20260401_133044.jpg | 0.43 → 0.43 | 6.2 → 3.8 | 358 → 343 | 32.0 → 30.0 | 19 → 15 | 354 → 360 |
| 20260525_134500.jpg | 0.50 → 0.50 | 9.3 → 4.0 | 638 → 596 | 30.1 → 28.3 | 23 → 23 | 594 → 422 |
| 20260525_144320.jpg | 0.50 → 0.50 | 8.2 → 6.1 | 784 → 745 | 24.6 → 25.3 | 30 → 26 | 625 → 544 |
| **średnio (24)** |  →  | 6.6 → 3.9 | 485 → 468 | 31.4 → 29.6 | 21 → 19 | 506 → 524 |

## Wnioski (uczciwie)

- **Spadek „kredy %” (6,6 → 3,9) to w większości usunięte listwy, ramy i ściana**, nie zgubione pismo: sprawdzone na
  podglądach (`20260326_140000`: listwa między tablicami i trójkąty ściany zniknęły, wszystkie wzory zostały; na czystym
  zdjęciu bez listew `20260318_154119` udział nawet wzrósł 3,6 → 3,7 % dzięki dociąganiu bladej kredy).
- **Fragmentacja prawie bez zmian (21 → 19 %).** Kreski < 10 px to w dużej części prawdziwe drobne znaki (indeksy,
  kropki, kreseczki strzałek) na zdjęciach 1600 px, więc ta miara nie jest czystą miarą błędu. Liczba kresek spadła
  (485 → 468), średnia długość prawie ta sama (31,4 → 29,6 px: wycięte długie listwy były „kreskami”).
- **Próg globalny dalej dobija do 0,50 na 9 z 24 zdjęć.** Histereza łagodzi skutek (blade części liter połączone z
  wyraźnymi wracają), ale całkiem blade litery nadal mogą zginąć. Próg lokalny (kafelkami) rozważałem i odłożyłem: w
  pustych kafelkach obniżyłby próg i wyciągnął smugi po gąbce, a bez zdjęcia z okładki PDF (Φ(r,θ,φ) na szarej tablicy;
  nie da się go tu wyrenderować — brak `pdftoppm`) nie umiem uczciwie sprawdzić, czy pomaga. **Prośba:** dorzucić to
  zdjęcie do `kreda/blackboard/` — test pomiarowy od razu je obejmie.
- **Znany problem: zgadywanie narożników** (`BoardGeometry.guessQuad`) na zdjęciach z dużym kawałkiem ściany i dolną
  listwą (`20260525_144320`, `20260525_134500`) obejmuje ścianę; cienka listwa (nie grubsza od kredy) zostaje wtedy jako
  długa kreska. Obejście w aplikacji: tryb „Narożniki” w panelu (B2-5). Poprawione częściowo niżej.
- Czas bez istotnej zmiany (506 → 524 ms na JVM przy 1600 px).

## Narożniki: maska bez ściany (`BoardGeometry.clipToContent`)

Przyczyna (zrzuty z narożnikami w `KREDA_OUT`): ściana, ekran projektora i głowy mają ten sam szary, mało nasycony kolor
co tablica i łączą się z nią w jedną plamę maski, więc skrajne punkty plamy leżą na ścianie. Poprawka, zachowawcza:

1. **Mapa pisma** (`inkMap`) na podglądzie 400 px: cienkie (top-hat), prawie bezbarwne (nasycenie < 0,2: skóra i ubranie
   odpadają) jasne ślady, bez długich prostych odcinków w paśmie 3 px (listwy, półka, ramy, nogi stojaka).
2. **Zakres pisma** (`contentBox`): profil śladów w wierszach i kolumnach, odcinki sklejane przez przerwy ≤ 5 % boku,
   od pierwszego do ostatniego odcinka z masą ≥ 10 % najcięższego (osobne bloki wzorów zostają), margines 2,5 %.
3. **Cięcie tylko tam, gdzie maska wychodzi poza kadr** (`clipEdges`): bok kończący się w kadrze to prawdziwa krawędź
   tablicy i zostaje razem z perspektywą. Bok przesuwa się o ≥ 2 % i **tylko gdy za nim zostaje ≤ 3 % śladów pisma**
   (bezpiecznik: przycięcie nie może zjeść pisma; test `writingBeyondTheCutKeepsTheSide`).

Próbowałem też cięcia po ramach (listwa, za którą prawie nie ma pisma): na `20260323_135040` „rama” okazała się pasem
20 kolumn (pochylona długa kreska) i cięcie zjadało pismo z prawej, więc ten wariant odrzuciłem.

Wynik (zmienione zdjęcia; reszta identyczna): sprawdzone na podglądach, żaden wzór nie zginął.

| zdjęcie | maski odcięto | wynik px przed → po | kreda % | kresek | co odpadło |
|---|---|---|---|---|---|
| 20260525_134500 | 12 % | 1206×851 → 1126×754 | 4.0 → 4.2 | 596 → 554 | ściana i filar z lewej, dolna półka |
| 20260525_144320 | 5 % | 1362×939 → 1355×763 | 6.1 → 5.9 | 745 → 737 | ściana pod półką (prawy bok z osobą zostaje: ślady osoby > 3 %) |
| 20260324_154544 | 3 % | 1564×887 → 1557×685 | 3.6 → 3.3 | 458 → 398 | ekran projektora nad tablicą (głowy na dole zostają) |
| 20260318_153200 | 27 % | 1590×854 → 1590×643 | 3.9 → 2.7 | 507 → 416 | pusty dół tablicy i półka |
| 20260326_161035 | 20 % | 1549×798 → 1525×649 | 4.4 → 5.2 | 467 → 453 | ekran pod tablicą |
| 20260326_161039 | 16 % | 1549×834 → 1541×712 | 4.3 → 5.1 | 440 → 417 | jak wyżej |
| 20260326_140000 | 11 % | 1366×881 → 1094×790 | 9.4 → 10.4 | 654 → 601 | ściana z boków (obie tablice całe) |
| 20260318_152749 | 26 % | 1232×828 → 932×799 | 1.9 → 2.5 | 239 → 216 | pusta lewa część; ucięte kadrem „V₀” na samym brzegu |
| 20260318_153635, 155221, 20260323_135040 | 1–3 % | | ±0,2 | ±10 | drobne paski przy brzegu |

Średnio (24): kreda 3,9 → 3,9 %, kresek 468 → 451 (mniej śmieci ze ściany), fragmenty 19 → 19 %. **Zostaje:** prawy bok
`20260525_144320` (osoba przy tablicy) i głowy na `20260324_154544`: tam obejściem jest tryb „Narożniki”.
