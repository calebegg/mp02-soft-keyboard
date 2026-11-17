# MP02 Keyboard

An Android `InputMethodService` replicating the physical keyboard layout, dimensions, and **Onyx** / **Pearl** finishes of the **Minimal Phone 2 (MP02)**.

## Layout & Hardware Proportions

Calibrated directly from Minimal Phone 2 schematics (`72.0 × 122.6 mm` body):
- **37-Key Matrix (`10 + 10 + 10 + 7`)**:
  - Row 1: `Q(#) W(1) E(2) R(3) T(() Y()) U(_) I(-) O(+) P(@)`
  - Row 2: `A(*) S(4) D(5) F(6•) G(/) H(:) J(;•) K(') L(") ⌫`
  - Row 3: `⇧ Z(7) X(8) C(9) V(%) B(?) N(!) M(,) .($) ↵`
  - Row 4: `123  ctrl(0)  🔍  [ Spacebar ]  ⁘  alt(☺)  123`
- **Physical Scaling**: `5.33 × 5.33 mm` superellipse keycaps (`exp = 2.95`), `1.50 mm` gaps (`6.83 mm` pitch), `17.50 mm` spacebar, and `2.24 mm` top bezel, dynamically scaled to the screen's physical `xdpi`/`ydpi` (renders at ~97% true 1:1 physical scale on a Pixel 10 Pro).
- **Finishes**: **Onyx**, **Pearl**, and **Auto** (follows system dark/light theme), with sculpted bevel catchlights, `F`/`J` homing pips, Minimal Red state micro-LEDs, and pressed-key backlight flare.

## Input Behavior

- **Multi-touch Chording & Rollover**: Hold `Shift`, `alt`, or `ctrl` with one thumb while typing with the other; fast two-thumb rollover commits characters in press order.
- **Numbers & Symbols**: Tap `alt` (one-shot), double-tap `alt`, tap `123` (lock), or hold any character key for its printed upper-left symbol.
- **`ctrl` Shortcuts**: `ctrl` + `A/C/X/V/Z/Y`, Vim `H/J/K/L` arrows, `⌫` delete word, and raw `META_CTRL_ON` `KeyEvent`s in terminal fields (`TYPE_NULL`).
- **Trackpad Gestures**: Slide horizontally across the spacebar to scrub the cursor (`Shift` + slide to select); swipe left from `⌫` to delete a word; long-press spacebar to switch system keyboards.
- **Emoji Deck**: Hold `alt` for the inline emoji grid; `⁘` is left visually intact and functionally unassigned.

## Build & Install

```bash
./build.sh
./install.sh
# Or over wireless ADB:
MP02_ADB_TARGET=<ip>:<port> ./install.sh
```

## Privacy & License

- Requests only `android.permission.VIBRATE` (no network, storage, or telemetry permissions).
- Licensed under the [MIT License](LICENSE).

