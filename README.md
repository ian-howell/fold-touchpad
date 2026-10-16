# Fold Touchpad

Turns a Galaxy Z Fold 8 (Android 16, API 36) into a Bluetooth touchpad and keyboard
for a PC, using Android's `BluetoothHidDevice` profile. No PC-side software needed.

## Install
Every push builds a debug APK in GitHub Actions.

1. Download the latest build:
   ```sh
   gh run download -R ian-howell/fold-touchpad -n fold-touchpad-debug
   ```
   (or: repo → **Actions** → latest run → **Artifacts** → `fold-touchpad-debug`)
2. Install `app-debug.apk` on the phone:
   - USB: `adb install app-debug.apk`, or
   - copy the file to the phone and open it (allow "install unknown apps" when prompted).

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
- Tap **⌨** to open the phone's keyboard; keystrokes go to the PC.
- **Ctrl / Alt / Shift / Win** toggles latch for the next key only (e.g. Ctrl, then `c` = copy).
- **Esc, Tab, arrows, Del** are on the toolbar (scrolls horizontally).
- Mapping assumes a US layout on the PC.

## Settings
Tap **⚙** on the toolbar:
- **Pointer speed** and **Scroll speed** sliders (0.2×–4.0×). Changes apply and save immediately.
- **Reset** restores defaults (pointer 1.6×, scroll 1.0×).

## Troubleshooting
- **Won't connect:** forget the pairing on both the PC and the phone, then pair again.
- **Pairing never works at all:** some Samsung firmware restricts the HID Device profile;
  note the status-line text when reporting it.
- **Garbled or duplicated text:** turn off predictive text in Samsung Keyboard, or use Gboard.
- **Pointer/scroll too fast or slow:** adjust in **⚙** settings.

## Building locally
Requires JDK 17 and the Android SDK (platform 36), plus Gradle 8.14:

```sh
gradle :app:assembleDebug     # APK in app/build/outputs/apk/debug/
gradle :app:installDebug      # build + install to a connected phone
```
