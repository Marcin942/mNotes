# Nearby: łącze telefon ↔ tablet bez routera (materiał, nie do kopiowania 1:1)

Z aplikacji mChochlik, gdzie telefon działa jako klawiatura tabletu. Pomysł dla mNotes: **sync dwóch
aplikacji** i **zdjęcie zrobione telefonem wstawiane od razu do notatki na tablecie**.
Kod w `kod/` jest tylko wzorem: nie kompiluje się bez dostosowania (patrz „Czego brakuje”).

## Jak to działa
- Google **Nearby Connections**, strategia `P2P_POINT_TO_POINT` (Bluetooth / Wi‑Fi Direct, bez wspólnej sieci).
  Jedna strona się ogłasza (`startAdvertising`), druga szuka (`startDiscovery`). W klawiaturze ogłasza tablet.
- **Nazwa punktu** = `"kbd-" + hex(HMAC(klucz_pary, "…-nearby"))[:16]`: obce urządzenia odpadają przy odkrywaniu.
  Nearby sam **nie uwierzytelnia**, więc każdą ramkę szyfruje AES‑256‑GCM kluczem pary (`LocalCodec`).
- **`LinkChunks`**: ładunek BYTES w Nearby ma limit ok. 32 KB, więc wiadomość dzielimy na porcje po 30 000 B
  (pierwszy bajt: 1 = ciąg dalszy, 0 = ostatnia), limit wiadomości 1 MiB. **Do zdjęć trzeba to rozszerzyć**
  (limit w górę albo `Payload.fromFile`/`fromStream` zamiast BYTES).
- **`LocalCodec`**: `hello` z losowym nonce od każdej strony → `sid` sesji (skrót obu nonce) → ramki z licznikiem
  `seq` w AAD (powtórki i nagrania z innej sesji odpadają).
- **Parowanie (ECDH P‑256 + 6 cyfr do porównania wzrokiem)**: `KeyboardCrypto.generateKeys/pairKey/sas`.
  W mChochliku klucze publiczne szły przez serwer; **w mNotes trzeba to zrobić inaczej** (bez serwera): np. kod QR
  z kluczem publicznym tabletu skanowany telefonem (w mNotes jest już skaner), potem te same 6 cyfr na obu ekranach.
  Klucz pary zapisz w Android Keystore / EncryptedSharedPreferences, nie jawnie.

## Zależność i uprawnienia
- `com.google.android.gms:play-services-nearby:19.3.0` (19.4.0 wymaga desugaringu, 19.5.x Kotlin 2.4 — przy Kotlinie 2.2
  zostań na 19.3.0). Wymaga Usług Google Play na obu urządzeniach.
- **To wyjątek od „bez nowych zależności” i od „bez INTERNET”**: Nearby nie wymaga uprawnienia INTERNET, ale to nowa
  biblioteka. Zapisz powód w `docs/NOTATKI-NOC.md`, a funkcję trzymaj za przełącznikiem, żeby bez niej aplikacja działała jak dotąd.
- Manifest: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES` (Android 13+),
  `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION` (szukanie wymaga jej także na nowych Androidach;
  na 16 bez niej błąd 8034). Uprawnienia żądaj w czasie działania. Kody błędów: `NearbyLink.isPermissionProblem`.

## Czego brakuje do użycia w mNotes
- `NearbyLink` zależy od typów klawiatury: `KbdLink`, `KbdLinkListener`, `Pairing`, `KbdRole` (SCREEN/KEYS) i `LocalMsg`
  (JSON z polem `t`). Zastąp własnymi: rola (np. `HOST`/`CLIENT`), własny `sealed interface` wiadomości
  (np. `Hello`, `NoteSync(diff)`, `Photo(nazwa, bajty)`) i własny serializer (`LocalCodec` używa `kbdJson`, `KbdLimits`).
- `ChochlikCrypto` to zwykłe AES‑GCM (`seal`/`open`, `nonce‖ct‖tag`); można zostawić albo zastąpić własnym.
- Zmień `SERVICE_ID` i ciągi w HMAC/AAD na własne (np. `com.mdev.mnotes.sync`).
- Tryb pracy `main` wątku: wywołania Nearby i `Handler` idą głównym wątkiem, stan bez blokad. Duże zdjęcia
  koduj i dekoduj poza nim.

## Pomysł na funkcje
1. **Zdjęcie z telefonu do notatki**: tablet „Dodaj zdjęcie z telefonu” → ogłasza → telefon (mNotes w trybie aparatu)
   robi zdjęcie, kompresuje (JPEG ~1–2 MB) i wysyła → tablet wstawia obraz w miejsce kursora.
2. **Sync zeszytów**: wymiana listy stron z wersją/datą zmiany, potem tylko brakujące strony (nie cały plik);
   konflikt: zostaw obie wersje strony. Format `.mnotes` zostaje bez zmian.

Testy w `testy/` (LinkChunks, ECDH/SAS/HKDF) przepisz przy zmianie nazw; prostsze zmiany nie wymagają telefonu.
