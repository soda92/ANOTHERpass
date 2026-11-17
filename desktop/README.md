# ANOTHERpass desktop (Arch Linux port)

Native desktop port of the Android app. See [`../PORTING-PLAN.md`](../PORTING-PLAN.md)
for the full architecture and phasing.

## Status

| Area | State |
|---|---|
| Model (`Secret`, `Key`, `Password`, `Encrypted`, `EncryptedType`) | ✅ ported |
| Crypto (`CryptoService`), PBKDF2 + Argon2id/i/d | ✅ ported, vector-tested |
| Vault create/unlock, v1 legacy compatibility | ✅ working |
| Wire-format compatibility with Android | ✅ tested |
| Desktop wrapping-keystore abstraction | ✅ in-memory + `0600` file fallback |
| Secret Service (libsecret) keystore | ✅ working, with safe fallback |
| SQLite storage, schema v10 | ✅ working |
| Android vault **import** (with master key) | ✅ working |
| Android vault **export** (re-importable on phone) | ✅ working |
| Compose Desktop app (create/import/unlock/list/export) | ✅ working |
| Credential CRUD (create/edit/delete/duplicate, search) | ✅ working |
| EMP QR import (image) + PIN-only unlock | ✅ working |
| HiDPI / UI scaling (auto + in-app) | ✅ working |
| Password / pseudo-phrase / PIN generator + strength | ✅ working |
| Label management | ✅ working |
| Expiry + pinned flags, clipboard auto-clear | ✅ working |
| Extension server | ⏸ deferred (Phase 5 skipped) |
| Packaging / AUR (PKGBUILD, desktop entry, AppStream, icons) | ✅ working |

## Requirements

- JDK 21 (`pacman -S jdk21-openjdk`)
- `libsecret` for the Secret Service keystore (`pacman -S libsecret`, provides
  `secret-tool`). A running keyring daemon is required — GNOME Keyring or KWallet.
- Gradle is provided via the wrapper.

## Build & test

