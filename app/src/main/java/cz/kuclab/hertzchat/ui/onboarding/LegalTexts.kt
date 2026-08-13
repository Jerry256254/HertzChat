package cz.kuclab.hertzchat.ui.onboarding

const val TERMS_TEXT = """Podmínky užití Hertz Chat

1. Povaha aplikace
Hertz Chat je peer-to-peer (P2P) komunikační aplikace s koncovým šifrováním.
Neexistuje žádný centrální server - ani provozovaný autorem aplikace, ani
třetí stranou. Zařízení se navzájem nachází a spojují přímo přes veřejnou
síť Tor; zprávy a soubory se přenáší přímo mezi zařízeními a zůstávají
uložené pouze lokálně na zařízeních účastníků konverzace.

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
veřejně dostupný a kdokoliv si může ověřit, jak aplikace pracuje se šifrováním,
daty a síťovým provozem.

5. Bezpečnostní upozornění
Ačkoliv aplikace používá standardní, veřejně auditovatelné kryptografické
postupy (X3DH, Double Ratchet), žádný software není absolutně neprolomitelný.
Uživatel je odpovědný za zabezpečení vlastního zařízení.

6. Změny podmínek
Tyto podmínky se mohou v budoucích verzích aplikace změnit; aktuální znění je
vždy součástí zdrojového kódu v repozitáři projektu."""

const val PRIVACY_TEXT = """Zásady ochrany soukromí Hertz Chat

Co aplikace NEsbírá ani neukládá vůbec nikde:
- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje),
- tvoji skutečnou IP adresu vůči tvým kontaktům (o tu se stará Tor).

Jak tě může někdo najít:
- neexistuje žádný adresář ani seznam "kdo je online" - kontaktovat můžeš
  jen někoho, jehož Hertz ID už znáš (dostal jsi ho mimo appku - QR kód,
  ústně, jinou appkou). Žádost appka pošle přímo na jeho onion adresu.

Kde jsou tvoje data doopravdy uložená:
- výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na
  Android Keystore tohoto konkrétního telefonu."""
