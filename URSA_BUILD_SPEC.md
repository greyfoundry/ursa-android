# URSA - Uptime Kuma Android Client

Build specification for package `dev.astoris.ursa`.

Status: Implemented and verified against live Uptime Kuma 2.4.x and 2.5.0-2.5.3.
Stack: Kotlin, Jetpack Compose, native Android, and a separate native Wear OS module.

## Product boundary

URSA is a native companion for self-hosted Uptime Kuma. It has no hosted backend,
account service, analytics, advertising, or paid tier. It connects directly to the
servers and push distributor selected by the user.

The implemented phone surface includes:

- Multiple encrypted server connections, password/TOTP or imported session login,
  opt-in self-signed TLS, and up to eight encrypted access headers per server.
- Live monitor state, history, charts, certificates, public status pages, offline
  cache, compact and responsive layouts, saved filters, and fleet dashboards.
- Safe common monitor editing/creation, notification assignment, groups, tags, bulk
  actions, local discovery, and all six maintenance strategies.
- UnifiedPush, certificate/slow-response/update notifications, app lock, widgets,
  tile, shortcuts, strict deep links, encrypted backups, and glance display.
- A separate Wear OS app with dashboard, detail, tile, complications, and optional
  same-signed phone session pairing.

Advanced type-specific monitor creation and Kuma server administration remain out of
scope. Unknown or sensitive monitor fields stay inside the network adapter and are
preserved during common edits.

## Architecture

```
Kuma Socket.IO / REST -> core.network -> data.repository -> StateFlow -> Compose UI
Encrypted DataStore   -> connection/session policy --------^       -> Android surfaces
UnifiedPush           -> core.push -------------------------------> notifications
```

Kuma's API is internal and unstable. Every wire quirk, including positional events,
snake/camel naming splits, and double-encoded configuration, belongs in
`core.network`. The rest of the app consumes bounded domain models. See
`docs/references/uptime-kuma-api.mdx` for the verified contract.

## Storage and security

- Tink AES-256-GCM seals sensitive DataStore records under an Android
  Keystore-backed master key.
- Passwords and TOTP codes are never persisted. Session tokens and custom header
  values are masked, never logged, and never rendered after save.
- Release builds apply `FLAG_SECURE` and exclude all app data from backup/transfer.
- TLS is the default. Plain HTTP and session-lifetime TOFU for self-signed
  certificates are explicit self-hosting compromises documented in
  `docs/security.mdx`.
- Deep links contain a one-way server scope plus a bounded local identifier, never a
  server URL, token, header, or credential.

## Toolchain

| Component | Version |
|---|---|
| Kotlin | 2.4.10 |
| Compose BOM | 2026.08.00 |
| Android Gradle Plugin | 9.3.2 |
| Gradle | 9.7.1 |
| compileSdk / targetSdk | 37 / 37 |
| phone minSdk | 26 |
| Wear minSdk | 30 |
| Java/Kotlin target | 17 |

`gradle/libs.versions.toml` is authoritative when versions change. AGP 9 has
built-in Kotlin, so modules do not apply `org.jetbrains.kotlin.android`.

## Verification gates

```bash
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
./gradlew.bat :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug --no-daemon
./gradlew.bat -PursaWearBridge=true :app:assembleDebug --no-daemon
```

Live release-candidate QA covers password/TOTP or token login, reconnect, direct
Android 17 LAN permission, custom access headers through a reverse-proxy gate,
management actions, notification delivery, scoped deep links, phone UI, and Wear
surfaces against an official Kuma container.

## Release model

Releases are manual. A release PR updates both module versions, the changelog,
Fastlane metadata, and the mirrored F-Droid recipe. After CI passes, the maintainer
creates the `vX.Y.Z` tag and GitHub release, then runs the artifact workflow against
that existing tag. F-Droid discovers normal version tags automatically.
