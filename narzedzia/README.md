# Narzędzia dla mNotes

Skrypty w Pythonie, które robią zeszyty `.mnotes` z plików spoza aplikacji. **Działają bez Claude:** wystarczy
Python 3 na komputerze, a polecenia są niżej i w `SKILL.md` każdego narzędzia. Każde narzędzie jest też gotowym
skillem Claude (plik `SKILL.md`), więc można je wgrać do aplikacji Claude i używać z telefonu.

Bez Claude, na komputerze:

```bash
# PDF z zadaniami → zeszyt: analiza, przegląd planu (plan.json), budowa
python3 narzedzia/zadania-do-mnotes/scripts/analyze.py ZADANIA.pdf --out /tmp/zm
python3 narzedzia/zadania-do-mnotes/scripts/build.py /tmp/zm/plan.json --format mnotes
# Samsung Notes (.sdocx albo PDF zgodny z Notatkami) → zeszyt
python3 narzedzia/samsung-notes-do-mnotes/scripts/convert.py NOTATKA.sdocx -o NOTATKA.mnotes
```

## `zadania-do-mnotes`

Zamienia PDF-y z zadaniami (np. listy od wykładowcy) na zeszyt `.mnotes` albo PDF: na początku oryginalne strony w A5,
dalej strony A5 w kratkę z obrazkami treści zadań (jedno zadanie lub podpunkt na stronę; przy podpunktach dwa osobne
obrazki: wspólna treść zadania i treść podpunktu). W odpowiedzi skill wkleja też spis treści zadań w bloczku kodu
Markdown (jeden PDF, jeden bloczek; wzory w LaTeX), który można skopiować do czata i od razu zadawać pytania. Spis powstaje
z obrazków zadań (`scripts/wycinki.py`), bo warstwa tekstowa PDF-ów bywa zniekształcona.

### Instalacja w Claude

1. Zbuduj archiwum: `narzedzia/pakuj.sh zadania-do-mnotes` (powstaje `narzedzia/zadania-do-mnotes.zip`, folder `zadania-do-mnotes/` na najwyższym poziomie).
2. claude.ai lub aplikacja → Ustawienia → Możliwości → Skille → Dodaj skill → wybierz ZIP.
   Włącz „Wykonywanie kodu i tworzenie plików”.
3. W rozmowie dołącz PDF i napisz np. „Zrób z tego zeszyt mNotes”.

Wymagania środowiska: Python 3 + Pillow + poppler-utils (albo PyMuPDF). Do przeskalowania oryginałów do A5 (i do wyniku PDF) pypdf, PyMuPDF albo
ghostscript. Skill sam sprawdza środowisko (`scripts/check_env.py`) i podpowiada, co doinstalować.

### Test lokalny

```bash
python3 narzedzia/zadania-do-mnotes/scripts/selftest.py
```

Test Kotlin `SkillZadaniaFormatTest` sprawdza, że aplikacja czyta strony i zeszyt zapisane przez skrypty.

## `samsung-notes-do-mnotes`

Zamienia notatki z Samsung Notes (plik `.sdocx` albo „PDF zgodny z Notatkami”, który ma osadzony `.sdocx`) na zeszyt
`.mnotes` z kreskami jako edytowalnymi obiektami (pióro, pędzel, zakreślacz) i takim samym tłem (kolor papieru, kratka
o rozstawie jak w Notatkach). Zwykły PDF z Samsung Notes to sam raster: skill go odrzuca z podpowiedzią, co wyeksportować.
Zasada: wygląd jak najwierniejszy oryginałowi i maksymalna edytowalność; gdy mNotes nie ma odpowiednika, skill wybiera
najbliższy i wypisuje odstępstwa w raporcie.

Etap pierwszy przenosi tylko kreski i tło. Pola tekstowe, obrazy, kształty, tło z PDF-a i warstwy trafiają do raportu jako
„Pominięto”; dojdą po testach na notatce ze wszystkimi typami obiektów. Import `.mnotes` do aplikacji jest istniejącym
„Importuj”.

### Instalacja w Claude

1. `narzedzia/pakuj.sh samsung-notes-do-mnotes` (powstaje `narzedzia/samsung-notes-do-mnotes.zip`).
2. claude.ai lub aplikacja → Ustawienia → Możliwości → Skille → Dodaj skill → wybierz ZIP.
   Włącz „Wykonywanie kodu i tworzenie plików”.
3. W rozmowie dołącz plik `.sdocx` (albo PDF zgodny z Notatkami) i napisz np. „Zrób z tego zeszyt mNotes”.

Wymagania: Python 3 (konwersja używa tylko biblioteki standardowej); do podglądu PNG dodatkowo Pillow i numpy.

### Test lokalny

```bash
python3 narzedzia/samsung-notes-do-mnotes/scripts/selftest.py
```

Samotest buduje syntetyczny `.sdocx` (`scripts/synth_sdocx.py`) i przechodzi całą konwersję bez prywatnych plików.
Test Kotlin `SkillSamsungFormatTest` czyta w aplikacji zeszyt wygenerowany z tej próbki
(`narzedzia/generuj-fixture-samsung.py` → `app/src/test/resources/samsung/`); fixture odnawiaj tylko po zmianie formatu
zapisu albo kalibracji piór.
