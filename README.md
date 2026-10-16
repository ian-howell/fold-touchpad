# Fold Touchpad

Turns a Galaxy Z Fold 8 (Android 16, API 36) into a Bluetooth touchpad and keyboard
for a PC, using Android's `BluetoothHidDevice` profile. No PC-side software needed.

## Install
Every push to `main` publishes a signed APK as a GitHub Release.

**On the phone (simplest):** while signed in to GitHub in the browser, open
https://github.com/ian-howell/fold-touchpad/releases/latest/download/fold-touchpad.apk
then tap the download, allow your browser/My Files to install unknown apps, and tap **Install**
(choose **Install anyway** if Play Protect warns). Updating = the same link again;
it installs over the old version and keeps your settings.

**From a PC with adb:**
```sh
gh release download -R ian-howell/fold-touchpad -p fold-touchpad.apk --clobber
adb install -r fold-touchpad.apk
```

Releases are signed with a key stored in repo secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`);
a backup lives in `~/.config/fold-touchpad/`. Losing it means users must uninstall to update.

## First-time pairing
1. Open **Fold Touchpad** and grant the Bluetooth permissions.
2. Tap **Connect** → **Make discoverable (pair new PC)…** and accept.
3. On the PC, open Bluetooth settings → *Add device* → select the phone.
   It should appear as a keyboard/mouse.
4. Afterwards, just open the app and tap **Connect** → pick the PC
   (the PC may also reconnect on its own).

The status line at the top of the app shows registration/connection state.

## Touchpad
Unfold and use the inner screen.

| Gesture | Action |
|---|---|
| 1-finger drag | Move pointer |
| 1-finger tap | Left click |
| 2-finger tap | Right click |
| 3-finger tap | Middle click |
| 2-finger drag | Scroll (vertical + horizontal) |
| Tap, then tap-and-hold + drag | Click-drag |

The **Left** / **Right** buttons at the bottom can also be held for dragging.

## Keyboard
Two modes, switched with the **Mode** button on the toolbar (remembered between launches):

- **Staged** (default, like Termux): a text box sits above the touchpad. Type locally with
  full autocorrect, swipe, voice input or paste, then:
  - **Send** types the text on the PC;
  - the keyboard's **Enter/Send** key types the text *and* presses Enter.
- **Live:** every keystroke goes straight to the PC (good for shortcuts, games, terminals).

Common to both:
- **⌨** opens the phone's keyboard for the current mode.
- **Ctrl / Alt / Shift / Win** latch for the next key only (e.g. Ctrl, then `c` = copy, in Live mode).
- **Esc, Tab, arrows, Del** are on the toolbar (scrolls horizontally).
- Mapping assumes a US layout on the PC; characters with no US key (emoji, accents) are skipped.

## Settings
Tap **⚙** on the toolbar:
- **Pointer speed** and **Scroll speed** sliders (0.2×–4.0×). Changes apply and save immediately.
- **Typing delay** (0–30 ms, default 0): pause after each key report. Lower = faster typing;
  raise it if letters repeat or go missing on the PC.
- **Reset** restores defaults (pointer 1.6×, scroll 1.0×, typing delay 0 ms, link best-effort).
- **Bluetooth link mode** (QoS best-effort / guaranteed / none): restart the app after changing.
  If keys repeat, try another mode. The status line shows reports/s and busy-retries while typing.

## Troubleshooting
- **Won't connect:** forget the pairing on both the PC and the phone, then pair again.
- **Pairing never works at all:** some Samsung firmware restricts the HID Device profile;
  note the status-line text when reporting it.
- **Letters repeat ("Thiiiis") or go missing:** raise **Typing delay** in ⚙.
- **Garbled or duplicated text in Live mode:** turn off predictive text in Samsung Keyboard, use Gboard, or use Staged mode.
- **Pointer/scroll too fast or slow:** adjust in **⚙** settings.

## Building locally
Requires JDK 17 and the Android SDK (platform 36), plus Gradle 8.14:

```sh
gradle :app:assembleDebug     # debug APK (different signature than releases) in app/build/outputs/apk/debug/
gradle :app:installDebug      # build + install to a connected phone
```
