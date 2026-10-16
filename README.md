# Fold Touchpad

Turns a Galaxy Z Fold 8 (Android 16, API 36) into a Bluetooth touchpad and keyboard
for a PC, using Android's `BluetoothHidDevice` profile. No PC-side software needed.

## Use
1. Install and open the app; grant the Bluetooth permissions.
2. Tap **Connect** → *Make discoverable*, then pair "your phone" from the PC's Bluetooth settings.
   Already-paired PCs can be picked directly from the list.
3. Unfold and use the inner screen as a trackpad.

### Gestures
| Gesture | Action |
|---|---|
| 1-finger drag | Move pointer |
| 1-finger tap | Left click |
| 2-finger tap | Right click |
| 3-finger tap | Middle click |
| 2-finger drag | Scroll (vertical + horizontal) |
| Tap, then tap-and-hold + drag | Click-drag |

Bottom **Left/Right** buttons can be held for dragging.

### Keyboard
Tap **⌨** to open the phone's keyboard; keystrokes go to the PC. Ctrl/Alt/Shift/Win
toggles latch for the next key (e.g. Ctrl then `c`). Esc, Tab, arrows and Del are in the toolbar.
Mapping assumes a US layout on the PC.

## Build
Requires JDK 17 and the Android SDK (platform 36).

```sh
gradle wrapper   # once, if gradlew is missing
./gradlew installDebug
```
