# Wear-App im Emulator testen (3 Schritte)

## Wichtig

**Die Uhrzeit auf dem runden Display = System-Zifferblatt, nicht unsere App.**

Die App heißt **Eazpire Creator** und hat dunklen Hintergrund + orange Tabs oder ein Logo beim Start.

---

## Schritt 1: Projekt öffnen

In Android Studio **diesen Ordner** öffnen (nicht nur `android`):

```
…/creator-worker/wear
```

Modul oben: **`app`** auswählen.

---

## Schritt 2: Emulator starten & App installieren

1. **Tools → Device Manager** → **Wear OS XL Round** → ▶ Start  
2. Grüner **Run**-Button (▶) klicken  
3. Unten: **Install successfully finished**

---

## Schritt 3: App **auf der Uhr** öffnen (oft vergessen!)

Nach Run zeigt der Emulator oft noch das **Zifferblatt**. Dann:

1. **In das runde Emulator-Fenster klicken** (rechts in Studio)  
2. In der Leiste **über** der Uhr: Icon **App-Drawer** (Kreis mit 6 Punkten)  
   - oder vom Zifferblatt **nach oben wischen** (Maus: klicken + nach oben ziehen)  
3. **Eazpire Creator** antippen

### Erwartung

| Phase | Was du siehst |
|--------|----------------|
| Start | **Eazpire-Creator-Logo** zentriert (~2 s), dunkler Hintergrund |
| Danach | 3 Chips: **Dashboard** · **Active Jobs** · **Phone upload** (Demo-Daten im Emulator) |

---

## Terminal-Shortcut (Windows)

```powershell
cd "C:\Users\tobim\OneDrive\Dokumente\Cursor Projects\creator-worker"
npm run wear:run-emulator
```

Startet die App direkt auf dem laufenden Emulator.

---

## Build-Fehler `packageDebug` / NullPointerException

Ursache war oft **OneDrive-Sperre** auf `wear/*/build` oder kaputte Zwischenstände.

**Fix im Projekt:** Builds landen lokal unter  
`%LOCALAPPDATA%\eazpire-wear-build\` (nicht im OneDrive-Ordner).

Wenn es in Android Studio noch scheitert:

1. **Build → Clean Project**, dann **Rebuild**
2. Oder Terminal: `cd wear` → `.\gradlew.bat --stop` → `.\gradlew.bat :app:assembleDebug`
3. Bei „Unable to delete directory“: Android Studio kurz schließen, Befehl erneut ausführen

Debug-APK: `%LOCALAPPDATA%\eazpire-wear-build\app\outputs\apk\debug\app-debug.apk`

## Nur ein kleiner Streifen oben („Eazpire Creator“) + Uhr?

Das ist **kein Widget** — die Uhr war im **Ambient-Modus** (Energiesparen). Die App wurde oben als Mini-Streifen gezeichnet, darunter das Zifferblatt.

**Fix:** App antippen / Bildschirm antippen → **interaktiver Modus** → volles Display (Logo, Tabs, Inhalt).

Im Emulator: **Einstellungen → Entwickleroptionen → Bildschirm bleibt aktiv** (Stay awake) oder Uhr während des Tests laden.

---

## Echte Daten (optional)

- Phone-Projekt: `creator-worker/android` → einloggen  
- Wear + Phone koppeln (echtes Gerät einfacher als 2 Emulatoren)
