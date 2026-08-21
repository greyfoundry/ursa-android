# URSA - Uptime Kuma Android Client
### Build Spec v1 (package `dev.astoris.ursa`)

Status: Implemented - reverified against live Kuma 2.5.0 on 2026-08-21
Stack: Kotlin, Jetpack Compose, native Android (no KMP, no RN)

---

## 0. Verification status (2026-08-21)

The protocol checks ran against a live `louislam/uptime-kuma:2.5.0` instance.
Results folded into this doc:

- **Storage (§5):** EncryptedSharedPreferences confirmed deprecated. DataStore +
  Tink + Keystore approach stands.
- **Versions (§8):** catalog updated to Kotlin 2.4.10, Compose BOM 2026.08.00,
  AGP 9.3.1, Gradle 9.7.1, and compileSdk/targetSdk 37.
- **Socket.IO API (§4):** event *names* correct; several *payload shapes* in the
  original draft were wrong and are corrected below (positional args, chart field
  names, snake_case vs camelCase). Adapter layer is now mandatory, not optional.
- **Push (§6):** tested end to end - Kuma Webhook → ntfy endpoint delivered
  (HTTP 200). Design confirmed; safe to build M3 on it.

Items marked `[VERIFIED]` were observed live; `[UNVERIFIED]` were not exercised
and still come from the wiki.

---

## 1. Why this exists

Uptime Kuma: 88.7k GitHub stars, 169M+ combined Docker pulls, MIT licensed,
actively maintained.

Android has been the visible gap for years:

