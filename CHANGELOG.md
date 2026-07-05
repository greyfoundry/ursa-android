# Changelog

## [1.1.2](https://github.com/AstorisTheBrave/ursa-android/compare/v1.1.1...v1.1.2) (2026-07-05)


### Bug Fixes

* **build:** literal versionName/versionCode so F-Droid checkupdates can parse them ([e793b2e](https://github.com/AstorisTheBrave/ursa-android/commit/e793b2ee936eae84a3d828f3100efa4b94ff481f))

## [1.1.1](https://github.com/AstorisTheBrave/ursa-android/compare/v1.1.0...v1.1.1) (2026-07-05)


### Bug Fixes

* **build:** omit AGP dependency-metadata signing block for F-Droid ([a75ff71](https://github.com/AstorisTheBrave/ursa-android/commit/a75ff719a85d0ab7638645883556146a6457fde6))

## [1.1.0](https://github.com/AstorisTheBrave/ursa-android/compare/v1.0.0...v1.1.0) (2026-07-05)


### Features

* **alerts:** live foreground check + per-monitor override, shared notifier ([#1813](https://github.com/AstorisTheBrave/ursa-android/issues/1813)) ([4948e5e](https://github.com/AstorisTheBrave/ursa-android/commit/4948e5e3b5d371bd081d9eb6cb6f98bf8d8d1a5e))
* **alerts:** periodic background slow-response worker ([#1813](https://github.com/AstorisTheBrave/ursa-android/issues/1813)) ([2737d3a](https://github.com/AstorisTheBrave/ursa-android/commit/2737d3a14ac105e184b15df62af7d669b622fb78))
* **alerts:** response-time threshold evaluator + settings store ([#1813](https://github.com/AstorisTheBrave/ursa-android/issues/1813) groundwork) ([829f336](https://github.com/AstorisTheBrave/ursa-android/commit/829f33609ecf1af0b628ac7c45152e11ee22a793))
* **alerts:** settings toggle + response-time limit ([#1813](https://github.com/AstorisTheBrave/ursa-android/issues/1813)) ([deba08b](https://github.com/AstorisTheBrave/ursa-android/commit/deba08b1a132ac123870cb48ff620ae537bf2284))
* **assets:** bear app icon (adaptive + themed monochrome) + notification icon ([5c99073](https://github.com/AstorisTheBrave/ursa-android/commit/5c99073d5229aa7bb3ddee6c2c4c232f99805c9c))
* **data:** connection storage + monitor repository ([38f76f9](https://github.com/AstorisTheBrave/ursa-android/commit/38f76f9739f1ed81a40a21b147433557bd16d0f5))
* **m2:** encrypt credentials (Tink) + resilient reconnect ([96b0d3b](https://github.com/AstorisTheBrave/ursa-android/commit/96b0d3bbf5288db89283d5131e7bf6a0fc9cee7e))
* **m2:** opt-in self-signed TLS trust per connection ([290bb14](https://github.com/AstorisTheBrave/ursa-android/commit/290bb14511cf87c58dc2d8697d0a74287f504c4e))
* **m3:** push registration UI — distributor picker, endpoint, notif permission ([c77727b](https://github.com/AstorisTheBrave/ursa-android/commit/c77727b5867ce2c827c87ec45657f6ada84ff2b8))
* **m3:** UnifiedPush receive path — PushService, webhook parser, notifications ([9f61207](https://github.com/AstorisTheBrave/ursa-android/commit/9f61207e4d4bf9ae3c9723dfaea616609b7b43d7))
* **m4:** app shortcuts (Push, Settings) with deep-link routing ([5ead908](https://github.com/AstorisTheBrave/ursa-android/commit/5ead90867a886784a659cba531406a70b69f8dd6))
* **m4:** biometric / device-credential app lock + settings toggle ([17a39a2](https://github.com/AstorisTheBrave/ursa-android/commit/17a39a2c852b09d041104851190f5d5712aec67d))
* **m4:** home-screen widget (Glance) with cached up/down counts ([8926d23](https://github.com/AstorisTheBrave/ursa-android/commit/8926d237cc2a78ef5d9bf6561c664c20eb9921ec))
* **m4:** i18n string extraction (part 1: list, login, settings, lock, status) ([16f8f1f](https://github.com/AstorisTheBrave/ursa-android/commit/16f8f1fbbc8bfb852898022dca7aa3a8c6e9c311))
* **m4:** i18n string extraction (part 2: detail, status page, push) ([07a14fc](https://github.com/AstorisTheBrave/ursa-android/commit/07a14fc9718ff75ed282a933e58eb31d76a2e740))
* **m4:** local TLS-expiry reminders (cached cert data + daily WorkManager) ([81be906](https://github.com/AstorisTheBrave/ursa-android/commit/81be906892822f59325f4f39f80bb348415f77b0))
* **m4:** offline last-known monitor cache (encrypted, per-server) [2/2] ([0675e26](https://github.com/AstorisTheBrave/ursa-android/commit/0675e26fec37e6f49595cb9e9f2717e9829f6385))
* **m4:** Pause/Resume actions on monitor notifications ([ec6261a](https://github.com/AstorisTheBrave/ursa-android/commit/ec6261aa5e02af58606a451752a69ee1ca45c97b))
* **m4:** Quick Settings tile with cached up/down status ([8256c66](https://github.com/AstorisTheBrave/ursa-android/commit/8256c66e1c0075a51ee09e494b78fb95ad61de9e))
* **m4:** serializable monitor snapshot model + pure codec (offline cache 1/3) ([3ebbb90](https://github.com/AstorisTheBrave/ursa-android/commit/3ebbb90f5c3b7d30b68a4a0b2e50c1889dce10cc))
* **network:** Kuma domain models + Socket.IO client adapter ([a2a4269](https://github.com/AstorisTheBrave/ursa-android/commit/a2a4269eebf077172b3288a1aa2aeaba9e780db5))
* **push:** report downtime duration on recovery ([#177](https://github.com/AstorisTheBrave/ursa-android/issues/177)) ([3a67915](https://github.com/AstorisTheBrave/ursa-android/commit/3a6791553a0207d647688e7bb2ce69b2545122f0))
* **security:** FLAG_SECURE app-wide to block screenshots/recents leakage ([bb98517](https://github.com/AstorisTheBrave/ursa-android/commit/bb98517d87d27d82f929ab9fcfc9cab01515d94b))
* **theme:** opt-in Material You dynamic color (default keeps Kuma palette) ([5d0f44b](https://github.com/AstorisTheBrave/ursa-android/commit/5d0f44baa7984e35406689631a2b067d1a29578f))
* **ui:** bottom navigation shell (Monitors/Notifications/Settings) + bear logo header ([8a02797](https://github.com/AstorisTheBrave/ursa-android/commit/8a027977a51cab23bb45f7d97f29f74ffa14feba))
* **ui:** Kuma-matched theme with light/dark mode and status colors ([834907d](https://github.com/AstorisTheBrave/ursa-android/commit/834907d79e137144b560ed4c49bcefd7f56b2c6c))
* **ui:** Kuma-style status pill badges + rounded card monitor list ([509e52f](https://github.com/AstorisTheBrave/ursa-android/commit/509e52f6955583dc3062784fd5a060a122829ad4))
* **ui:** login screen + live monitor list ([4c22f45](https://github.com/AstorisTheBrave/ursa-android/commit/4c22f4599e8b60544dd7b1ed16703296b740ea60))
* **ui:** monitor detail — heartbeat history + TLS cert ([ee6e218](https://github.com/AstorisTheBrave/ursa-android/commit/ee6e218160c2c35ed5dc5d24db95d687d5ce7ebb))
* **ui:** per-monitor response-time sparklines on cards (heartbeatList capture) ([a3fe1d5](https://github.com/AstorisTheBrave/ursa-android/commit/a3fe1d5cd3bb4665a119ae8a455f85ffecefb2f3))
* **ui:** public status-page viewer (Ktor) ([6e72617](https://github.com/AstorisTheBrave/ursa-android/commit/6e726172728ccd375084682f2fcb43bc3d15cdea))
* **ui:** redesigned monitor cards (status circle + name/url + pill + uptime) ([a462f3f](https://github.com/AstorisTheBrave/ursa-android/commit/a462f3f0402ca73f106e5b53b95aeb3cf0cdc9f2))
* **ui:** search + status filter in the monitor list top bar ([8666dcc](https://github.com/AstorisTheBrave/ursa-android/commit/8666dccf8ab4dec913ea35483eaa7318ed3f7d3d))
* **ui:** selectable heartbeat range on monitor detail ([#1888](https://github.com/AstorisTheBrave/ursa-android/issues/1888)) ([789095f](https://github.com/AstorisTheBrave/ursa-android/commit/789095fe16b67496f00d772645c716517d8d3ea7))
* **ui:** service favicons in monitor list, status-circle fallback ([#443](https://github.com/AstorisTheBrave/ursa-android/issues/443)) ([1d65700](https://github.com/AstorisTheBrave/ursa-android/commit/1d6570059810caa7e19c7eb664bf05f7c1d3a56a))
* **wear:** FOSS Wear OS module + status tile skeleton (androidx tiles, no GMS) ([00f6ac2](https://github.com/AstorisTheBrave/ursa-android/commit/00f6ac2e79cc3cef63ebe4895f5ffdddeb5cecb3))
* **wear:** live status tile - polls Kuma status page, tap to configure ([3875ac0](https://github.com/AstorisTheBrave/ursa-android/commit/3875ac0aca64c92732073fba4494ab18f26ba215))


### Bug Fixes

* startup crash (repo init order), dark theme (day/night resources + Surface), FLAG_SECURE debug gate ([4326bca](https://github.com/AstorisTheBrave/ursa-android/commit/4326bca25f29100629c0b6bc6947589db9bf59b2))
* **wear:** allow cleartext HTTP so plain-http Kuma status pages load ([1f85591](https://github.com/AstorisTheBrave/ursa-android/commit/1f85591ab1f0ea06af9c307d89fa796c58366bf3))

## [1.0.0](https://github.com/AstorisTheBrave/ursa-android/compare/ursa-android-v0.1.0...ursa-android-v1.0.0) (2026-07-05)


### Features

* **assets:** bear app icon (adaptive + themed monochrome) + notification icon ([5c99073](https://github.com/AstorisTheBrave/ursa-android/commit/5c99073d5229aa7bb3ddee6c2c4c232f99805c9c))
* **data:** connection storage + monitor repository ([38f76f9](https://github.com/AstorisTheBrave/ursa-android/commit/38f76f9739f1ed81a40a21b147433557bd16d0f5))
* **m2:** encrypt credentials (Tink) + resilient reconnect ([96b0d3b](https://github.com/AstorisTheBrave/ursa-android/commit/96b0d3bbf5288db89283d5131e7bf6a0fc9cee7e))
* **m2:** opt-in self-signed TLS trust per connection ([290bb14](https://github.com/AstorisTheBrave/ursa-android/commit/290bb14511cf87c58dc2d8697d0a74287f504c4e))
* **m3:** push registration UI — distributor picker, endpoint, notif permission ([c77727b](https://github.com/AstorisTheBrave/ursa-android/commit/c77727b5867ce2c827c87ec45657f6ada84ff2b8))
* **m3:** UnifiedPush receive path — PushService, webhook parser, notifications ([9f61207](https://github.com/AstorisTheBrave/ursa-android/commit/9f61207e4d4bf9ae3c9723dfaea616609b7b43d7))
* **m4:** app shortcuts (Push, Settings) with deep-link routing ([5ead908](https://github.com/AstorisTheBrave/ursa-android/commit/5ead90867a886784a659cba531406a70b69f8dd6))
* **m4:** biometric / device-credential app lock + settings toggle ([17a39a2](https://github.com/AstorisTheBrave/ursa-android/commit/17a39a2c852b09d041104851190f5d5712aec67d))
* **m4:** home-screen widget (Glance) with cached up/down counts ([8926d23](https://github.com/AstorisTheBrave/ursa-android/commit/8926d237cc2a78ef5d9bf6561c664c20eb9921ec))
* **m4:** i18n string extraction (part 1: list, login, settings, lock, status) ([16f8f1f](https://github.com/AstorisTheBrave/ursa-android/commit/16f8f1fbbc8bfb852898022dca7aa3a8c6e9c311))
* **m4:** i18n string extraction (part 2: detail, status page, push) ([07a14fc](https://github.com/AstorisTheBrave/ursa-android/commit/07a14fc9718ff75ed282a933e58eb31d76a2e740))
* **m4:** local TLS-expiry reminders (cached cert data + daily WorkManager) ([81be906](https://github.com/AstorisTheBrave/ursa-android/commit/81be906892822f59325f4f39f80bb348415f77b0))
* **m4:** offline last-known monitor cache (encrypted, per-server) [2/2] ([0675e26](https://github.com/AstorisTheBrave/ursa-android/commit/0675e26fec37e6f49595cb9e9f2717e9829f6385))
* **m4:** Pause/Resume actions on monitor notifications ([ec6261a](https://github.com/AstorisTheBrave/ursa-android/commit/ec6261aa5e02af58606a451752a69ee1ca45c97b))
* **m4:** Quick Settings tile with cached up/down status ([8256c66](https://github.com/AstorisTheBrave/ursa-android/commit/8256c66e1c0075a51ee09e494b78fb95ad61de9e))
* **m4:** serializable monitor snapshot model + pure codec (offline cache 1/3) ([3ebbb90](https://github.com/AstorisTheBrave/ursa-android/commit/3ebbb90f5c3b7d30b68a4a0b2e50c1889dce10cc))
* **network:** Kuma domain models + Socket.IO client adapter ([a2a4269](https://github.com/AstorisTheBrave/ursa-android/commit/a2a4269eebf077172b3288a1aa2aeaba9e780db5))
* **security:** FLAG_SECURE app-wide to block screenshots/recents leakage ([bb98517](https://github.com/AstorisTheBrave/ursa-android/commit/bb98517d87d27d82f929ab9fcfc9cab01515d94b))
* **ui:** bottom navigation shell (Monitors/Notifications/Settings) + bear logo header ([8a02797](https://github.com/AstorisTheBrave/ursa-android/commit/8a027977a51cab23bb45f7d97f29f74ffa14feba))
* **ui:** Kuma-matched theme with light/dark mode and status colors ([834907d](https://github.com/AstorisTheBrave/ursa-android/commit/834907d79e137144b560ed4c49bcefd7f56b2c6c))
* **ui:** Kuma-style status pill badges + rounded card monitor list ([509e52f](https://github.com/AstorisTheBrave/ursa-android/commit/509e52f6955583dc3062784fd5a060a122829ad4))
* **ui:** login screen + live monitor list ([4c22f45](https://github.com/AstorisTheBrave/ursa-android/commit/4c22f4599e8b60544dd7b1ed16703296b740ea60))
* **ui:** monitor detail — heartbeat history + TLS cert ([ee6e218](https://github.com/AstorisTheBrave/ursa-android/commit/ee6e218160c2c35ed5dc5d24db95d687d5ce7ebb))
* **ui:** per-monitor response-time sparklines on cards (heartbeatList capture) ([a3fe1d5](https://github.com/AstorisTheBrave/ursa-android/commit/a3fe1d5cd3bb4665a119ae8a455f85ffecefb2f3))
* **ui:** public status-page viewer (Ktor) ([6e72617](https://github.com/AstorisTheBrave/ursa-android/commit/6e726172728ccd375084682f2fcb43bc3d15cdea))
* **ui:** redesigned monitor cards (status circle + name/url + pill + uptime) ([a462f3f](https://github.com/AstorisTheBrave/ursa-android/commit/a462f3f0402ca73f106e5b53b95aeb3cf0cdc9f2))
* **ui:** search + status filter in the monitor list top bar ([8666dcc](https://github.com/AstorisTheBrave/ursa-android/commit/8666dccf8ab4dec913ea35483eaa7318ed3f7d3d))


### Bug Fixes

* startup crash (repo init order), dark theme (day/night resources + Surface), FLAG_SECURE debug gate ([4326bca](https://github.com/AstorisTheBrave/ursa-android/commit/4326bca25f29100629c0b6bc6947589db9bf59b2))
