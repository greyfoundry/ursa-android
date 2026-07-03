# URSA

A native Android client for [Uptime Kuma](https://github.com/louislam/uptime-kuma) —
watch your self-hosted monitors from your phone, in real time.

> Status: early development. The app scaffold builds; core viewing (M1) is in progress.

## Why

Uptime Kuma has a huge homelab userbase but no solid Android app — the community
options are abandoned or broken. URSA aims to be the one that isn't, with a clean
real-time view and push notifications that need **no paid relay server**.

## Features

**In progress (M1 — core viewing)**
- Connect to one or more Uptime Kuma servers
- Login with username/password + 2FA, persistent session
- Live monitor list — real-time status, ping, uptime
- Monitor detail — heartbeat history, TLS certificate info
- Pause / resume a monitor
- Public status-page viewer (no login required)

**Planned**
- Push notifications via [UnifiedPush](https://unifiedpush.org) (FOSS, self-hostable,
  no Firebase) — the app registers an endpoint you paste into a Kuma Webhook
  notification; no server to run
- F-Droid release

## Tech

- Kotlin + Jetpack Compose (Material 3)
- Socket.IO for the live connection, Ktor for status-page REST
- DataStore for encrypted-at-rest credentials
- AGP 9 / Gradle 9.4.1 · compileSdk 36 · minSdk 26 (Android 8.0+)

## Build

```bash
# Requires Android Studio (bundled JDK) + Android SDK
export JAVA_HOME="<path-to-jbr>"
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug
```

Point the app at a Uptime Kuma instance (e.g. `http://10.0.2.2:3001` from an
emulator). Local dev instance:

```bash
docker run -d -p 3001:3001 -v uptime-kuma:/app/data --name uptime-kuma louislam/uptime-kuma:2
```

## License

MIT — matching upstream Uptime Kuma.