- Issue `#1943` ("API for Android App") open since July 2022, no resolution.
- Issue `#2351` ("App for Android with push notification Integrated") open since Nov 2022.
- The most-starred community attempt (Expo/React Native, "an attempt to create a
  mobile app for Uptime Kuma") sits at 3 stars, effectively abandoned.
- The one Play Store app (`Uptime Kuma Manager`, closed source) has live reviews
  reporting broken 2FA and a settings lock-out bug on classic 3-button nav.
- iOS has two actively maintained third-party apps (Live Activities, widgets,
  Apple Watch, push - paid/subscription-gated).

Proven scale, multi-year unmet demand, existing Android options dead or broken.

---

## 2. Scope for v1

**In:**
- Multi-server connection management (add/remove/switch instances)
- Login (username/password, 2FA token, "remember me" → persistent JWT)
- Live monitor list - real-time status, ping, uptime %, grouped by tag
- Monitor detail - heartbeat history chart, recent event log, TLS cert info
- Pause / resume a monitor
- Public status page viewer (no auth required, read-only)
- Push notifications via UnifiedPush (§6) - the actual differentiator

**Out (deferred to v1.1+):**
- Creating or editing monitors
- Notification provider CRUD from the app (configure in Kuma web UI for now)
- Status page creation/editing
- Maintenance window management
- API key management, Docker monitor specifics, remote browser monitors
- Home screen widgets (stretch)

Rationale: everything "in" is read-heavy or single-action. Everything "out" is a
full CRUD surface with its own validation and error states.

---

## 3. Architecture

```
app/
├── core/
│   ├── network/        # Socket.IO client wrapper, REST client (Ktor)
│   ├── storage/        # DataStore + Tink-encrypted credential store
│   └── push/           # UnifiedPush connector integration
├── data/
│   ├── model/          # Monitor, Heartbeat, Notification, StatusPage data classes
│   └── repository/     # Single source of truth per domain, exposes Flow<T>
├── ui/
│   ├── connections/    # Add/switch server screen
│   ├── monitors/       # List + detail
│   ├── statuspage/     # Public status page viewer
│   └── settings/
└── MainActivity.kt
```

Single persistent Socket.IO connection per active server, held in a
service-scoped repository (not a background service for v1 - push does not need
the socket alive, see §6). Repositories expose `StateFlow`/`Flow` to Compose via
`collectAsStateWithLifecycle`.

### Why native Kotlin over KMP/RN
This is explicitly the "new surface" project - Kotlin/Compose skill-building is
part of the point. Noted so it doesn't get re-litigated as "should this be TS"
mid-build.

---

## 4. Uptime Kuma API - what v1 actually touches

The API is internal/unstable (flagged as such in the Kuma wiki, no back-compat
guarantee). Build a **thin adapter layer** so protocol drift doesn't ripple
through the app. Verification on 2.5.0 confirmed this is necessary - several
payloads are positional multi-arg emits, not the objects the wiki implies.

### 4.1 First-run / DB setup `[VERIFIED]`
Kuma 2.x boots into a separate "setup-database" mode first; the main app socket
is **not** mounted until a database is chosen. For a dev instance, seed
`data/db-config.json` with `{"type":"sqlite"}` and restart to skip the wizard.
The app itself never touches this - it only ever connects to already-configured
servers - but note it when standing up test instances.

### 4.2 Connection & auth flow (Socket.IO) `[VERIFIED]`
1. Open Socket.IO connection to server root (`/`). Transport negotiation: open
   with polling then upgrade; websocket-first can `connect_error` on some setups.
2. Server emits `loginRequired` (and, if no admin exists yet, `setup`).
3. Client emits `login` with `{ username, password, token? }` (token = 2FA code).
   - Callback: `{ ok, token?, tokenRequired? }`. `tokenRequired: true` → retry
     with the 2FA token.
   - On success with "remember me," store the returned JWT.
4. On later opens, emit `loginByToken` with the stored JWT instead of re-prompting.
5. On auth, server pushes `monitorList` (full snapshot) then real-time deltas,
   plus a batch of list events (see 4.6).

Observed login callback: `{ ok: true, token: "<JWT>" }`.

### 4.3 Server → client events used in v1 `[VERIFIED unless noted]`

> **Correction from earlier draft:** `avgPing`, `uptime`, and `certInfo` are
> emitted as **positional arguments**, NOT single objects. Handle them as
> `socket.on("uptime", (monitorID, period, percent) => …)`.

| Event | Actual payload (2.5.0) | Use |
|---|---|---|
| `info` | `{ version, latestVersion, serverTimezone, serverTimezoneOffset, dbType, isContainer, primaryBaseURL, runtime }` | Server info, version-gate features |
| `monitorList` | `{ [monitorID]: MonitorObject }` (id-keyed object) | Initial full list |
| `updateMonitorIntoList` | `{ [monitorID]: MonitorObject }` | Delta update `[UNVERIFIED]` |
| `deleteMonitorFromList` | `monitorID` | Remove from list `[UNVERIFIED]` |
| `heartbeat` | `{ monitorID, status, time, msg, ping, important, retries }` - camelCase | Real-time ping/status |
| `avgPing` | **positional** `(monitorID, avgPingMs)` | 24h average |
| `uptime` | **positional** `(monitorID, periodHours, fraction)` - period is a **number** (e.g. 24), fraction is **0..1** | Uptime % |
| `certInfo` | **positional** `(monitorID, tlsInfoJSON)` | Cert expiry for HTTPS monitors |

`status`: `0=DOWN, 1=UP, 2=PENDING, 3=MAINTENANCE` `[VERIFIED]`.
`time` is a string `"YYYY-MM-DD HH:mm:ss.SSS"` in server timezone, not an epoch.

### 4.4 Client → server events used in v1
- `login`, `loginByToken`, `logout`
- `getMonitor(monitorID)` → full `MonitorObject`
- `getMonitorBeats(monitorID, periodHours, cb)` → `{ ok, data: HeartbeatRow[] }` `[VERIFIED]`
- `getMonitorChartData(monitorID, periodHours, cb)` → `{ ok, data: ChartPoint[] }` `[VERIFIED]`
- `pauseMonitor(monitorID, cb)` / `resumeMonitor(monitorID, cb)` `[VERIFIED]`

All client-sent events return `{ ok, msg?, msgi18n? }` (plus event-specific
fields) via callback. Standardise error handling around this shape.

> **Correction - `getMonitorChartData` point shape:** rows are
> `{ up, down, avgPing, minPing, maxPing, timestamp }` (epoch seconds).
> The earlier draft's `ping / pingMin / pingMax` names are wrong.

> **Correction - case inconsistency:** the live `heartbeat` *event* is camelCase
> (`monitorID`), but `getMonitorBeats` *rows* are snake_case
> (`monitor_id, down_count, end_time, …`). The data model must map both.

### 4.5 REST endpoints used in v1 `[UNVERIFIED - wiki]`
- `GET /api/status-page/<slug>` - status page config, groups, monitors, incident
- `GET /api/status-page/heartbeat/<slug>` - heartbeat + uptime data for the page
- `GET /api/entry-page` - optional; detects vanity-domain status pages

No auth needed - status pages are public if published. The status page viewer can
ship before login/socket plumbing is solid - good M1 momentum.

### 4.6 Data shapes to model
`MonitorObject`, `HeartbeatObject` `[VERIFIED]`; `NotificationObject`
`[UNVERIFIED]`. Gotchas:
- Notification `config` and monitor `tags` arrive as **stringified JSON inside
  JSON** - parse twice.
- On login the server also pushes: `monitorTypeList`, `maintenanceList`,
  `notificationList`, `proxyList`, `dockerHostList`, `apiKeyList`,
  `remoteBrowserList`, `statusPageList`, `initServerTimezone`. v1 ignores most but
  the adapter should not choke on them.
- Kuma 2.x `add` requires a NOT-NULL `conditions` field (stringified JSON array).
  Only relevant once monitor create/edit lands (M2+, currently out of scope).

---

## 5. Storage & security

**Do not use `EncryptedSharedPreferences`.** `[VERIFIED]` It was deprecated at
`androidx.security:security-crypto:1.1.0-beta01` (June 2025) and the deprecation
shipped in stable **1.1.0** (July 30 2025) - all APIs deprecated in favour of
platform APIs / direct Android Keystore. Current pattern for 2026:

- **Jetpack DataStore** (Preferences or Proto) for persistence - coroutine/Flow
  native, off main thread by default
- **Tink** (`com.google.crypto.tink:tink-android`) for encryption primitives
- **Android Keystore** to hold the master key, never stored alongside the data

Store per-connection: server URL, username (not password - only the JWT after
first login), JWT token. If "remember me" wasn't used, don't persist the JWT.

---

## 6. Push notifications - the differentiator `[VERIFIED end-to-end]`

Kuma has no built-in mobile push. Every third-party client either skips push or
runs a paid relay. URSA uses **UnifiedPush, not a custom relay.**

- UnifiedPush is the established FOSS alternative to FCM. No Play Services, no
  account, self-hostable distributor.
- ntfy is the common distributor. Kuma ships a generic **Webhook** notification
  provider that POSTs to any URL - a UnifiedPush endpoint *is* just such a URL.
- Flow: user installs a UnifiedPush distributor (ntfy default). URSA registers
  via `org.unifiedpush.android:connector`, gets an endpoint URL, shows it with a
  copy button. User pastes it into a Webhook notification in their Kuma instance.
  No server for us to run.
- The Socket.IO connection does **not** need to stay alive in the background for
  push - it's only live while the app is open. No foreground service for v1.

**Verification result (2026-07-03):** ran ntfy + Kuma Webhook (json content type)
against a deliberately-down monitor. Kuma POSTed successfully (HTTP 200) and the
message was delivered. Payload body:

```json
{ "heartbeat": { "monitorID": 2, "status": 0, "time": "…", "msg": "connect ECONNREFUSED …", "important": true, "retries": 1 },
  "monitor":   { "id": 2, "name": "down-test", "url": "…", … },
  "msg": "[down-test] [🔴 Down] connect ECONNREFUSED 127.0.0.1:9" }
```

Implications for the app:
- `msg` is a ready-made human-readable notification string - display it directly.
- `heartbeat.status` + `monitor.name` drive icon/routing/grouping.
- For UnifiedPush proper, append `?up=1` to the ntfy endpoint so ntfy forwards the
  raw bytes; URSA parses this JSON itself rather than relying on ntfy formatting.
- No relay, no Firebase, no subscription. This is the feature that makes URSA
  categorically better than existing options. Protect its scope.

---

## 7. Milestones

**M1 - Core viewing.** Connection management, login incl. 2FA, live monitor list,
monitor detail with heartbeat chart, status page viewer. No actions. Goal: prove
the Socket.IO + Compose data flow end to end against a homelab instance.

**M2 - Actions + polish.** Pause/resume, multi-server switching, DataStore/Tink
credential storage hardened, error/reconnect handling. Self-signed certs are
common in this userbase - handle TLS explicitly, don't fail silently.

**M3 - UnifiedPush + release.** Webhook endpoint registration flow, notification
handling, F-Droid metadata, README, MIT license (match upstream). Confirm
`org.unifiedpush.android:connector` and `io.socket:socket.io-client` are FOSS-clean
for `fdroiddata`.

---

## 8. Environment & version catalog (current 2026-08-21)

Prefer Android Studio "Empty Activity (Compose)" wizard - it wires the Gradle
wrapper, AGP, and Compose compiler plugin correctly. Set minSdk 26, package
`dev.astoris.ursa`, Kotlin.

Toolchain (verified current stable):

| Component | Version | Notes |
|---|---|---|
| Kotlin | **2.4.10** | pinned through the root buildscript classpath |
| Compose BOM | **2026.08.00** | Compose 1.12 line; requires compileSdk 37 |
| AGP | **9.3.1** | Gradle 9.7.1, JDK 17 target |
| compileSdk / targetSdk | **37 / 37** | Android 17 platform installed and verified locally |
| minSdk | 26 | Android 8.0, ~98%+ of active devices |
| JDK | 17 | AGP 9.x minimum (JBR 21 used to build, targets 17) |

**AGP 9 built-in Kotlin (gotcha, learned during scaffold):** AGP 9.0+ compiles
Kotlin itself - do **NOT** apply `org.jetbrains.kotlin.android` (it errors). Still
apply `org.jetbrains.kotlin.plugin.compose`. AGP bundles an older KGP, so to run
Kotlin 2.4.10 (matching the Compose compiler) pin it in the **root** build via
`buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") } }`.
The `android { compilerOptions { } }` DSL from the AGP 9.0 notes is not resolvable
on 9.2 - omit explicit Kotlin `jvmTarget`; AGP aligns it to `compileOptions`.

`gradle/libs.versions.toml` - key deps beyond the Compose template:

```toml
[versions]
kotlin = "2.4.10"
composeBom = "2026.08.00"
agp = "9.3.1"
socketio = "2.1.2"
ktor = "3.5.2"
unifiedpush = "3.3.4"
tink = "1.23.0"
datastore = "1.2.1"

[libraries]
socket-io-client = { group = "io.socket", name = "socket.io-client", version.ref = "socketio" }
unifiedpush-connector = { group = "org.unifiedpush.android", name = "connector", version.ref = "unifiedpush" }
tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
```

Module `build.gradle.kts` - exclude Socket.IO's bundled `org.json` (Android
provides its own):

```kotlin
implementation(libs.socket.io.client) {
    exclude(group = "org.json", module = "json")
}
```

`AndroidManifest.xml` - `INTERNET` at minimum; `POST_NOTIFICATIONS` for API 33+.

**Local dev instance** (already running from verification):

```bash
docker run -d -p 3001:3001 -v uptime-kuma-dev:/app/data --name uptime-kuma-dev louislam/uptime-kuma:2
# then seed data/db-config.json = {"type":"sqlite"} and restart to skip the wizard
```

From the emulator hit `http://10.0.2.2:3001`; from a physical device use the LAN IP.

---

## 9. Open questions

- Final app name - URSA is a placeholder (avoids "Kuma Companion" / "Wuma" on iOS).
- minSdk 26 confirmed as the floor unless there's a reason to go higher.
- Whether the status page viewer (§4.5) ships as an unauthenticated "quick look"
  first-run entry point before a full server connection exists.
