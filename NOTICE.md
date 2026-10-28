# NOTICE

mNotes jest udostępniony na licencji Mozilla Public License 2.0 (`LICENSE`). Biblioteki niżej mają własne licencje.

## Zależności w aplikacji

| Biblioteka | Licencja | Wariant |
|---|---|---|
| AndroidX: core-ktx, activity-compose, fragment, biometric, exifinterface, input-motionprediction, lifecycle, profileinstaller | Apache-2.0 | oba |
| Jetpack Compose (BOM): ui, ui-graphics, material3, material-icons-extended, ui-tooling-preview | Apache-2.0 | oba |
| kotlinx-coroutines-android | Apache-2.0 | oba |
| Kotlin standard library | Apache-2.0 | oba |
| `com.google.android.gms:play-services-nearby` | Android Software Development Kit License (niewolna) | tylko `gms` |

## Tylko w testach (nie trafiają do APK)

| Biblioteka | Licencja |
|---|---|
| JUnit 4 | EPL-1.0 |
| org.json | zob. licencja w artefakcie Maven |

## Grafika i pliki testowe

- Ikona aplikacji i glify narzędzi (pióra, gumka, lasso, zdjęcie tablicy) to własne wektory rysowane w kodzie.
- Ikony interfejsu pochodzą z Material Icons (Apache-2.0).
- Aplikacja nie ma własnych plików czcionek, używa czcionek systemowych.
- Obrazy w `docs/podglad/` to rendery z testów, nie zdjęcia.
- Pliki w `app/src/test/resources/` są syntetyczne, bez prywatnych danych.

## Podziękowania

Pomysły konstrukcji geometrycznych, eksportu i części narzędzi pochodzą z pracy licencjackiej. Dziękujemy za nią.
Nazwisko podamy tylko za zgodą osoby, która ją napisała. Kod mNotes napisano od zera, bez kopiowania kodu z tej pracy.
