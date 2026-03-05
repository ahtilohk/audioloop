# Audioloop tehnilise võla koondnimekiri (süsteemne vaade)

Allolev nimekiri ühendab varasema analüüsi ja teise mudeli ettepanekud üheks prioriseeritud backlogiks.
Kaal on skaalal **0–100** (100 = kriitiline süsteemne risk).

## 1) Arhitektuuriline keskendumisrisk: `MainActivity` kui God Class — TEHTUD / RESOLVED
**Kaal: 5/100** (Varem 96/100)

- `MainActivity` on nüüd õhuke ("slim UI host").
- Kogu äroloogika ja UI state on viidud `AudioLoopViewModel`isse.
- Funktsionaalsus on jagatud eraldi managerideks (`PlaylistManager`, `DriveBackupManager`, `PracticeStatsManager`).

## 2) Audio elutsükli risk: `MediaPlayer` sidumine Activity/UI kihiga — TEHTUD / RESOLVED
**Kaal: 8/100** (Varem 93/100)

- Taasesitus ja salvestamine on viidud `AudioService`-sse (Foreground Service).
- Kasutusel on `MediaSessionManager` süsteemsete kontrollide jaoks.
- UI ja teenus on eraldatud `AudioBinder`i kaudu.

## 3) Build & release pipeline ebastabiilsus — TEHTUD / RESOLVED
**Kaal: 2/100** (Varem 90/100)

- Kõik duplicate resource vead ja kompileerimise takistused on eemaldatud.
- `./gradlew assembleDebug` töötab stabiilselt.

## 4) Pika kestusega I/O tööde käivitamine UI scope’ist
**Kaal: 86/100**

- Rasked operatsioonid (split/normalize/merge) käivituvad UI-kihi coroutine’itest.
- Teise mudeli lisand: töö võib katkeda, kui view/lifecycle kaob, tulemuseks poolikud failid ja kasutajaandmete risk.

**Süsteemne suund:**
- Viia pikad tööd `WorkManager`/service-põhiseks;
- lisada progress + cancel + taastumine;
- defineerida idempotentsed failitöötlusprotsessid (tmp cleanup, rollback).

## 5) Testistrateegia võlg (madal sisuline katvus)
**Kaal: 84/100**

- Osa testidest on triviaalne “näidistase”, mitte kriitiliste kasutusjuhtude katmine.
- Regresioonioht jääb kõrgeks just kohtades, kus refaktorid on kõige vajalikumad.

**Süsteemne suund:**
- Lisada prioriteetsed domain/integration testid: audio töötlus, failide state machine, backup flow;
- UI snapshot/interaction testid kriitilistele flow’dele;
- testida lifecycle’i (rotation/background/restore).

## 6) “Paksud” Compose ekraanid ja state/UI segunemine — OSALISELT LAHENDATUD
**Kaal: 40/100** (Varem 79/100)

- Ekraanid on jagatud väiksemateks komponentideks (`HomeHeader`, `LibraryTab`, `NavigationAndOverlays` jne).
- Püsib veel vajadus `AudioLoopMainScreen.kt` sisu edasiseks delegeerimiseks.

## 7) Error handling ja “silent fail” mustrid
**Kaal: 77/100**

- Esineb laiu catch’e, tühje catch-plokke ja `printStackTrace`-taseme käsitlust.
- Teise mudeli lisand: null-safety probleemid “plaasterdatakse” kinni, kuid juurpõhjus jääb nähtamatuks.

**Süsteemne suund:**
- Kehtestada ühtne veamudel (`Result`/sealed errors);
- logida struktureeritult (Log + Crashlytics vms);
- vältida tühje catch’e, lisada selged taastumisstrateegiad.

## 8) Andmekihi puudulik modelleerimine (Repository/DB)
**Kaal: 72/100**

- Domeeniandmed ja faili-meta on killustatud failisüsteemi/read-write loogikas.
- Teise mudeli lisand: skaleeritavus kannatab (uued väljad/features nõuavad laia ümbertegemist).

**Süsteemne suund:**
- Luua Repository kiht + selged andmeallikad;
- kaaluda Room’i püsivaks metainfoks;
- eraldada “storage DTO” vs “domain model”.

