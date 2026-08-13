---
schema_version: 1
app_name: Hertz Chat
package_id: cz.kuclab.hertzchat
version_name: "0.9.3"
version_code: 14
last_updated: 2026-08-14
license: MIT
category: Komunikace
short_description: >-
  Peer-to-peer, end-to-end šifrovaný chat bez serveru, přes síť I2P.
full_description: |-
  Hertz Chat je chatovací aplikace, za kterou nestojí žádný server - ani náš,
  ani cizí. Dvě zařízení se najdou a spojí přímo přes veřejnou síť I2P
  (každé si otevře vlastní "destinaci" - I2P obdobu adresy), zdarma, bez
  registrace, bez jakékoliv firmy uprostřed. Text, obrázky, videa i hlasové
  zprávy jsou navíc šifrované Signal Protokolem (X3DH + Double Ratchet),
  takže je nikdy nikdo jiný nemůže přečíst - ani autor aplikace.

  Identita je čistě zařízení - žádné telefonní číslo, e-mail ani registrace.
  Bez centrálního adresáře nejde procházet cizí online uživatele - kontakty
  se přidávají podle Hertz ID (QR kód nebo text), které si sdílíte mimo
  appku. Když příjemce nemá zrovna internet, zpráva u odesílatele počká a
  appka to zkouší znovu, dokud se nedoručí.
tags:
  - messaging
  - encryption
  - p2p
  - privacy
  - i2p
