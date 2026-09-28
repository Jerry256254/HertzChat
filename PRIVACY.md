# Zásady ochrany soukromí Hertz Chat

## Co aplikace NEsbírá ani neukládá vůbec nikde

- obsah zpráv, hlasových zpráv, obrázků ani videí,
- seznam tvých kontaktů,
- tvoje jméno, telefonní číslo ani e-mail (aplikace je nevyžaduje),
- tvoji skutečnou IP adresu vůči tvým kontaktům (o tu se stará Tor).

Neexistuje žádný server - ani náš, ani cizí, ani dočasný - kterým by tvoje
zprávy nebo média kdy procházely. Dvě zařízení se najdou a spojí přímo přes
veřejnou síť [Tor](https://www.torproject.org/): každé zařízení si publikuje
vlastní "onion" (skrytou) službu a druhá strana se k ní připojí napřímo.

## Jak tě může někdo najít

Neexistuje žádný adresář ani seznam "kdo je online" - stejně jako u Tor
adresy samotné, kontaktovat můžeš jen někoho, jehož Hertz ID už znáš (dostal
jsi ho od něj mimo appku - QR kód, ústně, jinou appkou). To ID v sobě nese
i jeho onion adresu. Žádost o přátelství appka pošle přímo na tuto adresu -
nikam jinam.

## Co vidí síť Tor po cestě

Uzly v síti Tor (které appka ani nikdo jiný neprovozuje - jsou to tisíce
dobrovolnických serverů po celém světě) vidí jen zašifrovaná data mezi
jednotlivými přeskoky, nikdy ne oba konce spojení najednou ani obsah zpráv -
to je celý smysl cibulového směrování. Nad tím vším navíc leží ještě
end-to-end šifrování Signal Protokolem, takže i kdyby něco selhalo na úrovni
Toru, obsah zůstává čitelný jen tobě a příjemci.

## Kde jsou tvoje data doopravdy uložená

Výhradně na tvém zařízení, v databázi zašifrované klíčem vázaným na Android
Keystore tohoto konkrétního telefonu (SQLCipher). Při přechodu na nové
zařízení se přenáší jen kryptografická identita a onion adresa (QR kód) -
historie zpráv a média zůstávají na původním zařízení, protože nikdy
neopustila jeho úložiště.

## Kontrola nad dosažitelností

V Nastavení → Soukromí lze kdykoliv vypnout "Být dosažitelný" - appka pak
vypne Tor a onion službu a nikdo (ani stávající kontakty) tě nemůže najít
ani ti poslat zprávu, dokud to znovu nezapneš. Zprávy, které jsi mezitím
poslal ty a čekají na doručení, appka dál zkouší odeslat, jakmile budeš mít
internet znovu zapnutý.
