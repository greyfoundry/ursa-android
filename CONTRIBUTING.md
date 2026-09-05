# Contributing to URSA

Thanks for your interest in URSA, a native Android client for
[Uptime Kuma](https://github.com/louislam/uptime-kuma). This guide covers how to
build the app, the conventions we follow, and how to get a change merged.

By participating you agree to our [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- **Search first.** Check existing [issues](../../issues) and
  [pull requests](../../pulls) so we do not duplicate work.
- **Open an issue for anything non-trivial.** For bugs and features, file an issue
  (or start a [Discussion](../../discussions)) before a large PR, so we can agree on
  the approach. Small fixes (typos, obvious bugs) can go straight to a PR.
- **Understand and test your own changes.** Please only submit code you understand
  and have actually run. PRs are reviewed for correctness and quality, not volume.

## Scope

URSA is intentionally focused on monitoring and incident response. Core viewing,
UnifiedPush, native Android surfaces, monitor and maintenance management, public
status pages, advanced fleet dashboards, and the Wear OS companion are implemented.
Advanced monitor-type-specific creation fields and server administration remain
deliberate boundaries. Check the public roadmap before proposing a large surface.

## Development setup

Requirements:

- Android Studio (bundled JDK 21 is fine) with the Android SDK
- JDK 17 target, `compileSdk 37`, `minSdk 26`

Build and test:

```bash
export JAVA_HOME="<path-to-jbr>"     # e.g. Android Studio's bundled JBR
./gradlew assembleDebug              # build the debug APK
./gradlew :app:testDebugUnitTest     # run unit tests
./gradlew :app:lintDebug             # run Android Lint (must pass)
./gradlew :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug
```

To run the app, install the debug APK and point it at an Uptime Kuma instance
(for the emulator, the host is reachable at `http://10.0.2.2:3001`). A quick local
server:

```bash
docker run -d -p 3001:3001 -v uptime-kuma:/app/data --name uptime-kuma louislam/uptime-kuma:2
```

On Android 17, testing or connecting to a direct LAN address may show the platform's
local-network permission prompt. DNS-SD discovery uses the scoped system picker and
does not request broad access by itself.

## Git hooks (required)

URSA keeps committed files to ASCII punctuation (use `-`, not an em-dash or
en-dash). A shared hook enforces this. Enable it once after cloning:

```bash
git config core.hooksPath scripts/hooks
```

The same check runs in CI, so enabling the hook locally saves a round trip.

## Conventions

- **Language / UI**: Kotlin and Jetpack Compose (Material 3). Match the existing
  style; do not restyle existing UI without discussion.
- **Architecture**: reactive and layered. The network layer parses Uptime Kuma's
  wire quirks into clean domain models; repositories expose `StateFlow`s; Compose
  renders them. Keep Kuma's protocol quirks isolated in the network adapter.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org)
  (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, ...). Keep them accurate
  so release notes can be prepared from the history. Use a scope when helpful, e.g.
  `feat(push): ...`.
- **Tests**: add or update unit tests for logic changes. Wire-format parsing must
  stay covered.
- **Lint**: `./gradlew :app:lintDebug` must be clean before you push.

## Pull requests

1. Fork and branch from `main`.
2. Keep the PR focused; one logical change per PR.
3. Fill in the PR template: what changed, why, and how you tested it (include the
   device/emulator and Android version if UI is affected).
4. Ensure build, unit tests, and lint pass locally.
5. Be responsive to review feedback.

## Maintainer releases

Release publication is deliberately manual. Follow the checklist in
[`docs/infrastructure/deployment.mdx`](docs/infrastructure/deployment.mdx) and do
not tag a release until its release PR has passed CI.

## Security

Do not open public issues for vulnerabilities. See [SECURITY.md](SECURITY.md) for
private reporting. URSA's security posture is documented in
[docs/security.mdx](docs/security.mdx).

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT License](LICENSE).
