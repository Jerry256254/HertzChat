# Hertz Chat

Peer-to-peer, end-to-end šifrovaná chatovací aplikace pro Android - bez serveru, bez cloudu, bez registrace, bez jakékoliv firmy uprostřed.

[![Stáhnout nejnovější verzi](https://img.shields.io/github/v/release/Jerry256254/HertzChat?label=St%C3%A1hnout&style=for-the-badge&color=D97757)](https://github.com/Jerry256254/HertzChat/releases/latest)

**➡️ [Stáhnout z poslední verze](https://github.com/Jerry256254/HertzChat/releases/latest)**
— žádná registrace, žádný obchod, jen jeden soubor ke stažení (jedno
univerzální APK, funguje na všech běžných telefonech, žádné vybírání
architektury). V telefonu je potřeba povolit instalaci z neznámých zdrojů,
appka není z Play Store.

## Funkce

- **Místní síť úplně bez serverů** — když jsou obě zařízení na stejné Wi-Fi
  nebo hotspotu, najdou se přes mDNS (stejný mechanismus, jakým se hlásí
  tiskárny) a spojí se přímo podle IP adresy. Žádný I2P, žádný bootstrap,
  žádná infrastruktura - funguje i úplně bez internetu. Appka tuhle cestu
  použije automaticky, kdykoliv je dostupná.
- **Skutečně bez serveru** — dvě zařízení se najdou a spojí přímo přes
  veřejnou síť [I2P](https://geti2p.net/) (každé zařízení si otevře vlastní
  "destinaci" - I2P obdobu adresy). I2P je zdarma, decentralizovaná,
  nikým nevlastněná a nevyžaduje žádný účet ani registraci - a jako vedlejší
  efekt to řeší i procházení NAT/routerů a schová oběma stranám navzájem
  jejich skutečnou IP adresu. Nikdy tu není žádný server (ani náš, ani
  cizí), který by cokoliv přeposílal nebo ukládal. Běží přímo v appce jako
  obyčejný Java kód (ne jako samostatný spouštěný proces), takže appku
  nemůže rozbít to, že Android v novějších verzích omezuje spouštění
  vlastních binárek jako podprocesů - přesně to, co dřív rozbilo Tor.
- **End-to-end šifrované** — text, obrázky, videa i hlasové zprávy jsou
  šifrované Signal Protokolem (X3DH + Double Ratchet). Média mají navíc
  vlastní jednorázový klíč doručený stejnou šifrovanou cestou, takže se
  dají přenášet po částech bez zbytečného zatěžování ratchetu.
- **Zprávy počkají, až budeš online** — když příjemce zrovna nemá internet,
  zpráva se u odesílatele uloží a appka to zkouší znovu, dokud se nedoručí.
  "Online" tu neznamená mít appku otevřenou - stačí mít internet, appka
  naslouchá i na pozadí.
- **Galerie médií** — všechny fotky a videa z konverzace na jednom místě,
  listování prstem, přiblížení gestem i dvojklikem, videa s náhledy a
  přehrávačem, každá příloha jde jedním tlačítkem stáhnout do zařízení.
- **Foťák a editor fotek v appce** — focení přímo z chatu ve špičkové
  kvalitě (blesk, přední/zadní foťák) a editor s ořezem, kreslením a
  mozaikovou cenzurou. Fotky se jen připnou do rozepsané zprávy - klidně
  několik najednou - a odešlou se až s ní.
- **Hlasovky přidržením** — nahrávání jen po dobu držení tlačítka, pak
  náhled s poslechem: poslat, nebo smazat a zkusit znovu.
- **Vestavěný prohlížeč souborů** — texty, markdown a PDF se otevřou přímo
  v appce, bez stahování a bez opuštění zařízení.
- **Hledání v konverzaci a čištění chatu** — najde slovo, větu i frázi
  s proklikem na výskyty; historii jde lokálně vymazat bez smazání
  kontaktu.
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
  I2P adresu. Přijímání žádostí lze v nastavení i zautomatizovat.
- **Vypínatelné pozadí** — "Být dosažitelný" v nastavení opravdu vypne
  síť I2P i službu na pozadí, když zrovna nechceš být k zastižení - žádná
  appka tiše neběží dál a nebere baterii navíc.
- **Kontrola aktualizací přímo v appce** — nastavení umí zkontrolovat
  nejnovější verzi na GitHubu a rovnou nabídnout stažení.
- **Správa chatů** — připínání chatů, blokování uživatelů, historie zpráv
  šifrovaná na disku (SQLCipher, klíč vázaný na Android Keystore).
- **Přenos identity mezi zařízeními** — naskenováním QR kódu ze starého
  telefonu pokračuješ se stejnou identitou (i stejnou I2P adresou) na
  novém zařízení.
- **Otevřený zdrojový kód** — kompletně, žádná skrytá součást.
- **AI asistent Hertz** — KucLab Hertz (kuclab.org/hertz) zabudovaný přímo
  v appce jako webové zobrazení - přihlásíš se svým KucLab účtem, login
  zůstane uložený a nic se neotevírá venku v prohlížeči. Jediné místo
  v appce, kde obsah zprávy záměrně opouští zařízení (běží na serverech
  KucLabu) - podrobně vysvětleno v Podmínkách užití a Zásadách ochrany
  soukromí přímo v appce.
- **Skupinové chaty** — každý člen dostává zprávu zvlášť zašifrovanou jeho
  vlastním klíčem (žádný sdílený skupinový klíč); appka mezi členy, kteří
  se ještě neznají, automaticky vyřídí vzájemné přátelství.
- **`@zmiňování`** lidí ve skupinách, s našeptáváním jmen a zvýrazněným
  oznámením, když jsi zmíněn/a.

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
šifrování, embedded I2P router (`net.i2p:router`) pro serverless P2P
rendezvous a přenos, Room/SQLCipher pro lokální úložiště.

## Licence

[MIT](LICENSE)
