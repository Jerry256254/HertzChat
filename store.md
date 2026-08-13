---
schema_version: 1
app_name: Hertz Chat
package_id: cz.kuclab.hertzchat
version_name: "0.2.0"
version_code: 2
last_updated: 2026-08-13
license: MIT
category: Komunikace
short_description: >-
  Peer-to-peer, end-to-end šifrovaný chat bez serveru a registrace.
full_description: |-
  Hertz Chat je chatovací aplikace bez centrálního serveru: text, obrázky,
  videa i hlasové zprávy jdou vždy přímo mezi zařízeními přes WebRTC a jsou
  šifrované Signal Protokolem (X3DH + Double Ratchet), takže je nikdy nikdo
  jiný nemůže přečíst - ani autor aplikace.

  Identita je čistě zařízení - žádné telefonní číslo, e-mail ani registrace.
  Kontakty se hledají mezi právě online uživateli, žádosti o přátelství se
  přijímají/odmítají a identitu lze přenést na nové zařízení naskenováním QR
  kódu.
tags:
  - messaging
  - encryption
  - p2p
  - privacy
  - webrtc
repository_url: https://github.com/Jerry256254/HertzChat
download_url: https://github.com/Jerry256254/HertzChat/releases/latest
logo: store/logo.png
screenshots: []
changelog:
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

Peer-to-peer, end-to-end šifrovaný chat bez serveru a registrace.

## Popis

Hertz Chat je chatovací aplikace bez centrálního serveru: text, obrázky,
videa i hlasové zprávy jdou vždy přímo mezi zařízeními přes WebRTC a jsou
šifrované Signal Protokolem (X3DH + Double Ratchet), takže je nikdy nikdo
jiný nemůže přečíst - ani autor aplikace.

Identita je čistě zařízení - žádné telefonní číslo, e-mail ani registrace.
Kontakty se hledají mezi právě online uživateli, žádosti o přátelství se
přijímají/odmítají a identitu lze přenést na nové zařízení naskenováním QR
kódu.

## Logo

![Hertz Chat logo](store/logo.png)

## Odkazy

- Zdrojový kód: <https://github.com/Jerry256254/HertzChat>
- Stažení nejnovější verze: <https://github.com/Jerry256254/HertzChat/releases/latest>
- Licence: [MIT](LICENSE)