```bash
cd desktop
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

70 tests cover crypto vectors, wire format, vault lifecycle, keystore selection and
Secret Service integration, SQLite CRUD, Android import/export round-trips,
secret generators, EMP recovery/obfuscation, stored master passwords, QR decoding,
UI scale resolution, and application-level credential/label CRUD.

## Run the app

```bash
cd desktop
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:run
```

First launch offers two paths:

1. **Create a new vault** — PIN + master password, choose Argon2id/PBKDF2 and cipher.
2. **Import from Android** — on the phone use *Export vault* and enable
   **include master key**, then select that file here.

After unlock you can browse credentials (names/users/websites/labels) and
**Export for Android…** to produce a file the phone can re-import.

### Using the vault

- **Search** filters by name, user, and website.
- **New credential / Edit** opens the editor: name, website, user, password,
  notes, expiry date (`yyyy-MM-dd`), OTP auth URI, labels, pinned.
- **Generate** opens the generator:
  - **Pseudo phrase** (human-readable, Android-compatible word generation)
  - **Password** (upper/lower/digits/specials toggles)
  - **PIN**
  - strength selection from one word up to hyper, with a live preview
- **Strength** is shown under the password field (rating + entropy bits).
- **Labels** opens the label manager; deleting a label also removes it from every
  credential that references it.
- **Duplicate** creates an independent copy.
- **Copy** puts a value on the clipboard and clears it again after 30 seconds
  (unless something else was copied meanwhile).
- Expired credentials are flagged in the list and in the detail view.
- **Export** writes an Android-importable vault file.

### EMP QR import and PIN-only unlock

If the vault export does **not** contain the master key, or you want to recover the
master password from your protected EMP backup:

1. Choose the vault JSON.
2. Choose the **EMP QR image** (for example `Your Protected Master Password.jpeg`).
3. Enter the vault PIN and the **EMP codeword**.
4. Optionally leave **Store master password for PIN-only unlock** checked.
5. Import.

The app decodes the QR with ZXing, decrypts the EMP with the vault salt, deobfuscates
it with the codeword, and verifies PIN + recovered master password against the
EMK. A wrong PIN, codeword, or mismatched EMP fails the import.

When the master password is stored, future unlocks ask only for the PIN (the
master password is unwrapped from the Secret Service). Use **Forget stored master
password** on the unlock screen to remove it and require both factors again.

> ⚠️ Storing the master password reduces the vault to “PIN + unlocked keyring”.
> This is the same trade-off as the Android app’s optional stored master password.
> Leave the checkbox off if you want to keep the two-factor property.

### UI scaling

On startup the launcher detects the desktop scale (KDE `kscreen-doctor`, GNOME
`gsettings`) and passes it to the app. The app treats it as a **target effective
scale** and computes the density relative to the actual Java/Skiko density, so it
does not double-scale.

Use the **− / +** controls on the first-run/unlock header or in the vault toolbar.
Keyboard shortcuts: **Ctrl+−** / **Ctrl++** (also Ctrl+Shift+= and the numpad
keys). The range is 75–400%; the choice is saved and the window is resized.

Overrides, highest priority first:

1. In-app button → `$XDG_CONFIG_HOME/anotherpass/ui.properties`
2. `ANOTHERPASS_UI_SCALE=200` or `$XDG_CONFIG_HOME/anotherpass/ui-scale`
3. Detected KDE/GNOME scale
4. Current system density

EMP QR images are currently read from **image files**; camera scanning is not
implemented yet.

### Where data lives

| Path | Contents |
|---|---|
| `$XDG_CONFIG_HOME/anotherpass/vault.properties` | KDF config, salt, wrapped master key, optional wrapped master password |
| `$XDG_CONFIG_HOME/anotherpass/credentials.db` | SQLite, Android schema v10 (fields are app-encrypted) |
| Secret Service | wrapping keys (`application=anotherpass`, `purpose=wrapping-key`) |
| `$XDG_CONFIG_HOME/anotherpass/wrapping-keys.properties` | `0600` fallback keystore, only when no keyring is available |

## Keystore selection

At startup the app chooses a backend:

1. `ANOTHERPASS_KEYSTORE=file` → always use the file store.
2. Existing keys in `wrapping-keys.properties` → keep using the file store.
   Switching would make the existing vault unopenable, so this is deliberate.
3. Otherwise → Secret Service (`secret-tool`) if reachable, else the file store.

Environment variables:

| Variable | Meaning |
|---|---|
| `ANOTHERPASS_KEYSTORE` | `secret-service` (or `libsecret`) / `file` |
| `ANOTHERPASS_SECRET_TOOL` | path to `secret-tool` (default: `secret-tool` on `PATH`) |

Availability is probed with a **lookup** of a nonexistent alias, which
distinguishes "no such key" from "cannot reach the keyring" without writing
secrets or unlocking the keyring at startup.

> ⚠️ There is no automatic file→Secret Service migration yet. An existing file
> vault keeps using the file store; re-import your Android export to start fresh
> with Secret Service if you want to switch.

## Android compatibility

The importer mirrors `ImportVaultUseCase`:

- `vaultId` = KDF salt (base64)
- `cipherAlgoritm` (typo is contractual) = cipher name
- `encMk` = master key encrypted only with the PIN+password passphrase key; its
  `EncryptedType` payload carries the KDF config
- `credentials` / `labels` / `usernameTemplates` = JSON arrays encrypted with the
  master secret key

Credential and label IDs are preserved on import, so the encrypted
`labels` reference list (comma-separated IDs) keeps working.

## Module layout

```
desktop/
  core/      model + crypto + generators + vault create/unlock (no Android, no UI)
  storage/   SQLite repositories, entities, Android import/export
  service/   VaultService: application-level credential/label CRUD + strength
  app/       Compose Desktop UI
  packaging/ Arch launcher, desktop entry, AppStream, icons, PKGBUILD
```

## Packaging (Arch Linux)

Fast local staging without root:

```bash
cd desktop
./packaging/make-local-dist.sh
app/build/local-dist/usr/bin/anotherpass --version
app/build/local-dist/usr/bin/anotherpass --self-test
```

Build and install the package:

```bash
cd desktop/packaging/arch
makepkg -si
```

- `:app:stageSystemDistribution` assembles only the jars + Skiko natives for a
  system-JRE install and is much faster than jpackage.
- `:app:createDistributable` still produces a self-contained jpackage app-image
  with a bundled runtime if you prefer that.
- `options=(!strip)` is required because Skiko checksums its native library.
- See [`packaging/README.md`](packaging/README.md) for AUR/`.SRCINFO` and
  packaging details.

## Known limitations (Phase 4)

- Import expects a **fresh/empty** database (first-run import). Merging into an
  existing vault is a later feature.
- OTP auth URIs are stored and round-tripped, but TOTP/HOTP codes are not
  generated yet.
- No change-master-password / change-PIN flow in the desktop UI yet.
- Expiry is shown in-app; desktop notifications for expiring credentials are not
  implemented yet.
- EMP QR images are read from files; camera scanning is not implemented yet.
- `encSeed` and app settings from Android exports are ignored.
- Blowfish is supported for old vaults but not offered for new ones.
- No file→Secret Service key migration yet (existing file vaults stay on the file
  store).
- Flatpak packaging is not provided; the PKGBUILD is a VCS (`-git`) package until
  release tags exist.
