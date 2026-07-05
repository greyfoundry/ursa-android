# Changelog

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
