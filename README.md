# mNotes

Zeszyt do pisania rysikiem na tablecie z Androidem, do nauki i na zajęcia. Działa w pełni offline: bez internetu, bez
kont i bez telemetrii. Aplikacja nie ma nawet uprawnienia `INTERNET`.

![Strony zeszytu-przewodnika](docs/podglad/przewodnik-strony.png)

## Co potrafi

- **Zeszyty i foldery.** Pióra, zakreślacz, gumka, lasso, kształty i tekst. Strony w linie, w kratkę i w kropki.
- **PDF-y.** Import slajdów z zajęć i pisanie po nich. Eksport do PDF i PNG.
- **Geometria.** Cyrkiel, kątomierz, linijka i konstrukcje: okrąg przez 3 punkty, przecięcia, dwusieczna.
- **Krzywe.** Krzywe Béziera rysowane piórkiem i edycja ich węzłów.
- **Nauka.** Taśmy zakrywające odpowiedzi (odsłaniane dotknięciem), tryb wykładu i zeszyt-przewodnik z ćwiczeniami.
- **Stanowisko.** Kilka zeszytów i PDF-ów obok siebie na jednym płótnie.
- **Wyszukiwanie.** Szuka w tytułach, tekstach, tagach stron i w ustawieniach (rozumie też synonimy).
- **Tryb ciemny.** Obejmuje interfejs i, jeśli chcesz, papier.
- **Łącze z telefonem** (wariant `gms`, **eksperymentalne**). Zdjęcie zrobione telefonem trafia na stronę na
  tablecie, a przez łącze przesyłasz też całe zeszyty. Działa bez internetu, a każda ramka jest szyfrowana kluczem
  pary. W wersji 0.1.0 jest domyślnie wyłączone (Ustawienia → Rozszerzenia) i nie działa jeszcze dość sprawnie.

Więcej podglądów w `docs/podglad/`, a specyfikacje funkcji w `docs/features/`.

## Warianty

| Wariant | Co zawiera |
|---|---|
| `foss` | Tylko wolne biblioteki (AndroidX, Compose, Kotlin). |
| `gms` | Dokłada eksperymentalne łącze z telefonem: przez Nearby z Usług Google Play albo przez Bluetooth (wybór w ustawieniach łącza). Łącze jest wyłączone, dopóki nie włączysz go w ustawieniach. |

Oba warianty mają ten sam identyfikator `com.mdev.mnotes` i ten sam format zeszytów.

## Budowanie

Potrzebujesz JDK 21 i Android SDK z platformą 36.1. Gradle przychodzi z wrapperem. Zanim zaczniesz, utwórz plik
`local.properties` z wpisem `sdk.dir=…`.

```sh
./gradlew :app:testFossDebugUnitTest :app:testGmsDebugUnitTest   # testy na JVM, bez urządzenia
./gradlew :app:assembleFossDebug                                  # APK w app/build/outputs/apk/foss/debug/
```

Logika (geometria, kodeki, układ, wyszukiwanie, łącze) leży w `app/src/main/java/com/mdev/mnotes/core/` i ma testy
jednostkowe. Zasady pracy nad kodem są w `CONTRIBUTING.md`.

W `narzedzia/` są skrypty w Pythonie, które zamieniają PDF z zadaniami i notatki z Samsung Notes na zeszyty
`.mnotes` (działają bez aplikacji i bez Claude, zob. `narzedzia/README.md`).

## Licencja

[Mozilla Public License 2.0](LICENSE) (MPL-2.0). Możesz używać, zmieniać i rozpowszechniać mNotes, także w większym
programie. Zmienione pliki mNotes muszą jednak zostać na MPL-2.0 i być dostępne w postaci źródłowej. Zależności
i podziękowania są w `NOTICE.md`.

## Jak powstał ten kod

Kod, testy i dokumentację pisali agenci AI (Claude, model firmy Anthropic). Pracowali według wymagań autora projektu,
spisywanych z jego notatek i uwag po testach. Autor wyznaczał, co robić i w jakiej kolejności, przeglądał wyniki
i sprawdzał aplikację na swoim tablecie.

- **Testy.** Logika w `core/` ma testy jednostkowe na JVM (ok. 900 w każdym wariancie). Każdą zmianę sprawdzają
  testy obu wariantów, a stare zeszyty pilnuje `FormatCompatTest`.
- **Co sprawdzono na urządzeniu.** Autor testuje kolejne wersje na tablecie Samsung. Część funkcji ma jednak tylko
  testy JVM i nie była jeszcze sprawdzona na prawdziwym sprzęcie. Najważniejsze z nich to łącze z telefonem
  (eksperymentalne) i stanowisko z kilkoma dokumentami.
- **Czego się spodziewać.** Kod jest czytelny i ma komentarze o tym, „dlaczego”. Mogą się jednak w nim trafić
  miejsca, które człowiek napisałby prościej. Zgłoszenia i poprawki są mile widziane (`CONTRIBUTING.md`).

## English

mNotes is an offline handwriting notebook for Android tablets with a stylus. It has notebooks, PDF annotation,
geometry tools, study tapes and an experimental phone-to-tablet photo link (`gms` flavor). The app, comments and docs
are in Polish, while the code itself is in English. To build it, run `./gradlew :app:assembleFossDebug` (JDK 21,
Android SDK 36.1). Licensed under the Mozilla Public License 2.0. Most of the code was written by AI agents (Claude)
following the author's requirements and reviews. The core logic has JVM unit tests, but not every feature has been
tested on a device yet.
