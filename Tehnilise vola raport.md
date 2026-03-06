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

## 4) Pika kestusega I/O tööde käivitamine UI scope’ist — TEHTUD / RESOLVED
**Kaal: 5/100**

- Rasked operatsioonid (split/normalize/merge/autotrim) on viidud `WorkManager` peale.
- Lisatud `AudioProcessingWorker`, mis tagab töö jätkumise ka taustal.
- Idempotentsed failitöötlusprotsessid koos korrektse tmp cleanup-iga.

## 5) Testistrateegia võlg (madal sisuline katvus) — TEHTUD / RESOLVED
**Kaal: 5/100** (Varem 84/100)

- Loodud testimise infrastruktuur: **MockK**, **Kotlin Coroutines Test** ja **org.json** tugi JVM testides.
- Lisatud sisulised unit testid kriitilisele domeniloogikale:
    - `PracticeStatsManagerTest`: sessioonide logimine, streak-loogika, eesmärkide haldus.
    - `UtilsTest`: aja- ja kestuse vormindamise loogika.
    - `PlaylistManagerTest`: Lahendatud ja valmis (resolve ja duration loogika täielikult kaetud).
- Keskne UI state loogika on lahti kirjutatud ja testitud `UiStateTest.kt` all.
- ViewModel on Android frameworkiga liialt seotud, mistõttu puhtaid JVM-teste seal praegu välditakse; põhiosa loogikat on niigi testitud Manager'ide kaudu.

## 6) “Paksud” Compose ekraanid ja state/UI segunemine — OSALISELT LAHENDATUD
**Kaal: 40/100** (Varem 79/100)

- Ekraanid on jagatud väiksemateks komponentideks (`HomeHeader`, `LibraryTab`, `NavigationAndOverlays` jne).
- Püsib veel vajadus `AudioLoopMainScreen.kt` sisu edasiseks delegeerimiseks.

## 7) Error handling ja “silent fail” mustrid — TEHTUD / RESOLVED
**Kaal: 5/100**

- Loodud ühtne `AudioResult<T>` mudel (Success, Error, Loading).
- Repository meetodid tagastavad nüüd struktureeritud tulemusi.
- ViewModel käsitleb vigu ja kuvab kasutajale sisulisi Snackbar teavitusi eelmise vaikuse asemel.

## 8) Andmekihi puudulik modelleerimine (Repository/DB) — TEHTUD / RESOLVED
**Kaal: 5/100**

- Repository kiht on nüüd keskne "Single Source of Truth".
- Room andmebaas toimib failide registrina, mida sünkroonitakse automaatselt kettaga (`discoverRecordings`).
- `AudioMetadataHelper` ja sidecar failide (.note, .wave) haldus on viidud andmekihti.
- ViewModel on puhastatud madala taseme I/O loogikast.

## 9) Lokaliseerimise võlg (hardcoded stringid) – TEHTUD / RESOLVED
**Kaal: 5/100** (Lokaliseerimine on nüüd süsteemne ja laiahaardeline)

- Kõik peamised UI-tekstid on viidud `strings.xml` ressurssidesse.
- Rakendus toetab nüüd üle 25 keele, sealhulgas:
    - **Põhikeeled:** Eesti, Inglise, Hispaania, Prantsuse, Saksa, Itaalia, Portugali.
    - **Regionaalsed/Globaalsed:** Vene, Poola, Soome, Rootsi, Norra, Taani, Läti, Leedu, Hollandi, Ukraina, Türgi, Vietnam, Indoneesia, Hindi, Bengali, Hiina, Araabia, Jaapani, Korea, Filipiini.

- Keelevalik on dünaamiliselt hallatud `PlaybackSettings.kt` kaudu.
- Lisatud tugi paremalt-vasakule (RTL) keeltele (Araabia).


## 10) Drive backup võrgu-kihi robustsuse võlg – TEHTUD / RESOLVED
**Kaal: 2/100**

- Juurutatud OkHttp koos NetworkHelperiga, mis pakub eksponentsiaalset backoff retry't ja timeout'e.
- DriveBackupManager on refaktoreeritud kasutama OkHttp MultipartBody't ja ühtset veahaldust.
- Lisatud baidi-tasemel progressi jälgimine nii üles- kui allalaadimiseks.

## 11) Waveform renderduse jõudlusrisk – TEHTUD / RESOLVED
**Kaal: 1/100**

- Waveformi joonistamine on optimeeritud kahe-etapiliseks (background bars + clipped progress bars).
- Värvide arvutamine per tulp igal kaadril on asendatud efektiivsema `clipRect` lähenemisega, mis vähendab tunduvalt Canvas operatsioone.

## 12) Asünkroonsuse vastutuse hajusus — TEHTUD / RESOLVED
**Kaal: 5/100** (Varem 52/100)

- Kehtestatud “main-safe API” reegel: Repository, WaveformGenerator ja AudioMetadataHelper vastutavad ise oma dispatcher’i eest (Dispatchers.IO).
- ViewModeli launch-id on nüüd main-safe ja ei nõua käsitsi dispatcher'i määramist UI kihis.
- Eemaldatud `runBlocking` Main-threadil ja asendatud asünkroonsete lahendustega.

## 13) Navigeerimismudeli võlg (if-flag põhine ekraanivahetus) — TEHTUD / RESOLVED
**Kaal: 4/100** (Varem 47/100)

- Rakendus kasutab nüüd **Jetpack Compose Navigationit** karkassina.
- Route'id (Library, Record, Coach, Settings, PlaylistView) on formaliseeritud ja juhitud NavControllingi kaudu.
- See võimaldab edaspidi lihtsat deeplinkide tuge ja paremat back-stack haldust.

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
