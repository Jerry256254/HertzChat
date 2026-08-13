package cz.kuclab.hertzchat.ui.onboarding

const val TERMS_TEXT = """Podmínky užití Hertz Chat

1. Povaha aplikace
Hertz Chat je peer-to-peer (P2P) komunikační aplikace s koncovým šifrováním.
Neexistuje žádný centrální server, který by ukládal, četl nebo zprostředkovával
tvoje zprávy, kontakty či média. Zprávy a soubory se přenáší přímo mezi
zařízeními a zůstávají uložené pouze lokálně na zařízeních účastníků konverzace.

2. Identita a účet
Aplikace nevyžaduje registraci přes telefonní číslo, e-mail ani jinou osobní
identifikaci. Tvoje identita je tvořena kryptografickým klíčem vygenerovaným
a uloženým výhradně na tvém zařízení.

3. Odpovědnost
Autor aplikace neprovozuje žádnou infrastrukturu pro doručování, ukládání ani
moderování obsahu, který si mezi sebou uživatelé vyměňují, a nemá k tomuto
obsahu přístup. Za obsah zpráv a médií odesílaných prostřednictvím aplikace
odpovídá výhradně uživatel, který je odeslal.

4. Otevřený zdrojový kód
Hertz Chat je open source software šířený pod licencí MIT. Zdrojový kód je
veřejně dostupný a kdokoliv si může ověřit, jak aplikace pracuje se šifrováním
a daty, nebo si spustit vlastní instanci volitelného signalizačního serveru.

5. Bezpečnostní upozornění
Ačkoliv aplikace používá standardní, veřejně auditovatelné kryptografické
postupy (X3DH, Double Ratchet), žádný software není absolutně neprolomitelný.
Uživatel je odpovědný za zabezpečení vlastního zařízení.

6. Změny podmínek
Tyto podmínky se mohou v budoucích verzích aplikace změnit; aktuální znění je
vždy součástí zdrojového kódu v repozitáři projektu."""

const val PRIVACY_TEXT = """Zásady ochrany soukromí Hertz Chat

Co aplikace NEsbírá ani neukládá na žádném serveru:
- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje).

Co dočasně prochází signalizačním serverem (a NIKDY se tam neukládá):
- tvoje pseudonymní ID (otisk veřejného klíče) a zvolená přezdívka, po dobu,
  kdy jsi v aplikaci online, aby tě mohli ostatní najít,
  - toto lze v Nastavení → Soukromí kdykoliv vypnout,
- technické údaje pro navázání přímého P2P spojení (WebRTC nabídky/odpovědi
  a ICE kandidáti) - server je jen slepě přeposílá mezi dvěma zařízeními.

Kde jsou tvoje data doopravdy uložená:
- výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na
  Android Keystore tohoto konkrétního telefonu."""
