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
| Password / pseudo-phrase / PIN generator + strength | ✅ working |
| Label management | ✅ working |
| Expiry + pinned flags, clipboard auto-clear | ✅ working |
| Extension server | ⬜ Phase 5 |
| Packaging / AUR | ⬜ Phase 6 |

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

56 tests cover crypto vectors, wire format, vault lifecycle, keystore selection and
Secret Service integration, SQLite CRUD, Android import/export round-trips,
secret generators, and application-level credential/label CRUD.

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

### Where data lives

| Path | Contents |
|---|---|
| `$XDG_CONFIG_HOME/anotherpass/vault.properties` | KDF config, salt, wrapped master key |
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
```

## Known limitations (Phase 4)

- Import expects a **fresh/empty** database (first-run import). Merging into an
  existing vault is a later feature.
- OTP auth URIs are stored and round-tripped, but TOTP/HOTP codes are not
  generated yet.
- No change-master-password / change-PIN flow in the desktop UI yet.
- Expiry is shown in-app; desktop notifications for expiring credentials are not
  implemented yet.
- `encSeed` and app settings from Android exports are ignored.
- Blowfish is supported for old vaults but not offered for new ones.
- No file→Secret Service key migration yet (existing file vaults stay on the file
  store).
