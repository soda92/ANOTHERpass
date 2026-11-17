# Packaging (Arch Linux)

This directory contains everything needed to package the desktop port for Arch
Linux, plus a local staging script for testing without `makepkg`.

## Contents

| File | Purpose |
|---|---|
| `anotherpass.sh` | Installed launcher (system JRE, sets Skiko library path) |
| `anotherpass.desktop` | Desktop entry |
| `anotherpass.metainfo.xml` | AppStream metadata |
| `anotherpass-*.png` | Icons at 16–512 px |
| `make-local-dist.sh` | Build a staged tree under `app/build/local-dist` |
| `arch/PKGBUILD` | Arch VCS package (`anotherpass-desktop-git`) |

## Local staged build (no root)

```bash
cd desktop
./packaging/make-local-dist.sh
app/build/local-dist/usr/bin/anotherpass --version
app/build/local-dist/usr/bin/anotherpass --self-test
```

This uses the `:app:stageSystemDistribution` Gradle task, which copies only the
runtime jars and the Skiko native library. It does **not** run jpackage or build
a JRE image, so it is much faster than `createDistributable`.

Run the staged app directly (opens a window):

```bash
app/build/local-dist/usr/bin/anotherpass
```

## Build and install the Arch package

```bash
cd desktop/packaging/arch
makepkg -si
```

Then launch `anotherpass` from your application menu or a shell.

To build without running the test suite:

```bash
makepkg -si --nocheck
```

## AUR submission

Generate the metadata file from the PKGBUILD:

```bash
cd desktop/packaging/arch
makepkg --printsrcinfo > .SRCINFO
```

Then push `PKGBUILD` and `.SRCINFO` to an AUR repository named
`anotherpass-desktop-git`.

## Packaging notes

- **System JRE, not bundled.** `:app:stageSystemDistribution` copies the runtime
  classpath and Skiko natives into `app/build/system-dist`; the package installs
  those under `/usr/lib/anotherpass/lib` and depends on `jre21-openjdk`.
- **`options=(!strip)` is required.** Skiko verifies `libskiko-linux-x64.so`
  against its `.sha256` sidecar. Stripping the library changes the hash and the
  app refuses to start.
- **HiDPI.** The launcher detects KDE `kscreen-doctor` / GNOME `gsettings` scale
  and exports it to the app. Override with `ANOTHERPASS_UI_SCALE` or
  `~/.config/anotherpass/ui-scale`; the in-app UI button persists a per-user
  choice in `ui.properties`.
- **Network during build.** The pinned Gradle wrapper downloads Gradle 8.12 and
  resolves Kotlin/Compose dependencies from Maven Central. `build()` needs network
  access unless the wrapper and dependency caches are pre-seeded.
- **Secret Service.** `libsecret` provides `secret-tool`; a keyring daemon
  (`gnome-keyring` or `kwallet`) must be running for the keystore.
- **Self-contained alternative.** `./gradlew :app:createDistributable` still
  produces a jpackage app-image with a bundled runtime at
  `app/build/compose/binaries/main/app/anotherpass`. Install that under `/opt`
  and symlink its launcher if you do not want the `jre21-openjdk` dependency;
  it adds ~100 MB.
- **Flatpak** is not provided yet.
