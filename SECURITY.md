# SECURITY.md — StarSmash Kids Security Posture

## Summary

StarSmash Kids is a local-only, offline Android app with a minimal attack surface.
It collects no data, communicates with no servers, and requires no sensitive permissions.

---

## Permissions

| Permission | Status | Purpose |
|---|---|---|
| `VIBRATE` | Declared, optional | Haptic feedback on touch events |
| `WAKE_LOCK` | Declared | Keep screen awake during play sessions |
| `INTERNET` | **NOT declared** | Not needed; no network features |
| `CAMERA` | **NOT declared** | Not needed |
| `RECORD_AUDIO` | **NOT declared** | Not needed |
| `READ/WRITE_EXTERNAL_STORAGE` | **NOT declared** | Not needed |
| `ACCESS_FINE_LOCATION` | **NOT declared** | Not needed |
| `READ_CONTACTS` | **NOT declared** | Not needed |

The absence of `INTERNET` means the app **cannot make network requests of any kind**,
regardless of any future code changes, unless the permission is explicitly added to
`AndroidManifest.xml`.

---

## Data Collection

**None.**

- No analytics SDK (no Firebase, Crashlytics, Sentry, Mixpanel, etc.)
- No advertising SDK (no AdMob, Meta Audience Network, etc.)
- No crash reporting
- No event logging
- No user identifiers created or stored

---

## Local Storage

The only data stored on-device is the user's settings, saved to Android
`SharedPreferences` under the key `starsmash_settings`. These settings contain:

- Boolean toggle states (sound on/off, haptics on/off, etc.)
- Enum selections (theme, sound mode, intensity)

No personally identifiable information is stored. No child usage data is stored.

---

## Secrets in Repository

**No secrets are committed to this repository.**

- No API keys
- No OAuth credentials
- No signing keystores
- No `.env` files

Release signing credentials must be stored externally (environment variables or
a credentials manager) and never committed to version control.

---

## Attack Surface

The attack surface of this app is intentionally minimal:

| Vector | Status |
|---|---|
| Network | No network access — zero attack surface |
| IPC (Intents) | One exported Activity (`MainActivity`), standard launcher intent only |
| Content Providers | None |
| Services | None |
| Broadcast Receivers | None |
| WebViews | None |
| JavaScript | None |
| External storage | Not accessed |
| Camera / microphone | Not accessed |

---

## Dependency Hygiene

Third-party dependencies are limited to well-maintained AndroidX and Jetpack libraries
managed via Compose BOM. To check for known vulnerabilities:

```bash
# Gradle dependency audit (if dependency-analysis plugin is added)
./gradlew buildHealth

# Manual check: review libs.versions.toml against https://deps.dev or https://osv.dev
```

Recommended practice: update to latest patch versions of all dependencies at least
quarterly. Use Dependabot or Renovate for automated PR creation.

---

## Responsible Disclosure

If you discover a security issue in this project, please report it by opening a
GitHub Issue marked `[SECURITY]`, or contact the maintainer directly via the
profile linked in the repository.

Given the app has no network access and no user data, the most likely security
concerns would be:

1. A malicious third-party library dependency (supply chain attack)
2. A future PR adding a permission or network call without review

Both are mitigated by the minimal dependency footprint and the permission policy
described in this document.
