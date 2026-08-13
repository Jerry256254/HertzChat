# Zásady ochrany soukromí Hertz Chat

## Co aplikace NEsbírá ani neukládá na žádném serveru

- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje).

## Co dočasně prochází signalizačním serverem (a nikdy se tam neukládá)

- tvoje pseudonymní ID (otisk veřejného klíče) a zvolená přezdívka, po dobu,
  kdy jsi v aplikaci online, aby tě mohli ostatní najít - lze v
  Nastavení → Soukromí kdykoliv vypnout,
- technické údaje pro navázání přímého P2P spojení (WebRTC nabídky/odpovědi a
  ICE kandidáti) - server (viz `/signaling-relay`) je jen slepě přeposílá mezi
  dvěma zařízeními a nic z toho nezapisuje na disk.

Pokud přímé P2P spojení kvůli přísnému NATu/firewallu nejde navázat, provoz se
přesměruje přes záložní TURN relay - ten ale stejně jako signalizační server
vidí jen již zašifrovaná data, nikdy obsah zprávy.

## Kde jsou tvoje data doopravdy uložená

Výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na Android
Keystore tohoto konkrétního telefonu (SQLCipher). Při přechodu na nové
zařízení se přenáší jen kryptografická identita (QR kód) - historie zpráv a
média zůstávají na původním zařízení, protože nikdy neopustila jeho úložiště.

## Kontrola nad viditelností

V Nastavení → Soukromí lze kdykoliv vypnout "Být viditelný online" - pak tě
signalizační server vůbec nezaregistruje a nikdo (ani stávající kontakty) tě
nemůže najít ani ti poslat zprávu, dokud to znovu nezapneš.
