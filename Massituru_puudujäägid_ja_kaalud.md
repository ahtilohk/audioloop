 (cd "$(git rev-parse --show-toplevel)" && git apply --3way <<'EOF' 
diff --git a/Massituru_puudujäägid_ja_kaalud.md b/Massituru_puudujäägid_ja_kaalud.md
new file mode 100644
index 0000000000000000000000000000000000000000..7cdafa15d9ce698144937ff67ff08c05dc133b1d
--- /dev/null
+++ b/Massituru_puudujäägid_ja_kaalud.md
@@ -0,0 +1,69 @@
+# AudioLoop – puudujäägid ja kaalud (Google Play massituru valmisolek)
+
+Skaala:
+- **9–10** = kriitiline (takistab usaldust, retention’i või Play valmisolekut)
+- **6–8** = oluline (mõjutab kvaliteeditaju, hinnanguid, churn’i)
+- **3–5** = keskmine (poleerimine, premium-tunnetus)
+- **1–2** = madal (järelviimistlus)
+
+## A. Toote usaldusväärsus ja töökindlus
+
+- **Release gate on liiga nõrk (ainult debug assemble CI-s)** — **Kaal: 9/10**
+  - Puudub tugev release-quality värav (`lint`, testid, release assemble, sign/verify sammud).
+- **Crash/ANR observability puudulik** — **Kaal: 9/10**
+  - Ilma tootmismonitooringuta (Crashlytics/Sentry jms) on raske kiiresti regressioone avastada.
+- **Background job robustsus vajab tugevdamist** — **Kaal: 8/10**
+  - WorkManager töödel vajavad constraints/retry/unique-work poliitikad järjepidevat kasutust.
+- **Backup/restore turvalisuse jätku-hardening** — **Kaal: 8/10**
+  - Path-safety on parandatud, kuid vaja lisaks kaitsta zip-bomb tüüpi sisendite vastu (entry count/size limiidid).
+- **Ressursihaldus pika protsessi ajal (madal mälu / kill-juhtumid)** — **Kaal: 7/10**
+  - Vajalik sihipärane testplaan low-end seadmetele ja pikkadele failidele.
+
+## B. Usaldus, privaatsus, Play nõuetele vastavus
+
+- **Backup/data extraction reeglid on liiga üldised** — **Kaal: 9/10**
+  - Vajalik täpne include/exclude poliitika tundlike andmete jaoks.
+- **Privaatsuspoliitika/andmekaitse kommunikatsioon tootes nähtavaks** — **Kaal: 8/10**
+  - Kasutaja peab teadma, mida kogutakse, kus hoitakse ja kuidas kustutada.
+- **Õiguste küsimine kontekstitundlikult + selgitused** — **Kaal: 7/10**
+  - Mikrokoopia enne permission prompti tõstab nõustumist ja usaldust.
+- **Andmete eksport/kustutus (user control) UX** — **Kaal: 7/10**
+  - Selge "export/delete my data" voog parandab usaldust ja Play hinnangut.
+
+## C. Massituru atraktiivsus ja onboarding
+
+- **Esimese 60 sekundi väärtus (Time-to-First-Success) vajab veel lihvi** — **TEHTUD / RESOLVED**
+  - “Importi → lõika → loopi → salvesta” peab olema ülikiire ja väga selge.
+- **Terminoloogia on osaliselt liiga tehniline** — **TEHTUD / RESOLVED**
+  - “Normalize”, “Autotrim”, “Fade” vajavad lihtkeelset UX copy’t + infovihjeid. Suurem osa hardkooditud stringe on eemaldatud.
+- **Tugev use-case põhine onboarding puudub** — **TEHTUD / RESOLVED**
+  - Alguses “Milleks kasutad?” (muusik, keeleõpe, podcast jne) + kohandatud vaikeseaded.
+- **“Aha” hetkede vähene rõhutamine (coach/value nudges)** — **TEHTUD / RESOLVED**
+  - Discovery nudges (loop/speed) prioriteetsed, Smart Coach lisatud Library-tabi, esmakordse kasutuse tähistamine.
+
+## D. Disain, ligipääsetavus, kvaliteeditaju
+
+- **Hardcoded UI stringe on endiselt kasutusel** — **Kaal: 8/10**
+  - Täielik i18n nõuab, et kõik kasutajale nähtavad tekstid oleksid string-resurssides.
+- **Ligipääsetavuse regressioonirisk (suured fondid, TalkBack, kontrast edge-case’id)** — **Kaal: 8/10**
+  - Vajalik süsteemne testmaatriks (font scale, screen size, dark/light, RTL).
+- **Mikrointeraktsioonide järjepidevus ekraanide lõikes** — **Kaal: 5/10**
+  - Haptic, animatsioonid, tühiolekud peavad olema ühes stiilis kõikjal.
+
+## E. Ärimudel ja Play “müüki panek”
+
+- **Monetiseerimisvalmidus (Play Billing integratsioon) pole selgelt tootes nähtav** — **TEHTUD / RESOLVED**
+  - BillingManager loodud, Pro-overlay lisatud, valmidus tellimusteks ja eluaegseks ostuks olemas.
+- **Paywall väärtuspakkumine/katsetusmudel vajab A/B testimist** — **Kaal: 7/10**
+  - “Miks maksta?” peab olema selge ja mõõdetav.
+- **Release-operatsioonid: staged rollout + rollback playbook** — **Kaal: 7/10**
+  - Kriitiline, et vigane väljalase ei kahjustaks reitingut.
+
+## F. Soovituslik järjestus (kõrgeim ROI)
+
+1. **Release gate + crash/ANR nähtavus** — **9/10**
+2. **Privaatsus/backup reeglid + in-app policy** — **9/10**
+3. **Onboarding & lihtkeelne UX copy** — **8–9/10**
+4. **I18n lõpuni + accessibility testmaatriks** — **8/10**
+5. **Billing + staged rollout operatsioonid** — **7–9/10**
+
 
EOF
)