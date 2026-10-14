# Skille dla mNotes

Folder zawiera skille Claude, które pracują na danych z mNotes bez komputera właściciela (w aplikacji Claude na
telefonie albo na claude.ai, z włączonym wykonywaniem kodu).

## `zadania-do-mnotes`

Zamienia PDF-y z zadaniami (np. listy od wykładowcy) na zeszyt `.mnotes` albo PDF: na początku oryginalne strony w A5,
dalej strony A5 w kratkę z obrazkami treści zadań (jedno zadanie lub podpunkt na stronę; przy podpunktach dwa osobne
obrazki: wspólna treść zadania i treść podpunktu).

### Instalacja w Claude

1. Zbuduj archiwum: `skills/pakuj.sh` (powstaje `skills/zadania-do-mnotes.zip`, folder `zadania-do-mnotes/` na najwyższym poziomie).
2. claude.ai lub aplikacja → Ustawienia → Możliwości → Skille → Dodaj skill → wybierz ZIP.
   Włącz „Wykonywanie kodu i tworzenie plików”.
3. W rozmowie dołącz PDF i napisz np. „Zrób z tego zeszyt mNotes”.

Wymagania środowiska: Python 3 + Pillow + poppler-utils (albo PyMuPDF). Do wyniku PDF dodatkowo pypdf, PyMuPDF albo
ghostscript. Skill sam sprawdza środowisko (`scripts/check_env.py`) i podpowiada, co doinstalować.

### Test lokalny

```bash
python3 skills/zadania-do-mnotes/scripts/selftest.py
```

Test Kotlin `SkillZadaniaFormatTest` sprawdza, że aplikacja czyta strony i zeszyt zapisane przez skrypty.
