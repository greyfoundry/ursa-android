# Released persistence contracts

These synthetic fixtures freeze formats written before the operations roadmap.
They contain no production credentials or user data.

| Store | Container | Persisted contract |
|---|---|---|
| Connections | DataStore `ursa` | `connections` is an encrypted JSON list; `active_url` is a URL string |
| Portable backup | exported JSON document | envelope v1 with PBKDF2-HMAC-SHA256 and AES-GCM; decrypted payload v1 or v2 |
| Monitor cache | DataStore `ursa_monitor_cache` | encrypted `MonitorSnapshot` JSON keyed by normalized server URL |
| Alert modes | SharedPreferences `ursa_push_alert_modes` | scoped mode plus `severity:`, `timing:`, and `snooze:` keys |
| Quiet hours | SharedPreferences `ursa_push_quiet_hours` | `enabled`, `start_minute`, `end_minute`, and `days_mask` |
| Pending alerts | SharedPreferences `ursa_pending_push_alerts` | encrypted JSON under `alert:<uuid>` and an `active:<serverId>:<monitorId>` pointer |
| Lock | SharedPreferences `ursa_lock` | boolean `enabled` |
| Widgets | DataStore `ursa_widgets` | encrypted `WidgetConfig` under `config_<widgetId>` and public snapshots under `page_<pageId>` |
| Wear pairing | SharedPreferences `ursa_wear` | encrypted v1 pairing JSON under `paired_session` |

`remote_mutations_v1.tsv` records every current remote write, its proposed access
capability, wire event, and known entry points. `intent_contracts_v1.tsv` freezes
released deep links, notification actions, channel IDs, and shortcut IDs before
the navigation and access-policy migrations.

Do not update an existing fixture to make a new format pass. Add a new versioned
fixture and keep the released one as an upgrade input.

Payload v2 adds explicit per-connection access profiles and custom capability
selections. Imports preserve a matching local connection's access settings by
default; restoring profiles from the backup requires a separate confirmation.
