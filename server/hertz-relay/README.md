# hertz-relay

Vlastní relay server pro Hertz Chat. Minimalistický Nostr relay, který umí
jen jediné: přeposílat *ephemeral* eventy (kind 20000–29999) právě
připojeným odběratelům.

## Proč vlastní relay

Vestavěné veřejné relay servery v appce stačí většině lidí. Vlastní relay
dává:

- **Nulovou závislost na cizí infrastruktuře** – tvoje zprávy tečou jen přes
  tvůj server.
- **Garanci zero-storage** – tenhle kód fyzicky neumí nic uložit ani
  zalogovat (žádná databáze, žádný zápis na disk, žádné logy zpráv/IP/časů).
  Jen hodinové souhrnné čítače na stdout.
- **Rychlost** – relay blízko tobě = latence v milisekundách.

## Spuštění

```sh
cd server/hertz-relay
npm install
PORT=8080 node relay.js
```

Za TLS terminátor (Caddy/nginx) pro `wss://`. Příklad Caddy:

```
relay.tvujedomena.cz {
    reverse_proxy 127.0.0.1:8080
}
```

V appce pak Nastavení → Síť → vyplnit `wss://relay.tvujedomena.cz` → Uložit.
Prázdné pole = zpět vestavěné servery.

## Kam nasadit zdarma

- **Oracle Cloud Always Free** – 2 virtuální servery napořád zdarma, bohatě
  stačí (relay žere jednotky MB RAM).
- Jakékoliv VPS / Raspberry Pi doma.

## Bezpečnostní model

I vlastní relay vidí jen zašifrované bloby: obsah šifruje Signal Protokol
end-to-end a relay neumí určit ani kdo komu píše (ephemeral klíče odesílatele
+ párové routovací tagy). Relay neukládá nic – ani IP adresy (pouze RAM
počítadla proti zahlcení, vypnutelná přes `RATE_LIMIT=0`), ani časy, ani obsah.
