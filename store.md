---
schema_version: 1
app_name: Hertz Chat
package_id: cz.kuclab.hertzchat
version_name: "0.3.0"
version_code: 5
last_updated: 2026-08-13
license: MIT
category: Komunikace
short_description: >-
  Peer-to-peer, end-to-end šifrovaný chat bez serveru, přes síť Tor.
full_description: |-
  Hertz Chat je chatovací aplikace, za kterou nestojí žádný server - ani náš,
  ani cizí. Dvě zařízení se najdou a spojí přímo přes veřejnou síť Tor
  (každé si publikuje vlastní onion adresu), zdarma, bez registrace, bez
  jakékoliv firmy uprostřed. Text, obrázky, videa i hlasové zprávy jsou navíc
  šifrované Signal Protokolem (X3DH + Double Ratchet), takže je nikdy nikdo
  jiný nemůže přečíst - ani autor aplikace.

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
  - tor
repository_url: https://github.com/Jerry256254/HertzChat
download_url: https://github.com/Jerry256254/HertzChat/releases/latest
logo: store/logo.png
screenshots: []
changelog:
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

Peer-to-peer, end-to-end šifrovaný chat bez serveru, přes síť Tor.

## Popis

Hertz Chat je chatovací aplikace, za kterou nestojí žádný server - ani náš,
ani cizí. Dvě zařízení se najdou a spojí přímo přes veřejnou síť Tor, zdarma,
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
