# Nearby: przebudowa łącza telefon ↔ tablet (FAZA 2b, 2b-B1)

27.10.2026, uwaga użytkownika: „nie działa: urządzenia się nie widzą, parowanie nie przechodzi, zdjęcie nie dociera; UI
beznadziejne po obu stronach”. Ten dokument powstał **przed** kodem: najpierw diagnoza z ówczesnego kodu i ze źródeł
(AAR `play-services-nearby:19.3.0` z cache Gradle, notatka z innej działającej aplikacji Nearby),
potem projekt. Czego nie da się sprawdzić bez dwóch urządzeń, jest oznaczone **[tablet]**.

## 1. Fakty ze źródeł (nie z pamięci)

Odczytane refleksją z `play-services-nearby-19.3.0-api.jar` (`ConnectionsStatusCodes`, `ConnectionsClient`, `Payload`):

| Kod | Stała | Co znaczy dla użytkownika |
|---|---|---|
| 8025 | `MISSING_SETTING_LOCATION_MUST_BE_ON` | wyłączona **usługa** lokalizacji (szybkie ustawienia), nie uprawnienie |
| 8029 | `MISSING_PERMISSION_NEARBY_WIFI_DEVICES` | Android 13+: „Urządzenia Wi-Fi w pobliżu” |
| 8030/8031 | `BLUETOOTH` / `BLUETOOTH_ADMIN` | Android ≤ 11 (uprawnienia z manifestu) |
| 8032/8033 | `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | z manifestu |
| 8034 | `MISSING_PERMISSION_ACCESS_COARSE_LOCATION` | brak lokalizacji |
| 8036 | `MISSING_PERMISSION_ACCESS_FINE_LOCATION` | brak **dokładnej** lokalizacji |
| 8037–8039 | `BLUETOOTH_SCAN` / `ADVERTISE` / `CONNECT` | Android 12+: „Urządzenia w pobliżu” |
| 8007 | `STATUS_RADIO_ERROR` | Bluetooth wyłączony albo zajęty |
| 8001/8002/8003 | już ogłasza / już szuka / już połączone | stan wewnętrzny, nie błąd |
| 8004 | `STATUS_CONNECTION_REJECTED` | druga strona odrzuciła |
| 8008 | `STATUS_ALREADY_HAVE_ACTIVE_STRATEGY` | inna strategia jeszcze aktywna (trzeba `stopAllEndpoints`) |
| 8050 | `API_CONNECTION_FAILED_ALREADY_IN_USE` | inna aplikacja używa Nearby |
| 13 | `STATUS_ERROR` | ogólny |

`ConnectionsClient.MAX_BYTES_DATA_SIZE = 1 047 552` B (nie 32 KB, jak zakładał wcześniejszy materiał). Typy ładunku:
`BYTES = 1`, `FILE = 2`, `STREAM = 3`; postęp: `PayloadTransferUpdate.Status` 1 sukces, 2 błąd, 3 w toku, 4 anulowano.
AAR nie deklaruje uprawnień łącza w swoim manifeście: wszystkie muszą być w manifeście aplikacji (są, `src/lab/AndroidManifest.xml`).
Notatka z innej działającej aplikacji Nearby: „`ACCESS_FINE_LOCATION` — szukanie wymaga jej także na nowych
Androidach; na 16 bez niej błąd 8034”.

## 2. Diagnoza: dlaczego dziś nie działa

1. **Obie strony szukają, nikt się nie ogłasza.** Okno parowania z Ustawień (`Lab.NearbySection`) startuje z rolą
   `CLIENT` („To jest telefon”) **na obu urządzeniach**; tablet musiał sam przełączyć chip. Bez tego dwa telefony‑klienci
   szukają się w nieskończoność („Szukam…”). Najbardziej prawdopodobna przyczyna „urządzenia się nie widzą”.
2. **Wyłączona usługa lokalizacji (8025) wygląda jak awaria Usług Google.** `isPermissionProblem` nie zna 8025, więc
   łącze ponawia co 2 s i pisze „Nearby nie wystartowało… Czy na urządzeniu są Usługi Google Play?”. Na Androidzie ≤ 11
   szukanie przez BLE wymaga włączonej lokalizacji; wielu ludzi ma ją wyłączoną.
3. **Lokalizacja przybliżona jako zgoda (B2-9) była błędem.** Źródło działającej aplikacji mówi, że szukanie wymaga
   dokładnej; Android 12+ pozwala dać tylko przybliżoną i wtedy Nearby zwraca 8036/8034. Wycofać: wymagać dokładnej i
   powiedzieć dlaczego, zanim system zapyta.
4. **Brak limitów czasu i ścieżki wyjścia.** „Szukam…”, „Czekam na …” wiszą bez końca, bez podpowiedzi, co sprawdzić.
5. **Zdjęcie w 100+ ładunkach po 30 KB bez postępu i potwierdzeń.** Limit BYTES to ~1 MB, więc porcje są niepotrzebnie
   drobne; nadawca nie wie, czy dotarło (dopiero `Note` na końcu), zerwanie w trakcie = cisza i zgubione zdjęcie.
6. **Nazwy punktów.** W parowaniu stała nazwa `mn-parowanie` (brak listy „który tablet?”, dwie pary w sali kolidują);
   telefon łączy się z pierwszym znalezionym.
7. **UI:** dwa różne okna (parowanie z chipami ról, odbiór, wysyłanie), stan mieszany z błędami, brak diagnostyki,
   kody błędów bez tłumaczenia.
8. Drobne już poprawione w B2-9: „Następne zdjęcie” nie wysyłało; para nie do odczytu wisiała jako „Czekam…”.

Czego nie wiem bez urządzeń **[tablet]**: czy Usługi Google na Tab S10+ i telefonie użytkownika są aktualne; czy Wi‑Fi Direct
się podnosi (ma wpływ tylko na szybkość); czy telefon ma włączoną lokalizację. Diagnostyka (§3.6) pokaże to na ekranie.

## 3. Projekt

### 3.1 Zasada: logika w `core/nearby`, Nearby tylko jako cienki transport

- `LinkTransport` (interfejs, `core`): `advertise(name)`, `discover()`, `stopSearch()`, `request(endpoint, name)`,
  `accept(endpoint)`, `reject(endpoint)`, `send(endpoint, bytes)`, `disconnect(endpoint)`. Zdarzenia wracają do maszyny:
  `Found(id, name)`, `Lost(id)`, `Initiated(id, name)`, `Connected(id)`, `ConnectFailed(id, code)`, `Disconnected(id)`,
  `Received(id, bytes)`, `Failed(code)`.
- `LinkMachine` (`core`): cały stan łącza, decyzje, limity czasu (zegar podawany z zewnątrz, testy bez `sleep`),
  parowanie (`PairingSession` z B2-9), szyfrowanie (`SyncCodec`), przesył zdjęć w częściach z potwierdzeniami.
  Czysta logika, testy na JVM z **symulowanym transportem** (`FakeAir`: dwa końce w jednym teście, utrata ramki,
  ponowienie, zerwanie w trakcie zdjęcia, odrzucenie, błąd 8025).
- `NearbyTransport` (`lab`): 1:1 wywołania `ConnectionsClient` i przekazanie zdarzeń, bez żadnej logiki.

### 3.2 Role z czynności, nie z wyboru

- **Odbiera ten, kto otworzył „Zdjęcie z telefonu”** (tablet): ogłasza się. **Wysyła ten, kto wybrał zdjęcie**
  (telefon): szuka. Nikt nie wybiera roli w trakcie pracy.
- **Parowanie**: jedno okno „Połącz telefon z tabletem” na obu urządzeniach; rola domyślna z ekranu
  (`smallestScreenWidthDp ≥ 600` → tablet ogłasza się, mniejszy → telefon szuka), zmiana jednym stuknięciem w zdaniu
  „To urządzenie: tablet · zmień”. Tak domyślny błąd z §2.1 znika. (Obie role naraz z rozstrzyganiem remisu odrzucam:
  nie umiem tu sprawdzić, czy `P2P_POINT_TO_POINT` pozwala ogłaszać i szukać jednocześnie, a pomyłka znów dałaby „nie widzą się”.)
- Strategia `P2P_POINT_TO_POINT` po obu stronach (jedna stała), `serviceId` ten sam: `com.mdev.mnotes.sync`.

### 3.3 Nazwy punktów i lista urządzeń

- Parowanie: `mnp|<nazwa urządzenia>` (np. `mnp|Galaxy Tab S10+`). Telefon pokazuje **listę znalezionych** z nazwami;
  jedno znalezione → łączy samo po 1 s (z „Anuluj”), kilka → stuknięcie wybiera. Obce nazwy (bez `mnp|`) ignorowane.
- Po parowaniu: `mn-` + skrót klucza pary (jak dziś): łączy się samo, obcy nie rozpozna.

### 3.4 Przesył zdjęcia

- Zdjęcie (JPEG ≤ 3000 px, jak dziś) dzielone na **części po 256 KB** (z zapasem pod limit 1 MB po szyfrowaniu),
  każda w wiadomości `PhotoPart(id, nr, z ilu, sha256 całości, bajty)` szyfrowanej `SyncCodec`. Odbiorca potwierdza każdą
  częścią `PhotoAck(id, nr)`; nadawca wysyła następną po potwierdzeniu (okno 2 części), brak potwierdzenia przez 15 s →
  ponowienie tej części (najwyżej 3 razy), potem błąd z czytelnym zdaniem. Po ostatniej części odbiorca sprawdza
  SHA-256 i wstawia; `PhotoDone(id)` → nadawca: „Wstawiono na tablecie”.
- Zerwanie w trakcie: nadawca wraca do szukania i po ponownym połączeniu **wznawia od pierwszej niepotwierdzonej
  części** (odbiorca trzyma części do końca okna, a `PhotoPart` z tym samym `id` i numerem nadpisuje).
- Dlaczego nie `Payload.FILE`: wymaga plików pośrednich i obsługi `asUri` na Androidzie 10+, a cała logika (postęp,
  ponowienia, wznowienie) byłaby w Nearby, nie w testowanym kodzie. Przy 256 KB i potwierdzeniach postęp jest i tak
  dokładny („wysyłam 2 z 3, 64 %”). Do zmiany, jeśli pomiar pokaże, że BYTES jest wolne **[tablet]**.

### 3.5 Stany i limity czasu (jedno zdanie + ikona w UI)

| Stan | Zdanie (przykład) | Limit | Po limicie |
|---|---|---|---|
| `Checking` | „Sprawdzam Bluetooth, Wi‑Fi i uprawnienia…” | — | — |
| `NeedsSetup(co)` | „Włącz lokalizację w szybkich ustawieniach (Android jej wymaga do szukania urządzeń).” | — | przycisk „Sprawdź jeszcze raz” |
| `Searching` | „Szukam tabletu… Na tablecie otwórz „Zdjęcie z telefonu”.” | 30 s | `NotFound` z listą „co sprawdzić” i „Szukaj dalej” |
| `Found(lista)` | „Znaleziono: Galaxy Tab S10+” | — | — |
| `Connecting(nazwa)` | „Łączę z Galaxy Tab S10+…” | 20 s | wraca do szukania, licznik prób |
| `ConfirmDigits(sas)` | „Czy na obu ekranach jest 482 913?” | — | czeka na użytkownika |
| `Securing` | „Zabezpieczam połączenie…” (hello) | 10 s | zerwij i szukaj |
| `Ready` | „Połączono z Galaxy Tab S10+.” | — | — |
| `Sending(i, n, %)` | „Wysyłam zdjęcie 2 z 3 · 64 %” | 15 s/część | ponowienie (3×), potem `Failed` |
| `Failed(kod)` | „Nie udało się: Bluetooth wyłączony (kod 8007). Włącz Bluetooth i spróbuj ponownie.” | — | „Spróbuj ponownie”, „Diagnostyka” |

Każdy kod z §1 ma zdanie „co zrobić” (`LinkErrors.explain(code)` w `core`, test, że każdy znany kod ma tekst).

### 3.6 Uprawnienia i diagnostyka

- Przed pierwszą prośbą systemową ekran „Dlaczego” (jedno zdanie na uprawnienie): „Urządzenia w pobliżu — żeby tablet i
  telefon się znalazły”, „Lokalizacja dokładna — Android wymaga jej do szukania przez Bluetooth; mNotes nie odczytuje
  położenia”. Wymagana dokładna (§2.3). Lista per wersja (`LinkPermissions.required(sdk)`, test):
  ≤ 30: `ACCESS_FINE_LOCATION` (+ usługa lokalizacji włączona); 31–32: `BLUETOOTH_SCAN/ADVERTISE/CONNECT` +
  `ACCESS_FINE_LOCATION`; 33+: jak 31 + `NEARBY_WIFI_DEVICES`.
- „Diagnostyka” (w oknie i w Ustawienia → Laboratorium): każde uprawnienie ✓/✗, Bluetooth włączony, Wi‑Fi włączone,
  usługa lokalizacji włączona, Usługi Google Play dostępne (`GoogleApiAvailability`), wersja Androida, **ostatnie 20 zdarzeń**
  łącza z czasem i kodem (pierścień w `LinkMachine`, bez treści zdjęć i kluczy), przycisk „Kopiuj raport”.

### 3.7 Jedno okno po obu stronach

`PhoneLinkDialog(tryb)` w stylu `MDialog`: ikona stanu (Bluetooth / szukanie / tarcza / ✓ / ⚠) i **duże zdanie** stanu,
pod nim jedna rzecz zależna od stanu (lista urządzeń, sześć cyfr `displaySmall`, pasek postępu, lista „co sprawdzić”),
na dole „Diagnostyka” i przyciski kontekstowe. Tryby: `Pair` (oba), `Receive` (tablet), `Send` (telefon; najpierw
wybór zdjęcia, potem łącze, „Następne zdjęcie”). Stare `PairDialog`/`ReceivePhotosDialog`/`SendPhotoDialog` znikają.

### 3.8 Bezpieczeństwo (bez zmian względem B2-9)

Parowanie commit-then-reveal (`PairingSession`), SAS z klucza pary i obu nonce, ramki AES-256-GCM z identyfikatorem sesji
i licznikiem (`SyncCodec`), klucz pary w Keystore, brak tajności w przód (świadomie). Części zdjęcia idą tym samym
szyfrowaniem; nowe typy wiadomości mają nowe kody bajtu typu (stara wersja dostaje `null` i je pomija).

## 4. Kolejność wdrożenia (każdy krok z testami, commit po kroku)

1. `core/nearby`: `LinkErrors`, `LinkPermissions.required`, wiadomości `PhotoPart/PhotoAck/PhotoDone`, `PhotoSender` i
   `PhotoReceiver` (części, potwierdzenia, ponowienia, wznowienie, SHA-256) — testy.
2. `core/nearby/LinkMachine` + `LinkTransport` + `FakeAir` — testy: parowanie przez symulowane powietrze (te same cyfry),
   praca po parowaniu, zdjęcie 3 części, utrata części → ponowienie, zerwanie w połowie → wznowienie, odrzucenie, 8025 →
   `NeedsSetup`, limit szukania → `NotFound`, dziennik zdarzeń.
3. `lab`: `NearbyTransport`, `PhoneLinkDialog` (trzy tryby), diagnostyka, podpięcie w `Lab` (przycisk telefonu,
   ustawienia), usunięcie starych okien i `NearbyLink`.
4. Potem (osobny punkt): ekran synchronizacji zeszytów na tej samej maszynie.

## 5. Jak sprawdzić na tablecie i telefonie **[tablet]**

1. Obie aplikacje `labDebug` z tej samej gałęzi. Ustawienia → Laboratorium → włącz łącze.
2. Tablet i telefon: „Połącz telefon z tabletem” — role ustawione same; telefon pokazuje „Znaleziono: <tablet>”; te same
   sześć cyfr → „Zgadza się” na obu.
3. Wyłącz lokalizację na telefonie → „Wyślij zdjęcie” → zdanie „Włącz lokalizację…” (nie „Usługi Google?”).
4. Tablet: „Zdjęcie z telefonu”; telefon: wybierz 3 zdjęcia po kolei → postęp „2 z 3 · %”, wstawione schodkami.
5. W połowie wysyłania wyłącz Bluetooth na tablecie na 5 s → po włączeniu wysyłanie wznawia się i zdjęcie dochodzi.
6. „Diagnostyka” → wszystko ✓; „Kopiuj raport” → dołącz do zgłoszenia błędu, jeśli coś nie działa.
