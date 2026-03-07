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
+- **Release gate on liiga nõrk (ainult debug assemble CI-s)** — **TEHTUD / RESOLVED**
+  - Lisatud `testDebugUnitTest` ja `lintDebug` automaatkontrollid Github Actions-isse.
+- **I18n lõpuni + ligipääsetavuse (accessibility) testmaatriks** — **TEHTUD / RESOLVED**
+  - Kõik interaktiivsed elemendid (A/B kordus, waveform) on TalkBack-sõbralikud.
+  - Puutealad suurendatud min 44-48dp-ni (eriti A/B nupud).
+  - Lisatud automaatne `AccessibilityTest.kt` kontrollimaks silte ja olekuid.
+  - I18n Wave 1 (ES, DE, FR, PT-BR, KO, JA) on valmis.
+- **Crash/ANR observability puudulik** — **Kaal: 9/10**
+  - Ilma tootmismonitooringuta (Crashlytics/Sentry jms) on raske kiiresti regressioone avastada.
+- **Background job robustsus vajab tugevdamist** — **Kaal: 8/10**
+  - WorkManager töödel vajavad constraints/retry/unique-work poliitikad järjepidevat kasutust.
+- **Backup/restore turvalisuse jätku-hardening** — **Kaal: 8/10**
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
+- **Hardcoded UI stringe on endiselt kasutusel** — **TEHTUD / RESOLVED**
+  - Kõik Wave 1 keeled (ES, PT, DE, FR, KO, JA) on süva-lokaliseeritud ja hardkooditud stringid eemaldatud.
+- **Ligipääsetavuse regressioonirisk (suured fondid, TalkBack, kontrast edge-case’id)** — **Kaal: 8/10**
4. **I18n lõpuni + accessibility testmaatriks** — **TEHTUD / RESOLVED**
+5. **Billing + staged rollout operatsioonid** — **TEHTUD / RESOLVED**
+
 
EOF
)