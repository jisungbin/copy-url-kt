# CopyUrl

A tiny macOS menu-bar utility that copies the current **Google Chrome** tab URL with a global hotkey — and strips tracking parameters when you double-tap.

No Compose, no AWT — a pure **Kotlin/JVM** app driving AppKit/Carbon through **JNA**, packaged as a self-contained `.app` with `jpackage`.

## Features

- **⇧⌘C** — copy the current Chrome tab's full URL
- **⇧⌘C ⇧⌘C** (double-tap within 0.5s) — copy with tracking parameters stripped (`utm_*`, `gclid`, `fbclid`, `igshid`, …)
- Menu-bar only (no Dock icon), native macOS notification on copy
- Launches at login

## How it works

| Concern | Implementation |
|---|---|
| Global hotkey | Carbon `RegisterEventHotKey` via JNA — **no Accessibility permission** needed |
| Read Chrome URL | AppleScript (`osascript`) — Apple Events |
| Strip trackers | Pure Kotlin URL parsing (whitelist of known trackers) |
| Clipboard | `pbcopy` |
| Menu-bar icon | Native `NSStatusItem` + SF Symbol (`link`) via the Objective-C runtime |
| Notification | `NSUserNotification` (falls back to `osascript`) |

> A menu-bar (UI) element is required: macOS only delivers global hotkey events to an app that has some UI presence, so the `NSStatusItem` isn't just decoration — it keeps the event pump alive.

## Build & install

```bash
./build_app.sh
```

Compiles → packages a self-contained `.app` (bundled JRE) with `jpackage` → **self-signs** it → installs to `/Applications` → launches.

### Code signing (important)

The app is **self-signed**, not notarized. Ad-hoc signing is *not* enough — macOS won't grant Automation (Apple Events) permission to an ad-hoc binary, and it won't even show up in System Settings → Privacy → Automation. So `build_app.sh` re-signs with a local certificate named **`CopyUrl Self-Signed`**.

Create it once (Keychain Access → Certificate Assistant → *Create a Certificate…* → Code Signing, self-signed), or via CLI:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -keyout cu.key -out cu.crt -days 3650 \
  -subj "/CN=CopyUrl Self-Signed" \
  -addext "extendedKeyUsage=critical,codeSigning"
security import cu.key -k ~/Library/Keychains/login.keychain-db -T /usr/bin/codesign -A
security import cu.crt -k ~/Library/Keychains/login.keychain-db -T /usr/bin/codesign -A
```

### First run

- On the first copy, macOS asks **"CopyUrl wants to control Google Chrome" → Allow** (needed to read the tab URL).
- Add `CopyUrl.app` to **System Settings → General → Login Items** to launch at login.

## Customizing the tracker list

Edit `src/main/kotlin/dev/jisungbin/copyurl/TrackerCleaner.kt`:

- `EXACT` — exact-match keys (`gclid`, `fbclid`, …)
- `PREFIXES` — prefix-match (`utm_`, `pk_`, …)

Only whitelisted trackers are removed; normal params (`id`, `q`, `page`, …) are never touched.

## Project layout

```
Main.kt            entry point · hotkey wiring · double-tap detection
CarbonHotkey.kt    JNA → Carbon RegisterEventHotKey
MacStatusBar.kt    NSStatusItem + SF Symbol + NSMenu (objc via JNA)
ObjC.kt            Objective-C runtime helper (objc_msgSend)
ChromeUrl.kt       current Chrome tab URL via osascript
TrackerCleaner.kt  strip tracking query params
ClipboardUtil.kt   pbcopy
Notifier.kt        native notification (+ osascript fallback)
Dbg.kt             file logger (~/copyurl-debug.log)
```

## Stack

Kotlin 2.4.0-RC2 · JNA 5.18.1 · Gradle 9.6.0-rc-1 · JDK 21 · `jpackage`

## License

MIT