repository_url: https://github.com/Jerry256254/HertzChat
download_url: https://github.com/Jerry256254/HertzChat/releases/latest
logo: store/logo.png
screenshots: []
changelog:
  - version: "0.9.3"
    date: 2026-08-14
    notes:
      - "Odpovědi Mistral AI se teď zobrazují průběžně (streamování) místo čekání na celou odpověď najednou"
      - "Oprava: AI si uměla vygenerovat nesmyslný název konverzace jako doslovné „Název konverzace“ místo skutečného shrnutí tématu - lepší instrukce pro model a appka takové odpovědi teď rozezná a použije místo nich první zprávu"
      - Modernizovaná historie konverzací (/chats) - karty místo prostého seznamu, přejmenování a mazání konverzací
      - "I2P: prodloužen čas na první spuštění routeru (generování klíčů na mobilním CPU umí trvat přes minutu) a appka teď explicitně vyžádá počáteční „reseed“ (stažení seznamu routerů v síti), místo aby spoléhala na automatické spuštění, které v appce bez konzole nemusí proběhnout"
      - Přidána viditelná diagnostika sítě I2P (počet známých routerů, aktivních sousedů a stav reseedu) pod stavovým řádkem na obrazovce Kontakty
  - version: "0.9.2"
    date: 2026-08-14
    notes:
      - "Oprava: síť I2P se uměla zaseknout na „Připojování... 0 %“ donekonečna - appka čekala na síťové potvrzení od více sousedních routerů bez časového limitu. Teď má bootstrap tvrdý časový limit (60 s) a nižší práh, takže appka vždy dojde do stavu Připojeno, i když se síť zrovna rozjíždí pomaleji."
      - Nový vzhled vstupního pole zpráv (zaoblená "pilulka" ve stylu appky, kulaté tlačítko odeslat/nahrát vedle ní), posunuté výš nade dno obrazovky
      - "Oprava: vstupní pole zpráv zůstávalo schované za klávesnicí místo aby se zvedlo nad ni"
      - Dlouhé podržení na kontaktu/chatu v seznamu teď nabídne menu i u Mistral AI (skrytí asistenta místo blokování, které tam nedává smysl)
      - Klepnutí na jméno nebo fotku kontaktu v otevřeném chatu teď otevře jeho detail (Hertz ID ke zkopírování, tlačítko Blokovat)
  - version: "0.9.1"
    date: 2026-08-14
    notes:
      - "Oprava pádu appky: po startu I2P routeru appka zkoušela otevřít vlastní adresu dřív, než byl router skutečně připravený - způsobovalo to pád (NullPointerException). Teď appka počká, dokud router opravdu neběží."
      - "Oprava: horní lišta na obrazovce Chaty byla zbytečně vysoká"
  - version: "0.9.0"
    date: 2026-08-13
    notes:
      - "Zásadní změna architektury: síť Tor nahrazena sítí I2P. Na reálném zařízení se ukázalo, že Android v novějších verzích umí zablokovat spouštění vlastní binárky (tor) jako podprocesu - appka na to narážela nepředvídatelně a bez opravy z naší strany. I2P router běží přímo v appce jako obyčejný Java kód, ne jako spouštěný proces, takže na stejný problém narazit nemůže."
      - "Oprava: release v0.8.0 omylem obsahoval starší sestavení appky (verze 0.7.0) - appka si po instalaci nejnovějšího souboru mylně myslela, že běží stará verze"
      - "Oprava: aktualizace appky už nemaže uloženou historii zpráv a kontakty (kromě jednorázového smazání při přechodu na tuto verzi) - od teď mají databázové změny opravdovou migraci, ne jen smazání a založení znovu"
      - Redesign seznamu chatů a otevřeného chatu (větší úvodní lišta, výraznější avatary, karty místo rovných řádků, zaoblené „bubliny" zpráv s ocáskem, kulaté tlačítko odeslat/nahrát)
      - Oprava chybějícího tlačítka zpět v otevřeném chatu
  - version: "0.8.0"
    date: 2026-08-13
    notes:
      - "Nová funkce: skupinové chaty. Každý člen dostává zprávu zvlášť zašifrovanou jeho vlastním klíčem (žádný sdílený skupinový klíč) - appka mezi členy, kteří se ještě neznají, automaticky vyřídí vzájemné přátelství"
      - "Nová funkce: @Mistral přímo v běžném i skupinovém chatu - napiš „@Mistral 10 shrň to" a appka pošle posledních 10 zpráv (jen od účastníků, kteří to nezakázali) spolu s dotazem na Mistral AI a odpověď vloží zpět do konverzace"
      - "Nové nastavení „Povolit ostatním @Mistral u mých zpráv" (výchozí zapnuto) - řídí, jestli tvoje zprávy smí být použité jako kontext pro cizí dotazy na @Mistral; volba se broadcastuje kontaktům"
      - "Nová funkce: @zmiňování lidí (i @Mistral) ve skupinách s automatickým našeptáváním jmen a zvýrazněným oznámením, když jsi zmíněn/a"
      - "Pozor: aktualizace databáze při této verzi znovu smaže uloženou historii zpráv na zařízení"
  - version: "0.7.0"
    date: 2026-08-13
    notes:
      - "Oprava: síť Tor se uměla zaseknout na neustálém „Navazuje se spojení...“ bez chyby a bez možnosti to zkusit znovu - knihovna pro Tor nemá vestavěný časový limit, takže zaseknuté volání blokovalo appku donekonečna. Teď má start i zveřejnění onion adresy tvrdý časový limit a při selhání se rovnou zobrazí chyba s tlačítkem Zkusit znovu."
      - Kompletní redesign obrazovky Nastavení (sjednocené karty, ikony, přehlednější členění)
      - "Appka teď jde přepnout do několika jazyků (čeština, angličtina, němčina, španělština, francouzština, ukrajinština, ruština) - volitelně už při zakládání identity nebo kdykoliv v Nastavení"
      - Mistral AI asistent si teď sám vymyslí název konverzace podle obsahu, místo prostého oříznutí první zprávy
      - Oprava rozložení a přidání tlačítka zpět na obrazovkách přenosu identity (QR export/import)
      - "Ověřeno: text, obrázky, videa i hlasové zprávy jsou vždy šifrované (Signal Protokol pro text, AES-256-GCM pro média) předtím, než cokoliv opustí zařízení"
  - version: "0.6.0"
    date: 2026-08-13
    notes:
      - "Nová funkce (volitelná, vypnutá ve výchozím stavu): vestavěný AI asistent postavený na Mistral AI. Vlastní trvalý kontakt s vlastní historií konverzací (/new založí novou, /chats mezi nimi přepíná), používá výhradně vlastní API klíč(e) uživatele (jde přidat libovolný počet, appka je zkouší popořadě, pokud jeden selže/dojde mu kvóta)"
      - "Jde o jedinou funkci appky, kde obsah zprávy záměrně opouští zařízení - důkladně zdokumentováno v Podmínkách užití, Zásadách ochrany soukromí a v samostatném souhlasu přímo v appce, který je nutné potvrdit před prvním zapnutím"
      - Výběr modelu (small/medium/large) a odkazy na vytvoření Mistral účtu a dočasného e-mailu pro další API klíč
      - "Pozor: aktualizace databáze při této verzi znovu smaže uloženou historii zpráv na zařízení (stejně jako u minulé aktualizace schématu)"
  - version: "0.5.0"
    date: 2026-08-13
    notes:
      - "Zásadní oprava: appka nikdy nepřestala ukazovat „Nepřipojeno“ a vlastní QR kód se nikdy nenačetl. Příčina: knihovna pro Tor se při každém startu pokoušela nainstalovat binárku pro tzv. pluggable transporty (Lyrebird), kterou appka nikdy nebalila - na Androidu 10+ to spolehlivě shodilo start Tor sítě hned na začátku, a appka tu chybu potichu polykala. Teď je binárka součástí appky a chyba se navíc zobrazí (s tlačítkem Zkusit znovu), místo aby appka jen donekonečna točila kolečko."
      - Kontrola nové verze appky přímo v nastavení (porovná se s poslední verzí na GitHubu, s odkazem ke stažení)
      - Automatické přijímání žádostí o přátelství (volitelné, v nastavení)
      - "Vypnutí „Být dosažitelný“ teď appku na pozadí opravdu vypne (zastaví síť Tor i službu na pozadí), místo aby jen tiše běžela dál a zbytečně brala baterii"
      - Oprava rozbitého rozložení: tlačítko pro povolení fotoaparátu při skenování QR bylo v rohu obrazovky místo uprostřed
      - Oprava useknutého horního textu na úvodní obrazovce (obsah se kreslil pod stavovým řádkem)
  - version: "0.4.0"
    date: 2026-08-13
    notes:
      - "Oprava: přidání kontaktu skenováním QR nefungovalo (vlastní ID se sdílelo dřív, než ho síť Tor vůbec zjistila) - teď appka počká a k tomu ukáže zpětnou vazbu (odesláno/chyba)"
      - Skutečná vyskakovací oznámení o nových zprávách a žádostech o přátelství
      - Profilové fotky (vlastní i u kontaktů)
      - Volba motivu (světlý/tmavý/podle systému) a vypnutí Material You barev z tapety, které appku dělaly fádní
      - Přehled a mazání uloženého místa zabraného médii
      - Nastavení kvality odesílaných obrázků teď skutečně ovlivňuje odeslaný soubor
  - version: "0.3.0"
    date: 2026-08-13
    notes:
      - "Zásadní změna architektury: signalizační server nahrazen sítí Tor (onion služby) - appka teď skutečně nepotřebuje žádný server, ani vlastní, ani cizí"
      - Přidávání kontaktů přes sdílené Hertz ID (QR/text) místo procházení online uživatelů
      - Fronta a automatický opakovaný pokus o doručení, když příjemce nemá internet
      - Zobrazení stavu doručení zprávy (čeká se / odesláno / doručeno)
  - version: "0.2.2"
    date: 2026-08-13
    notes:
      - "Oprava: appka spadla během první vteřiny po spuštění. Příčina: R8 (minifikace v release buildu) přejmenovávala/odstraňovala třídy SQLCipher, do kterých se natívní knihovna odkazuje přesným jménem - přidána chybějící keep pravidla."
      - Nová jednodušší ikona, která se nekříží ošklivě v kulaté masce Androidu
  - version: "0.2.1"
    date: 2026-08-13
    notes:
      - "Oprava: appka nabízela 3 samostatná APK podle architektury zařízení - kdo si stáhl nesprávné, aplikace mu hned po spuštění spadla (chyběly nativní knihovny). Teď je jen jedno univerzální APK, které funguje na všech zařízeních."
      - Nová ikona aplikace
      - Menší APK díky odstranění nepoužívané testovací nativní knihovny
  - version: "0.2.0"
    date: 2026-08-13
    notes:
      - Sdílení obrázků, videí a hlasových zpráv (AES-256-GCM přenos po částech)
      - Prohlížeč médií na celou obrazovku s přiblížením, přehrávač hlasovek
      - Základní editor obrázků (rotace, oříznutí na poměr stran)
  - version: "0.1.0"
    date: 2026-08-13
    notes:
      - První verze: anonymní identita, X3DH/Double Ratchet E2E šifrování
      - P2P přenos přes WebRTC s volitelným self-hosted signalizačním relay
      - Vyhledávání online kontaktů, žádosti o přátelství, blokování
      - Seznam chatů s pinováním, textové zprávy
      - Přenos identity mezi zařízeními přes QR kód
      - Rozšířené nastavení soukromí a sítě
---

# Hertz Chat

Peer-to-peer, end-to-end šifrovaný chat bez serveru, přes síť I2P.

## Popis

Hertz Chat je chatovací aplikace, za kterou nestojí žádný server - ani náš,
ani cizí. Dvě zařízení se najdou a spojí přímo přes veřejnou síť I2P, zdarma,
bez registrace. Text, obrázky, videa i hlasové zprávy jsou navíc šifrované
Signal Protokolem (X3DH + Double Ratchet), takže je nikdy nikdo jiný nemůže
přečíst - ani autor aplikace.

Identita je čistě zařízení - žádné telefonní číslo, e-mail ani registrace.
Kontakty se přidávají podle sdíleného Hertz ID (QR kód nebo text), ne
procházením cizích online uživatelů, a identitu lze přenést na nové zařízení
naskenováním QR kódu.

## Logo

![Hertz Chat logo](store/logo.png)

## Odkazy

- Zdrojový kód: <https://github.com/Jerry256254/HertzChat>
- Stažení nejnovější verze: <https://github.com/Jerry256254/HertzChat/releases/latest>
- Licence: [MIT](LICENSE)
