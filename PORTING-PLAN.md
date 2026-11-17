# ANOTHERpass → Arch Linux (native desktop) porting plan

Status: **Phase 3 complete** — JVM core, SQLite storage, Android import/export, Secret Service keystore. Phase 4 next.

## Goal

Ship a native Arch Linux application (AUR + PKGBUILD, optionally Flatpak) that:

- creates/unlocks the same vault format as the Android app,
- imports vault exports produced by the Android app,
- offers credential management, generated passwords/username templates, labels, expiring credentials,
- runs the existing Ktor credential server for the browser extension,
- integrates with the Linux desktop (Secret Service, system tray, clipboard policy).

Out of scope for v1: NFC credential storage (no cheap desktop equivalent), Android autofill (replaced by the browser extension + a Wayland portal/clipboard helper), biometric unlock (replaced by Secret Service / PAM / optional FIDO2).

## Architecture

Multi-module Gradle build under `desktop/`:

```
desktop/
  core/     # pure JVM: model, crypto, kdf, vault codec, password generation
  storage/  # SQLite (JDBC) repositories, vault file codec, import/export
  server/   # Ktor extension server (ported nearly as-is)
  app/      # Compose Desktop UI
  packaging/# PKGBUILD, desktop entry, icon, systemd user unit
```

Dependency direction: `app → storage → core`, `app → server → core`.
`core` must have **no** Android or UI imports.

### UI toolkit: Compose Multiplatform (Compose Desktop)

Chosen because it keeps the whole stack in Kotlin, reuses the ported model/usecase
layer directly, and packages cleanly on Arch via `jpackage` (app-image) or a
native launcher + JRE dependency.

## Android → Linux mapping

| Android mechanism | Linux replacement |
|---|---|
| `AndroidKeyStore` (AES-GCM wrapping keys `MK`, `MP`, `SALT`, `TRANSPORT`, RSA server key) | Secret Service via `libsecret` (GNOME Keyring / KWallet), with a file-backed fallback (0600) and optional kernel keyring session keys |
| Room / `SupportSQLiteDatabase` | SQLite via JDBC (Xerial `sqlite-jdbc`); same schema, migrations tracked by `user_version` |
| SharedPreferences (encrypted) | JSON settings file (0600) in `$XDG_CONFIG_HOME/anotherpass`, secrets wrapped with the Secret Service key |
| `EncryptedSharedPreferences` | same as above |
| BiometricPrompt / Keyguard | Secret Service unlock, PAM (`pam_authenticate`) or optional FIDO2 |
| QR scan (zxing-android) | camera capture via PipeWire/V4L2 + `zxing` decode, or import image file |
| NFC tags | not ported in v1 (document alternative: QR) |
| Autofill service | browser extension + optional `wl-clipboard`/`xdotool` typing helper |
| WorkManager expiry notifications | libnotify (`notify-send`/D-Bus) + systemd user timer |
| `Intent`/Activity navigation | Compose navigation |
| Clipboard | `wl-copy` / `xclip`, with clear-after timeout; honor "no insecure clipboard" setting |
| Ktor Netty server | unchanged (JVM) |
| `argon2kt` (JNI) | BouncyCastle `Argon2BytesGenerator` (identical output for the same params) |
| `kotpass` KDBX | unchanged (pure Kotlin) |

## Compatibility contract (must not change)

These byte-level details are what makes vaults interchangeable with Android:

- `Encrypted` wire format: `type:ivBase64:dataBase64:cipherOrdinal`, base64 **no wrap, no padding**.
- `CipherAlgorithm` **enum order** is serialized by ordinal:
  `AES_128, AES_256, BLOWFISH_128, BLOWFISH_256, CHACHACHA20`.
- `KeyDerivationFunction` ids: `p, a, i, d` (PBKDF2, Argon2id, Argon2i, Argon2d).
- `KdfConfig` encoding: `<id><base64-int iterations>_<base64-int memMiB>` with leading `A` trimmed.
- Master passphrase = `SHA-256(salt || pinBytes || masterPasswordBytes)`.
- Master key: 128 random bytes; vault data key = raw master key (v2) or PBKDF2(masterKey, salt, 65536) (v1).
- AES key from Argon2 output: first `keyLength/8` bytes.
- Argon2: parallelism 4, `mCost` passed as KiB (`MiB * 1024`), output length = 32 bytes / cipher key.
- PBKDF2 password encoding quirk: bytes → chars 1:1 (`byte.toChar()`), then JCE UTF-8 encoding. Ported verbatim.

## Migration from Android

Android keys are hardware/non-exportable, so the desktop app **cannot** read the
Android preferences/database directly. Import path:

1. On Android: *Export vault* **with master key** → JSON containing `encMk`
   (master key encrypted only with the master-passphrase key; KDF config is in the
   `EMK` type payload).
2. On desktop: open the JSON, ask for PIN + master password, derive the passphrase
   key, unwrap `encMk`, then decrypt the `credentials`/`labels`/`usernameTemplates` blobs.
3. Re-wrap the master key with a new desktop Secret Service key.

This is exactly what `ImportVaultUseCase` does internally; the desktop port
reuses the same JSON keys.

## Phases

1. **Core (done)** — port model + crypto + KDF + password generators; golden
   tests for `Encrypted` wire format, PBKDF2 and Argon2 vectors.
2. **Storage (done)** — SQLite repositories, schema parity with `YapmDatabase` v10,
   import/export (JSON vault).
3. **Desktop keystore (done)** — Secret Service provider via `secret-tool`, safe
   file fallback, no-lockout selection rules, tests.
4. **UI (next)** — Compose Desktop: unlock/create vault, credential list/detail, labels,
   generator, settings, import/export, expiry reminders.
5. **Extension server** — port `service/net` to `server` module.
6. **Packaging** — `jpackage` app-image, `.desktop`, icons, PKGBUILD, AUR, optional Flatpak.

## Risks

- **Crypto drift** — mitigated by golden-vector tests and by round-tripping real
  Android exports during Phase 2.
- **Secret Service availability** — headless/SSH sessions have no keyring; need a
  documented fallback and a clear security trade-off.
- **Wayland clipboard/typing** — compositor-specific; keep the browser extension as
  the primary integration.
- **Blowfish is legacy** — SunJCE still provides `Blowfish`; keep it for old vaults,
  discourage for new ones.
- **Single-maintainer Android app with few tests** — the port will own its test suite.
