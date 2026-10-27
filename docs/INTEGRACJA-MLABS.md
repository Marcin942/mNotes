# Integracja mNotes (wariant lab) z mLabs: rozpoznawanie i wymiana stron (K4)

Agent B, noc 26/27.10.2026. Para do `mLabs/docs/INTEGRACJA-MNOTES-KREDA.md` (strona mLabs: jak model korzysta ze strony).
Tu strona mNotes: co jest w kodzie gałęzi `noc/lab` (od fazy 2 `noc/lab2`) i jak dojść do działającego rozpoznawania.
Kopia w repozytorium mNotes: `docs/INTEGRACJA-MLABS.md` (odsyła do niej `Recognizer.kt`).

## Po co

Kreda i notatki na stronie mają się dać **sprawdzić i przećwiczyć** w mLabs („sprawdź moje obliczenia”, „zadaj mi 3
pytania”). Model w mLabs czyta tylko tekst, więc kreski ze strony trzeba zamienić na tekst i wzory (rozpoznawanie),
a stronę wysłać jako obraz + JSON.

## Co jest w kodzie (`noc/lab`, katalog `app/src/lab/`)

| Plik | Co robi | Testy |
|---|---|---|
| `lab/recognize/Recognizer.kt` | interfejs `Recognizer` (`recognize(image, pageBox, kind)` → bloki z typem, treścią, pewnością, ramką, silnikiem), `NoRecognizer` (nic nie zgaduje) | `RecognizerTest` |
| ten sam plik, `PageExport` | JSON `mnotes-strona` w wersji 1 (pola jak w dokumencie mLabs), kolejność czytania bloków, nazwa pliku bez polskich znaków | `RecognizerTest.pageJsonFollowsTheMlabsFormat` |
| `core/image/*` | kreda (K1–K3): na wyjściu obraz kredy albo kreski pióra | `BoardTest`, `ChalkVectorTest` |

Celowo **bez modeli** i bez nowych zależności: TrOCR i TAMER są w mLabs (ONNX, ok. 100 MB), a decyzja, gdzie mają
działać, należy do Marcina.

## Droga do rozpoznawania (propozycja)

1. **Usługa w mLabs** (zalecane): mLabs wystawia `bound service` z uprawnieniem `signature` (obie aplikacje podpisane
   tym samym kluczem), metoda `recognize(png: ByteArray, kind: String): String` zwraca JSON bloków. W mNotes-lab
   `MlabsRecognizer : Recognizer` łączy się przez `bindService`; `available` = usługa znaleziona. Zero modeli w mNotes.
2. **Modele w mNotes-lab**: kopia `recognizer/` z mLabs (`TrocrRecognizer`, `Recognizer` TAMER, `OnnxBackend`) w
   `src/lab`, zależność `onnxruntime-android` tylko w wariancie lab. Prościej w obsłudze, ale +100 MB i dwa miejsca z tym
   samym kodem.
3. Segmentacja strony na linie i wzory: kreski mNotes są wektorowe, więc grupowanie po odstępach w pionie (linie)
   i po rozmiarze/symbolach (wzory) jest łatwiejsze niż na zdjęciu; zrobić w `core/` jako czystą funkcję z testami.

## Wymiana stron z mLabs

- „Wyślij do mLabs” (**jest od fazy 2, B2-8**): polecenie „Wyślij stronę do mLabs…” w menu Plik i palecie (Ctrl+K),
  tylko wariant lab (`LabFeatures.commands`/`Overlay`, `lab/recognize/SendToMlabs.kt`). Render bieżącej strony do PNG
  1600 px (papier, wzór, tło PDF, treść) + `PageExport.json(...)` w `cache/share/mlabs/<zeszyt>-s<n>.{png,json}` →
  `ACTION_SEND_MULTIPLE` z `FileProvider` (typ `*/*`, dodatek `com.mdev.mnotes.extra.PAGE` = nazwa pliku JSON). Bez sieci
  i bez INTERNET; bez intencji do konkretnego pakietu (mLabs nie jest w tym repozytorium), więc wybiera użytkownik.
- Bloki bez rozpoznawania: użytkownik może dopisać tekst ręcznie (blok `silnik: uzytkownik`, pewność 1.0); lepsze to niż
  zgadywanie.
- Strona mLabs: przepisy `nauka/wejscie/notatka-mnotes` i `nauka/wejscie/zdjecie-tablicy` mówią modelowi, jak czytać
  bloki i jak traktować niepewne (`[?]`).

## Kolejność wdrożenia

1. ✅ (B2-8) UI „Wyślij do mLabs” w menu (lab), z polem „Tekst strony (opcjonalnie)” zamiast rozpoznawania
   (`PageExport.userBlock`: jeden blok, pewność 1, ramka = cała strona; pusty tekst = bez bloków).
2. Usługa rozpoznawania w mLabs + `MlabsRecognizer`.
3. Kolejka rozpoznawania (pomysł Fable 17): rozpoznany tekst jako ukryty tekst strony do wyszukiwania.

## Jak sprawdzić

- JVM: `./gradlew :app:testLabDebugUnitTest --tests 'com.mdev.mnotes.lab.recognize.*'`
  (`RecognizerTest.userTextBecomesOneSureBlockAndEmptyTextNone`).
- Tablet (`labDebug`): menu Plik (albo Ctrl+K) → „Wyślij stronę do mLabs…” → wpisz wzór → „Udostępnij…” → wybierz
  aplikację (np. Pliki/mLabs); w odebranych plikach PNG strony i JSON z blokiem `silnik: uzytkownik`.
- Format: plik z `PageExport.json` przeczytany w mLabs przykładem z przepisu `nauka/wejscie/notatka-mnotes`
  (`python3 -I narzedzia/biblioteka.py przyklady nauka/`) daje te same pola.
