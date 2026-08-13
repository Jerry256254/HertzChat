# Hertz Chat

Peer-to-peer, end-to-end šifrovaná chatovací aplikace pro Android bez serveru, cloudu a registrace.

[![Stáhnout nejnovější verzi](https://img.shields.io/github/v/release/Jerry256254/HertzChat?label=St%C3%A1hnout&style=for-the-badge&color=D97757)](https://github.com/Jerry256254/HertzChat/releases/latest)

**➡️ [Stáhnout z poslední verze](https://github.com/Jerry256254/HertzChat/releases/latest)**
— žádná registrace, žádný obchod, jen soubor ke stažení.
V telefonu je potřeba povolit instalaci z neznámých zdrojů, appka není z Play Store.

## Funkce

- **Čistě P2P a end-to-end šifrované** — text, obrázky, videa i hlasové
  zprávy jdou vždy přímo mezi zařízeními (WebRTC), šifrované Signal
  Protokolem (X3DH + Double Ratchet). Média mají navíc vlastní jednorázový
  klíč doručený stejnou šifrovanou cestou, takže se dají přenášet po
  částech bez zbytečného zatěžování ratchetu. Žádný server nikdy neuvidí
  obsah, nic se nikde neukládá kromě zařízení odesílatele a příjemce.
- **Prohlížeč a editor médií** — fotky a videa na celou obrazovku (přiblížení
  gestem), přehrávač hlasovek, základní úprava obrázku (rotace, oříznutí na
  poměr stran) před odesláním.
- **Plně anonymní identita** — žádné telefonní číslo, e-mail ani účet.
  Identita je kryptografický klíč vygenerovaný a uložený jen na tvém
  zařízení, s volitelnou přezdívkou (nebo náhodně vygenerovanou anonymní).
- **Vyhledávání online kontaktů** — najdeš jen ty, kdo mají appku zrovna
  otevřenou; žádosti o přátelství se přijímají/odmítají a jednou přijatý
  kontakt zůstává uložený lokálně.
- **Správa chatů** — připínání chatů, blokování uživatelů, historie zpráv
  šifrovaná na disku (SQLCipher, klíč vázaný na Android Keystore).
- **Přenos identity mezi zařízeními** — naskenováním QR kódu ze starého
  telefonu pokračuješ se stejnou identitou na novém.
- **Rozšířené soukromí a nastavení** — přepínač viditelnosti online, vlastní
  signalizační/TURN server, kvalita odesílaných médií.
- **Otevřený zdrojový kód** — včetně volitelného slepého signalizačního
  serveru pro navázání P2P spojení (`/signaling-relay`), který si může
  kdokoliv sám nasadit.

## Design

Jednoduché, přehledné UI v duchu WhatsAppu/Messengeru (Jetpack Compose,
Material 3) — žádné ICQ retro, žádné přebytečné prvky. Světlý i tmavý režim.

## Sestavení ze zdrojového kódu

Potřebuješ JDK 17 a Android SDK (platform 34, build-tools 34.0.0). Gradle
wrapper je součástí repozitáře.

```bash
./gradlew assembleDebug
```

Výsledný balíček najdete v `app/build/outputs/apk/debug/`.

Volitelný signalizační relay server pro P2P handshake je v `/signaling-relay`
(Node.js) — viz jeho vlastní README.

## Technologie

Kotlin, Jetpack Compose, Signal Protocol (`libsignal-client`) pro E2E
šifrování, WebRTC pro P2P přenos, Room/SQLCipher pro lokální úložiště.

## Licence

[MIT](LICENSE)
