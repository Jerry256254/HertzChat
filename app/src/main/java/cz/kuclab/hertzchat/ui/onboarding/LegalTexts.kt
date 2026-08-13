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

6. Mistral AI asistent (volitelná funkce)
Aplikace nabízí volitelného AI asistenta postaveného na službě Mistral AI.
Jde o jedinou funkci v celé appce, kde obsah zprávy záměrně opouští dvojici
zařízení účastníků konverzace - viz bod 7 zásad ochrany soukromí níže.
Funkce je vypnutá, dokud ji uživatel sám aktivně nezapne a nepotvrdí zvláštní
souhlas v Nastavení; používá výhradně vlastní API klíč(e) uživatele k účtu u
Mistral AI, který si uživatel sám zřizuje a spravuje. Autor aplikace k datům
odeslaným přes tuto funkci nemá přístup a neprovozuje k ní žádnou vlastní
infrastrukturu.

7. Skupinové chaty
Skupina je technicky množina jednotlivých 1:1 šifrovaných spojení - zpráva se
šifruje a posílá zvlášť každému členovi zvlášť jeho vlastním klíčem, appka
nepoužívá žádný sdílený "skupinový klíč". Členem skupiny se může stát jen
někdo, koho zakladatel (nebo jiný člen) už má jako důvěryhodný kontakt;
appka si mezi členy, kteří se ještě neznají, automaticky vyžádá vzájemné
přátelství, aby si mohli v rámci skupiny navzájem posílat zprávy.

8. @Mistral v běžném chatu nebo skupině
Kdokoliv v konverzaci může napsat "@Mistral [počet] [dotaz]" - appka pak
pošle posledních N zpráv z té konverzace (jen od účastníků, kteří to ve
svém Nastavení nezakázali) spolu s dotazem na Mistral AI a jeho odpověď
vloží zpět do konverzace jako zprávu od AI, viditelnou všem účastníkům.
Tohle funguje jen tehdy, když to má ve svém Nastavení povolené aspoň jeden
další účastník kromě toho, kdo @Mistral použil - tvoje vlastní volba to
neovlivňuje, jen volba ostatních.

9. Změny podmínek
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
  Android Keystore tohoto konkrétního telefonu.

7. Mistral AI asistent - jediná výjimka z "nikdo to nemůže přečíst"
Pokud si v Nastavení zapneš volitelného AI asistenta, text, který mu napíšeš
(a jeho odpověď), se z tvého zařízení odesílá přímo na servery Mistral AI -
pomocí tvého vlastního API klíče, bez jakéhokoliv zprostředkování ze strany
KucLab. Je to jediné místo v celé appce, kde obsah zprávy opouští zařízení
v čitelné podobě. Jakmile data dorazí k Mistral AI, řídí se jejich vlastními
zásadami ochrany soukromí, ne těmito. Funkce je ve výchozím stavu vypnutá a
kdykoliv ji můžeš v Nastavení znovu vypnout - v tu chvíli appka žádná další
data Mistral AI neposílá.

8. @Mistral v běžném chatu nebo skupině a tvoje volba "Povolit ostatním @Mistral u mých zpráv"
Tohle nastavení (v Nastavení → Soukromí a síť, výchozí stav zapnuto) řídí,
jestli smí AI vidět a použít tvoje zprávy jako kontext, když v konverzaci
s tebou někdo napíše @Mistral. Tvoje volba se automaticky pošle (stejným
šifrovaným kanálem jako běžné zprávy) všem tvým kontaktům, aby ji jejich
appka mohla rovnou respektovat. Vypneš-li to, tvoje zprávy se z kontextu
posílaného Mistral AI vynechají.

Za bezpečnost a nastavení vlastního Mistral AI
účtu (včetně API klíčů) odpovídá výhradně uživatel."""

const val MISTRAL_CONSENT_TEXT = """Tohle je jediné místo v appce, kde obsah zprávy opouští tvoje zařízení v čitelné podobě.

Když tuhle funkci zapneš, text, který napíšeš AI asistentovi (a jeho odpověď), se odešle přímo na servery Mistral AI - pomocí tvého vlastního API klíče, bez jakéhokoliv zprostředkování ze strany KucLab. Jakmile data dorazí k Mistral AI, řídí se jejich vlastními zásadami ochrany soukromí.

Za API klíč(e) a účet u Mistral AI odpovídáš ty sám. Funkci můžeš kdykoliv zase vypnout v Nastavení - appka pak žádná další data Mistral AI neposílá."""
