# URSA — project map (receptionist)

Native Android client for Uptime Kuma. **Read `docs/overview.mdx` first each
session, then only the module/reference docs you need — do not scan the repo.**

## ⚠ Project rules (override defaults)

- **Undercover, always.** No AI-provider references anywhere — commits, code,
  comments, docs, README. No co-author trailers, no AI hints.
- The git repo is rooted in this folder. The parent `C:/Users/User` is a separate,
  unrelated repo — **never commit there**.
- Work in small phases: summarize, run lint/tests, commit per phase.
- Don't change UI design/layout/styling unless explicitly asked.

## documentary — project map

### Stack snapshot
- Kotlin 2.4.0 · Jetpack Compose (BOM 2026.06.01, Material 3)
- AGP 9.2.0 / Gradle 9.4.1 · compileSdk 36 · minSdk 26 · JBR 21 → target 17
- AGP 9 built-in Kotlin (no `kotlin.android` plugin). Details: `docs/stack.mdx`.

### Room index (module → path)
- app shell → `app/src/main/java/dev/astoris/ursa/MainActivity.kt` — `docs/modules/app-shell.mdx`
- build config → `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- build spec → `URSA_BUILD_SPEC.md`
- _planned M1 layers_ (paths in `docs/architecture/index.mdx`): `core/network`,
  `core/storage`, `core/push`, `data/model`, `data/repository`, `ui/*`

### External API contracts (persisted docs — read before wiring a lib)
- Uptime Kuma socket/REST (verified live) → `docs/references/uptime-kuma-api.mdx`
- Socket.IO Java client → `docs/references/socketio-java.mdx`
- Ktor client → `docs/references/ktor-client.mdx`
- DataStore + Tink (encrypted creds) → `docs/references/datastore-tink.mdx`
- UnifiedPush push → `docs/references/unifiedpush.mdx`

### Conventions
- Conventional commits (`feat/fix/chore/docs`). Repository pattern, Flows to
  Compose. Kuma wire quirks isolated in the `core.network` adapter. Full rules:
  `docs/style.mdx`.

### Key commands
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat assembleDebug --no-daemon      # build
./gradlew.bat :app:lintDebug --no-daemon     # lint (must pass before commit)
# run: install APK, point at Kuma http://10.0.2.2:3001 (emulator)
```

Update this map and `docs/sessions/log.mdx` whenever structure changes.
