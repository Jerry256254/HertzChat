# Hertz Chat

Peer-to-peer, end-to-end šifrovaná chatovací aplikace pro Android - bez serveru, bez cloudu, bez registrace, bez jakékoliv firmy uprostřed.

[![Stáhnout nejnovější verzi](https://img.shields.io/github/v/release/Jerry256254/HertzChat?label=St%C3%A1hnout&style=for-the-badge&color=D97757)](https://github.com/Jerry256254/HertzChat/releases/latest)

**➡️ [Stáhnout z poslední verze](https://github.com/Jerry256254/HertzChat/releases/latest)**
— žádná registrace, žádný obchod, jen jeden soubor ke stažení (jedno
univerzální APK, funguje na všech běžných telefonech, žádné vybírání
architektury). V telefonu je potřeba povolit instalaci z neznámých zdrojů,
appka není z Play Store.

## Funkce

- **Skutečně bez serveru** — dvě zařízení se najdou a spojí přímo přes
  veřejnou síť [Tor](https://www.torproject.org/) (každé zařízení si
  publikuje vlastní "onion" adresu). Tor je zdarma, decentralizovaný,
  nikým nevlastněný a nevyžaduje žádný účet ani registraci - a jako vedlejší
  efekt to řeší i procházení NAT/routerů a schová oběma stranám navzájem
  jejich skutečnou IP adresu. Nikdy tu není žádný server (ani náš, ani
  cizí), který by cokoliv přeposílal nebo ukládal.
- **End-to-end šifrované** — text, obrázky, videa i hlasové zprávy jsou
  šifrované Signal Protokolem (X3DH + Double Ratchet). Média mají navíc
  vlastní jednorázový klíč doručený stejnou šifrovanou cestou, takže se
  dají přenášet po částech bez zbytečného zatěžování ratchetu.
- **Zprávy počkají, až budeš online** — když příjemce zrovna nemá internet,
  zpráva se u odesílatele uloží a appka to zkouší znovu, dokud se nedoručí.
  "Online" tu neznamená mít appku otevřenou - stačí mít internet, appka
  naslouchá i na pozadí.
- **Prohlížeč a editor médií** — fotky a videa na celou obrazovku (přiblížení
  gestem), přehrávač hlasovek, základní úprava obrázku (rotace, oříznutí na
  poměr stran) před odesláním.
- **Profilové fotky** — vlastní i u kontaktů, přenáší se stejně šifrovaně
  jako ostatní média.
- **Oznámení o nových zprávách** — i když appka zrovna neběží na popředí,
  stačí mít internet.
- **Plně anonymní identita** — žádné telefonní číslo, e-mail ani účet.
  Identita je kryptografický klíč vygenerovaný a uložený jen na tvém
  zařízení, s volitelnou přezdívkou (nebo náhodně vygenerovanou anonymní).
- **Přidávání kontaktů podle ID, ne procházením cizích lidí** — bez
  centrálního adresáře nejde "procházet, kdo je zrovna online". Místo toho
  ukážeš příteli svoje Hertz ID (QR kód nebo textový řetězec) mimo appku -
  on ho naskenuje/vloží a pošle ti žádost o přátelství přímo na tvou
  onion adresu.
- **Správa chatů** — připínání chatů, blokování uživatelů, historie zpráv
  šifrovaná na disku (SQLCipher, klíč vázaný na Android Keystore).
- **Přenos identity mezi zařízeními** — naskenováním QR kódu ze starého
  telefonu pokračuješ se stejnou identitou (i stejnou onion adresou) na
  novém zařízení.
- **Otevřený zdrojový kód** — kompletně, žádná skrytá součást.

## Design

Jednoduché, přehledné UI v duchu WhatsAppu/Messengeru (Jetpack Compose,
Material 3) — žádné ICQ retro, žádné přebytečné prvky. Světlý/tmavý/podle
systému, vlastní barevná paleta (bez Material You).

## Sestavení ze zdrojového kódu

Potřebuješ JDK 17 a Android SDK (platform 34, build-tools 34.0.0). Gradle
wrapper je součástí repozitáře.

```bash
./gradlew assembleDebug
```

Výsledný balíček najdete v `app/build/outputs/apk/debug/`.

## Technologie

Kotlin, Jetpack Compose, Signal Protocol (`libsignal-client`) pro E2E
šifrování, Tor onion služby (`org.briarproject:onionwrapper`) pro
serverless P2P rendezvous a přenos, Room/SQLCipher pro lokální úložiště.

## Licence

[MIT](LICENSE)
