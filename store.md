---
schema_version: 1
app_name: Hertz Chat
package_id: cz.kuclab.hertzchat
version_name: "0.20.0"
version_code: 27
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
  - version: "0.20.0"
    date: 2026-08-14
    notes:
      - "Oprava pádu po chvíli běhu na pozadí. Příčinu ukázal záznam pádu z předchozí verze: knihovna UPnP uvnitř I2P routeru na Androidu nemá XML parser a z vlastního vlákna vyhodila výjimku („No XML parser defined - Try to invoke UPnP.setXMLParser before“), která shodila celou aplikaci. UPnP je teď vypnuté - jen říká domácímu routeru, ať přesměruje příchozí port, což na mobilních datech nefunguje a pro nás nemá smysl, protože stejně nepřeposíláme cizí tunely."
      - "Chyba v jednom vlákně I2P routeru už neshodí celou aplikaci. Router si drží spoustu vlastních vláken a neodchycená výjimka v kterémkoli z nich normálně ukončí celý proces - teď zanikne jen to jedno vlákno, chyba se uloží k pozdější diagnostice a chat běží dál."
  - version: "0.19.0"
    date: 2026-08-14
    notes:
      - "Oprava: u vyskakovacích nabídek vykukovaly hranaté rohy. Zaoblení se dřív dávalo jen na obsah, ale samotnou plochu nabídky kreslí systémová komponenta pod ním - teď se zaobluje ta."
      - "Oprava: zpráva sama sobě zůstávala na „Čeká se, až bude příjemce online...“. Příjemcem jsi ty sám, takže zpráva je doručená ve chvíli, kdy se uloží - už se neposílá přes I2P ven a zpátky."
      - "Obsah bublin s přílohou je vycentrovaný. Tlačítko přehrání u hlasovky mělo kolem sebe povinnou dotykovou plochu, která bublinu zleva roztahovala víc než zprava."
  - version: "0.18.0"
    date: 2026-08-14
    notes:
      - "Appka si teď sama zapamatuje záznam pádu a při dalším spuštění ho ukáže (i s tlačítkem Zkopírovat). Bez toho je jediná stopa po pádu jen v systémovém logu, ke kterému se bez připojení telefonu k počítači nedostaneš."
      - "Sestaveno kompletně načisto - předchozí build 0.17.0 dojel na mezivýsledcích ze dvou běhů, které systém ukončil v půlce, což může vytvořit balíček, jenž se přeloží bez chyby, ale za běhu spadne."
      - "Vlastní kód appky se už při sestavení nezkracuje ani nepřejmenovává - záznam pádu tak obsahuje skutečné názvy a čísla řádků místo nečitelných zkratek."
  - version: "0.17.0"
    date: 2026-08-14
    notes:
      - "Úplný redesign menu při dlouhém podržení a menu příloh - položky teď mají ikonu ve zvýrazněném kolečku vedle popisku (styl jako u Telegramu/WhatsAppu) místo prostého seznamu textu, destruktivní akce (Blokovat, Opustit skupinu) jsou červené. Menu příloh je teď řada ikon s popiskem místo svislého seznamu."
      - "Oprava: vlastní profilová fotka se v chatu se sebou samým (a v seznamu chatů) nezobrazovala, protože appka čekala, až se fotka doručí sama sobě přes I2P. Fotka je ale už na zařízení - teď se pro vlastní avatar použije rovnou, bez čekání na síť."
  - version: "0.16.0"
    date: 2026-08-14
    notes:
      - "Oprava: soubor APK na stránce vydání 0.15.0 byl poškozený (nahrávání se přerušilo v polovině, 214 MB místo správných cca 358 MB) - appka se kvůli tomu nedala nainstalovat („Při analýze balíčku došlo k chybě“). Toto vydání je čistě opravný přebuild, žádná změna kódu ani funkcí oproti 0.15.0."
  - version: "0.15.0"
    date: 2026-08-14
    notes:
      - "Nová funkce: přílohy (obrázky) i u Mistral AI - přiložený obrázek se pošle jako součást dotazu (podporováno u modelů se schopností vidět obrázky, záleží na vybraném modelu v Nastavení)"
      - Modernizované vyskakovací menu (dlouhé podržení, přílohy) - zaoblená karta se stínem místo ploché obdélníkové plochy, která splývala s pozadím
      - "Kontakt, který jsi ty sám/sama, je teď označený „(Ty)" v seznamu chatů, v otevřeném chatu i v jeho detailu - a nejde ho (ani nedává smysl) blokovat"
      - Jde připnout i Mistral AI v seznamu chatů, ne jen skrýt
  - version: "0.14.0"
    date: 2026-08-14
    notes:
      - "Oprava: psaní sám sobě pořád hlásilo „local loopback denied“. Vlastnost, co to povoluje, se dřív nastavovala jen pro jednotlivé spojení, ale kontrolu dělá samotný router podle svého vlastního nastavení - teď se nastavuje i tam."
      - Každý má teď sám sebe rovnou v kontaktech, bez nutnosti přidávat vlastní Hertz ID ručně - appka to na pozadí vyřídí stejným postupem jako přátelství se skutečnou osobou (opravdová Signal relace, ne zkratka)
      - "Oprava: připojování k I2P se umělo zaseknout na jednom čísle (např. 35 %) a pak skočit dál - mezi jednotlivými kroky appka neměla co hlásit. Teď se ukazatel posouvá plynule po celou dobu, i když se zrovna nic nového nezjistilo."
      - Mistral AI teď umí i tabulky - vykreslí se jako skutečná mřížka, ne jako řádky se svislítky
  - version: "0.13.0"
    date: 2026-08-14
    notes:
      - "Jde napsat sám sobě. I2P ve výchozím stavu odmítá spojení na vlastní adresu („local loopback denied“) - teď se povoluje, takže si můžeš přidat vlastní Hertz ID jako kontakt."
      - "Rozšířené formátování: přeškrtnutí, citace, vodorovné oddělovače, číslované seznamy, odkazy (zobrazí se jejich text) a nadpisy s odstupňovanou velikostí. Formátování teď platí i pro tvoje vlastní zprávy, ne jen pro odpovědi AI - kdo napíše *takhle*, čeká kurzívu."
      - "Nedokončená zpráva zůstane v poli jako koncept. Když odejdeš z chatu (nebo appku vypne systém), rozepsaný text tam po návratu pořád je - zvlášť pro každý chat, skupinu i asistenta."
      - "Průběh připojování k I2P je teď plynulý. Dřív visel na 10 % a pak skočil - jednotlivé fáze (zvlášť otevírání vlastní adresy) trvají dlouho, takže se ukazatel posouvá průběžně místo skoků mezi milníky."
  - version: "0.12.0"
    date: 2026-08-14
    notes:
      - "Formátování odpovědí Mistral AI - tučné, kurzíva, kód, nadpisy a odrážky se teď vykreslí místo toho, aby se zobrazovaly doslovné hvězdičky. Zprávy od lidí zůstávají doslovné (kdo napíše 2 * 3 * 4, myslí hvězdičky)."
      - "Načítání I2P ukazuje skutečný průběh. Dřív viselo na 0 % a pak skočilo na 100, což vypadalo jako zaseknuté - teď má dvě reálné fáze (stahování seznamu routerů, stavba tunelů) s vlastním pruhem a popiskem, co se zrovna děje."
  - version: "0.11.2"
    date: 2026-08-14
    notes:
      - "Oprava: diagnostický řádek sítě zůstával na obrazovce Kontakty i po úspěšném připojení a končil prázdným „reseed:“ - existuje k vysvětlení, proč se připojení nepovedlo, takže se teď po připojení skryje"
      - "Zabezpečení místní sítě: příchozí spojení z Wi-Fi se teď přijme jen tehdy, když se hlásí z adresy, na které daný kontakt skutečně inzeroval sám sebe. U I2P je totožnost odesílatele kryptograficky dokázaná samotnou adresou, u obyčejného spojení v místní síti ne - bez téhle kontroly by se kdokoliv na stejné Wi-Fi mohl vydávat za kontakt a pohlcovat zprávy určené jemu (přečíst by je nikdy nemohl, jsou šifrované klíči skutečného kontaktu)."
      - Úklid kódu - odstraněna všechna varování překladače (zastaralé ikony, chybějící opt-in)
  - version: "0.11.1"
    date: 2026-08-14
    notes:
      - "Zrychlení připojení k I2P. Tunely mají 2 přeskoky místo výchozích 3 - vlastnost, na které u chatu záleží, zůstává (ani protistrana, ani žádný jednotlivý mezilehlý router nezná tvoji IP), ale ubráním jednoho přeskoku se zkrátí jak stavba tunelu, tak zpoždění každé zprávy."
      - Tunely se už neruší při nečinnosti - jejich znovustavění při další zprávě bylo přesně to několikasekundové zaseknutí, kterému se chceme vyhnout
      - "Appka už nepřeposílá tunely cizích uživatelů (telefon je klient, ne infrastruktura) - šetří to baterii, data i výkon pro vlastní provoz"
      - Reseed se teď spouští paralelně s otevíráním vlastní adresy místo až po něm
  - version: "0.11.0"
    date: 2026-08-14
    notes:
      - "Nalezena a opravena skutečná příčina, proč se I2P nikdy nepřipojilo. Reseed (stažení prvního seznamu routerů, bez kterého router nenajde ani jednoho souseda) přijímá výhradně balíčky podepsané formátem su3 a ověřuje je proti certifikátům v adresáři certificates/reseed. Žádná z knihoven, které appka používá, ani jeden takový certifikát neobsahuje - stažení tedy proběhlo, podpis se neověřil, naimportovalo se nula routerů a router zůstal navždy na nule sousedů. Appka teď těch 20 oficiálních certifikátů I2P přibaluje a rozbalí je routeru při startu."
      - "Oprava: QR kód se u vracejících se uživatelů načítal donekonečna - vlastní adresa se publikovala až po připojení k routeru, přestože je odvoditelná z uloženého klíče okamžitě"
      - "Oprava (důležité): Mistral AI o sobě tvrdil, že je lokální model běžící v zařízení a že konverzace s ním je bez serverů a end-to-end šifrovaná. Není - běží v cloudu Mistral AI a je jediné místo v appce, kde obsah zprávy opouští zařízení. Systémový prompt to teď říká výslovně a modelu zakazuje tvrdit opak."
      - "Nová funkce: posílání libovolných souborů (nejen obrázků/videí/hlasovek), v 1:1 i ve skupinách - s původním názvem souboru a otevřením v jiné appce klepnutím"
      - Mistral AI je teď na ploše chatů vidět od začátku (dokud není nastavený, klepnutí vede rovnou do Nastavení)
  - version: "0.10.0"
    date: 2026-08-14
    notes:
      - "Nová funkce: spojení v místní síti úplně bez serverů. Když jsou dvě zařízení na stejné Wi-Fi (nebo hotspotu), najdou se přes mDNS a spojí se přímo podle IP - bez I2P, bez jakéhokoliv bootstrapu, dokonce i úplně bez internetu. Appka to použije automaticky, když to jde, a I2P zůstává pro spojení přes internet."
      - Přílohy (obrázky, videa, hlasovky) teď fungují i ve skupinových chatech - médium se šifruje jedním klíčem, ale ten se každému členovi doručí zvlášť jeho vlastním Signal klíčem, takže pořád neexistuje žádný sdílený skupinový klíč
      - "Oprava: text ve vstupním poli nebyl svisle vycentrovaný (Material TextField si rezervoval místo pro popisek, který tam žádný není) - pole je teď postavené na BasicTextField a text sedí přesně uprostřed"
      - Tlačítko příloh je teď součástí pilulky vstupního pole ve všech chatech
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
