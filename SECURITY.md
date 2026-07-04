# Security Policy

URSA is a client that connects to your own Uptime Kuma servers. It stores as
little as possible and encrypts credentials at rest. The full security posture,
mapped to the OWASP Mobile Application Security Verification Standard (MASVS), is
documented in [docs/security.mdx](docs/security.mdx).

## Reporting a vulnerability

Please report security issues privately. Do not open a public issue, pull
request, or discussion for a suspected vulnerability.

1. Preferred: open a private report through GitHub Security Advisories
   ("Report a vulnerability" under the repository's Security tab).
2. Alternatively, email callmeSage0@proton.me with details.

Please include:

- A description of the issue and its impact
- Steps to reproduce, or a proof of concept
- Affected version (app `versionName` / commit) and Android version

We aim to acknowledge reports promptly and will keep you updated on remediation.
Coordinated disclosure is appreciated; please give us reasonable time to fix an
issue before any public disclosure.

## Scope

In scope:

- The URSA Android app in this repository (credential storage, session handling,
  network/TLS behavior, push handling, exported components).

Out of scope:

- Vulnerabilities in Uptime Kuma itself (report those to the
  [upstream project](https://github.com/louislam/uptime-kuma)).
- Issues in third-party UnifiedPush distributors (e.g. ntfy) or in the user's own
  server or network configuration.
- Automated scanner output for upstream dependencies without a demonstrated,
  practical impact on URSA. Dependency advisories are tracked via Dependabot.

## Supported versions

URSA is in active development before its first tagged release. Security fixes land
on `main` and in the latest release. Please reproduce on the latest version before
reporting.
