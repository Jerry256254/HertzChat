# Zásady ochrany soukromí Hertz Chat

## Co aplikace NEsbírá ani neukládá vůbec nikde

- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje),
- tvoji skutečnou IP adresu vůči tvým kontaktům (kontakty vidí jen tvůj
  anonymní relay klíč).

Zprávy a hovory mezi zařízeními přenášejí rychlé relay servery (veřejné
zdarma, nebo vlastní - viz `server/hertz-relay` v repozitáři), které fungují
jen jako slepá přepážka: přeposílají zašifrované bloby právě připojeným
zařízením a nic neukládají - žádné zprávy, žádné časy, žádné IP adresy,
žádné logy. Každá zpráva navíc nese čerstvý jednorázový klíč odesílatele a
anonymní párový tag, takže server neumí určit ani kdo komu píše.

## Jak tě může někdo najít

Neexistuje žádný adresář ani seznam "kdo je online" - kontaktovat můžeš jen
někoho, jehož Hertz ID už znáš (dostal jsi ho od něj mimo appku - QR kód,
ústně, jinou appkou). To ID v sobě nese i jeho anonymní relay klíč. Žádost
o přátelství appka zapečetí jeho veřejným klíčem identity a pošle přes relay
servery - nikam jinam.

## Co vidí relay server po cestě

Jen neprůhledné zašifrované bloby adresované anonymním klíčům - nikdy obsah
zpráv (ten chrání end-to-end šifrování Signal Protokolem s dopřednou
bezpečností a postkvantovým Kyber klíčem), nikdy kdo komu píše, a nic
z toho neukládá. Vlastní relay (`server/hertz-relay`) to garantuje kódem:
fyzicky neumí nic uložit ani zalogovat.

## Kde jsou tvoje data doopravdy uložená

Výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na Android
Keystore tohoto konkrétního telefonu (SQLCipher). Při přechodu na nové
zařízení se přenáší jen kryptografická identita a relay klíč (QR kód) -
historie zpráv a média zůstávají na původním zařízení, protože nikdy
neopustila jeho úložiště.

## Kontrola nad dosažitelností

V Nastavení → Soukromí lze kdykoliv vypnout "Být dosažitelný" - appka pak
odpojí relay servery a službu na pozadí a nikdo (ani stávající kontakty) tě
nemůže najít ani ti poslat zprávu, dokud to znovu nezapneš. Zprávy, které
jsi mezitím poslal ty a čekají na doručení, appka dál zkouší odeslat,
jakmile budeš mít internet znovu zapnutý.
