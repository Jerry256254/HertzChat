package cz.kuclab.hertzchat.ui.onboarding

const val TERMS_TEXT = """Podmínky užití Hertz Chat

1. Povaha aplikace
Hertz Chat je peer-to-peer (P2P) komunikační aplikace s koncovým šifrováním.
Neexistuje žádný centrální server - ani provozovaný autorem aplikace, ani
třetí stranou. Zařízení se navzájem nachází a spojují přímo přes veřejnou
síť I2P; zprávy a soubory se přenáší přímo mezi zařízeními a zůstávají
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

6. Hertz AI asistent
Aplikace nabízí AI asistenta Hertz - webovou službu KucLabu (kuclab.org/hertz)
zabudovanou přímo v appce jako webové zobrazení. Jde o jedinou funkci v celé
appce, kde obsah zprávy záměrně opouští tvoje zařízení - viz bod 7 zásad
ochrany soukromí níže. Asistent vyžaduje přihlášení tvým KucLab účtem přímo
v appce; co webová služba s daty dělá, řídí podmínky KucLabu, ne tyto.

7. Skupinové chaty
Skupina je technicky množina jednotlivých 1:1 šifrovaných spojení - zpráva se
šifruje a posílá zvlášť každému členovi zvlášť jeho vlastním klíčem, appka
nepoužívá žádný sdílený "skupinový klíč". Členem skupiny se může stát jen
někdo, koho zakladatel (nebo jiný člen) už má jako důvěryhodný kontakt;
appka si mezi členy, kteří se ještě neznají, automaticky vyžádá vzájemné
přátelství, aby si mohli v rámci skupiny navzájem posílat zprávy.

8. Změny podmínek
Tyto podmínky se mohou v budoucích verzích aplikace změnit; aktuální znění je
vždy součástí zdrojového kódu v repozitáři projektu."""

const val PRIVACY_TEXT = """Zásady ochrany soukromí Hertz Chat

Co aplikace NEsbírá ani neukládá vůbec nikde:
- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje),
- tvoji skutečnou IP adresu vůči tvým kontaktům (o tu se stará I2P).

Jak tě může někdo najít:
- neexistuje žádný adresář ani seznam "kdo je online" - kontaktovat můžeš
  jen někoho, jehož Hertz ID už znáš (dostal jsi ho mimo appku - QR kód,
  ústně, jinou appkou). Žádost appka pošle přímo na jeho I2P adresu.

Kde jsou tvoje data doopravdy uložená:
- výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na
  Android Keystore tohoto konkrétního telefonu.

7. Hertz AI asistent - jediná výjimka z "nikdo to nemůže přečíst"
Pokud používáš AI asistenta Hertz (obrazovka Asistent), text, který mu napíšeš
(a jeho odpověď), se z tvého zařízení odesílá na servery KucLabu (kuclab.org),
kde webová služba běží. Je to jediné místo v celé appce, kde obsah zprávy
opouští zařízení v čitelné podobě. Jakmile data dorazí ke KucLabu, řídí se
jejich vlastními podmínkami a zásadami ochrany soukromí, ne těmito. Chaty
s lidmi a skupiny tím nejsou nijak dotčené - ty zůstávají end-to-end
šifrované."""