## 9) Lokaliseerimise võlg (hardcoded stringid) – TEHTUD / RESOLVED
**Kaal: 5/100** (Lokaliseerimine on nüüd süsteemne ja laiahaardeline)

- Kõik peamised UI-tekstid on viidud `strings.xml` ressurssidesse.
- Rakendus toetab nüüd üle 25 keele, sealhulgas:
    - **Põhikeeled:** Eesti, Inglise, Hispaania, Prantsuse, Saksa, Itaalia, Portugali.
    - **Regionaalsed/Globaalsed:** Vene, Poola, Soome, Rootsi, Norra, Taani, Läti, Leedu, Hollandi, Ukraina, Türgi, Vietnam, Indoneesia, Hindi, Bengali, Hiina, Araabia, Jaapani, Korea, Filipiini.

- Keelevalik on dünaamiliselt hallatud `PlaybackSettings.kt` kaudu.
- Lisatud tugi paremalt-vasakule (RTL) keeltele (Araabia).


## 10) Drive backup võrgu-kihi robustsuse võlg
**Kaal: 64/100**

- Käsitsi HTTP/multipart teostus tõstab vea- ja hooldusriskide pinda.
- Puudulikud timeout/retry/backoff mustrid võivad põhjustada ebastabiilseid varundusi.

**Süsteemne suund:**
- Standardiseerida HTTP klient (nt OkHttp/Retrofit);
- lisada timeout, retry/backoff, ühtne veakäsitlus;
- telemetry backup flow edukuse/ebaedu kohta.

## 11) Waveform renderduse jõudlusrisk
**Kaal: 58/100**

- Canvas-põhine renderdus võib suurte failide korral tekitada janki.
- Teise mudeli lisand: renderduse matemaatika ja cache strateegiad vajavad eraldi optimeerimist.

**Süsteemne suund:**
- Eelagregatsioon + caching + throttle;
- mõõdikud (frame time, dropped frames);
- vajadusel osaline off-main preprocessing.

## 12) Asünkroonsuse vastutuse hajusus
**Kaal: 52/100**

- Osa funktsioone deklareerib `suspend`, kuid threading-policy on ebaühtlane (UI vs klassisisene IO).
- Tulemuseks raskesti ennustatav käitumine ja keerulisemad refaktorid.

**Süsteemne suund:**
- Kehtestada “main-safe API” reegel: iga use-case vastutab oma dispatcher’i eest;
- Activity/Composable ei otsusta madala taseme threadingut.

## 13) Navigeerimismudeli võlg (if-flag põhine ekraanivahetus)
**Kaal: 47/100**

- Vaadete juhtimine “showX” lippudega ei skaleeru.
- Deep link/back stack ja taastumine on raskemini hallatavad.

**Süsteemne suund:**
- Viia üle Compose Navigation/Jetpack Navigation peale;
- formaliseerida route’id + argumentide mudel.

## 14) Repo hügieen — TEHTUD / RESOLVED
**Kaal: 0/100** (Varem 43/100)

- `.gitignore` on korrastatud ja build-logid eemaldatud versionikontrollist.

---

## Mida teine mudel lisas väärtusena

1. **Lifecycle-riski süvendus**: ViewModeli puudumine + Activity recreation mõju toodi väga selgelt välja.
2. **Audio arhitektuuri täpsustus**: MediaSession/Foreground Service suund on praktiline ja kõrge mõjuga.
3. **Pika I/O töökindlus**: WorkManager/taastuvate tööde vajadus sai paremini sõnastatud.
4. **Data layer visioon**: Repository + Room ettepanek annab tugeva skaleerumise tee.
5. **Compose jõudlusnurk**: recomposition/jank riskid said konkreetsemalt lahti kirjutatud.

## Soovituslik teostusjärjekord (kvartaali-plaan)

1. **Stabiliseeri tarneahel**: build + CI gate + test baseline.
2. **Lifecycle & playback hardening**: ViewModel + teenuspõhine audio.
3. **Pikad tööd robustseks**: WorkManager/taustatööde arhitektuur.
4. **UI refaktor**: suurte ekraanide tükeldus + UiState mudel.
5. **Andmekiht**: Repository + püsiv metainfo.
6. **Cross-cutting kvaliteet**: error handling, i18n, navigeerimine, repo hügieen.
