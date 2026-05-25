# Eazpire Creator — Wear OS

Creator-only companion for Wear OS (no shop). Synced to `eazpire/eazpire-creator-wear` for Google Play releases.

## Features (MVP)

- Dashboard stats (products, sales, designs, payout balance)
- Active jobs list
- Phone upload via QR (`creator-phone-upload` API)
- Auth synced from phone app via Wearable Data Layer

## Requirements

- Phone app `com.eazpire.creator` logged in on a paired watch
- Android Studio or JDK 17 + Android SDK

## Build

```bash
cd wear
./gradlew assembleDebug
```

From repo root: `npm run wear:build` or `npm run wear:run-emulator`

On Windows, outputs go to `%LOCALAPPDATA%\eazpire-wear-build\` (avoids OneDrive file locks and `packageDebug` NPE). See `EMULATOR_QUICKSTART.md`.

## Setup & Play

See [docs/setup/WEAR_REPO_SETUP.md](../docs/setup/WEAR_REPO_SETUP.md).
