# AudioLoop: Mass Turu Launch & Rollout Playbook

See dokument sätestab reeglid ja sammud AudioLoopi edukaks ja turvaliseks lansseerimiseks Google Play poes. Eesmärk on minimeerida riske reitingule ja tagada maksete ning backup-funktsionaalsuse töökindlus.

## 1. Staged Rollout (Järkjärguline väljalase)

Massituru puhul EI TOHI väljalaset teha korraga 100% kasutajatele. Kasutame järgmist graafikut:

| Faas | % Kasutajaid | Kestus | Fookus |
| :--- | :--- | :--- | :--- |
| **0. Internal Testing** | 0% | Pidev | Arendaja ja QA seadmed. |
| **1. Alpha/Beta** | ~50-100 testi | 24-48h | Sõbrad, pere, usaldusväärsed testijad. |
| **2. Production 10%** | 10% | 24h | Crashlyticsi monitooring. Kas esineb uusi ANR-e? |
| **3. Production 25%** | 25% | 24h | Billing konversiooni jälgimine. |
| **4. Production 50%** | 50% | 48h | Kasutajate tagasiside ja Store review'd. |
| **5. Production 100%** | 100% | - | Täielik avalikustamine. |

## 2. Kriitilised mõõdikud (Health Checks)

Enne järgmisesse faasi liikumist peavad olema täidetud:
- **Crash-free users:** > 99.5%
- **ANR rate:** < 0.47% (Google Play threshold)
- **Billing success rate:** > 95% (kui keegi proovib osta, ei tohi tekkida tehnilisi vigu)
- **Backup success rate:** Drive'i logid peavad olema puhtad.

## 3. Rollback Playbook (Hädapeatuse plaan)

Kui avastatakse kriitiline viga (crash loop, andmekadu):

1. **Peata rollout kohe**: Google Play Console -> Release overview -> Halt rollout.
2. **Hinda olukorda**:
   - Kui viga on vaid 10% seas: Paranda ja tee uus väljalase.
   - Kui viga on 100% peal: Tee "Hotfix" kohe (prioriteet #1).
3. **Kommunikatsioon**: Kui viga mõjutab makseviise, uuenda Store listingu kirjeldust või sotsiaalmeediat informatsiooniga, et tegeled parandusega.

## 4. Maksete (Billing) testimine enne lansseerimist

1. Kontrolli, et `BillingManager` logib `Acknowledge` õnnestumist.
2. Kasuta Google Play **License Testers** seadeid, et testida reaalseid ostuvooge ilma päris rahata.
3. Proovi "Restore Purchase" funktsiooni pärast rakenduse uuesti installimist.

## 5. Kommunikatsioonistrateegia

- **Uuenduste sildid**: Iga uuendusega peab kaasas olema selge "What's new" (Wave 1 keeltes).
- **Review management**: Vasta kõikidele 1- ja 2-tärni arvustustele 24h jooksul.
