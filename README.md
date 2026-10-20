# Pixel Strip

An 8-bit style drag racing game for Android, built with libGDX and Kotlin. The world renders
into a 270 px tall framebuffer (480 to 640 px wide, following the screen's aspect ratio) with
nearest-neighbour pixels, then post-processing adds bloom, a per-track colour grade (LUT),
chromatic aberration and an optional CRT filter.

All art is generated in code by `tools/gen_art.py`: no external assets.

## Status

The original 8-phase plan was folded into a bigger one (multiplayer, crews, purchases come later).
Done so far:

- Neon City, the race, physics, lighting and effects (original phases 1-3)
- All 24 cars, each generated from a sprite profile, with stock stats checked against their real
  quarter-mile times (`RosterCalibrationTest`)
- Title screen, first-launch starter pick, garage, dealership, settings, local save, pixel
  transitions (dissolve, wipe, garage door)

- Parts and upgrades (14 slots, Stage 1-3 + Race), visible on the cars (wings, kits, scoops,
  blowers, intercoolers, side pipes, rims, underglow)
- Paint shop: 12 colours, gloss/metallic/pearl/matte/candy finishes, decals
- Deep tuning: every gear, final drive, launch control, nitrous timing, tyre pressure; neon gear
  chart; 4 presets per car; share codes via the clipboard
- Test-n-tune runs with a run log, and a dyno with before/after pulls

- Career: a city map with four tiers (Street, Sport, Pro, Outlaw) of three rivals and a boss each;
  VS cards, boss intro scenes, purses, and the boss's car as a pink-slip prize. Street boss is Joe
  "Ground Control" in his EF Civic hatch, Sport boss is Rotor in a black FC RX-7.

Next: the remaining tracks, weather and audio.

### How to race

| Touch | Desktop | What |
|---|---|---|
| GAS (right) | Up / W | Hold: burnout in the box, rev while staged, throttle in the run |
| Left button | Space | STAGE after the burnout, LAUNCH on green, SHIFT during the run |
| NOS | N | Spray (best once the tyres hook, 2nd gear on) |
| 1/8 MILE | T | Toggle 1/8 or 1/4 mile before staging |

Rev into the tach's green zone while the ambers count down. Too low bogs, too high spins.
The tach shows each gear's good (green) and perfect (pink) shift zone; perfect shifts are
faster and give a short power bonus. Leaving the beam before the green is a red light.

## Requirements

- JDK 17 (Gradle runs on it; set `org.gradle.java.home` in `~/.gradle/gradle.properties` if your
  default Java is newer)
- Android SDK with platform 35 (`local.properties`: `sdk.dir=/path/to/Android/Sdk`)
- Python 3 with Pillow and numpy, only to regenerate the art

## Build

```sh
python3 tools/gen_art.py            # regenerate every PNG in assets/ (and the launcher icon)
./gradlew android:assembleDebug     # -> android/build/outputs/apk/debug/android-debug.apk
./gradlew desktop:run               # play on the desktop (Space or click = gas)
```

## Install on a phone

With USB debugging on:

```sh
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

Or copy the APK to the phone and open it (allow installs from that app when asked).
Needs Android 8.0 (API 26) or newer.

## Save files

Android keeps `save.json` in the app's private storage. The desktop build uses
`~/.local/share/pixel-strip/save.json` (screenshot runs use a separate throwaway save).

## Desktop screenshot mode

The desktop build can autoplay a race and save frames, which is how the art gets checked
(`--close` matches the ghost to the autoplay driver, for photo finishes; `--car ID` picks the car;
`--screen title|starter|garage|dealer|settings` captures a menu screen instead):

```sh
./gradlew desktop:installDist
cd assets && ../desktop/build/install/desktop/bin/desktop --size 1280x720 --shots /tmp/shots 0.5,3.4,9
```

## Layout

| Path | What |
|---|---|
| `tools/gen_art.py`, `tools/art/` | Art generator: 48-colour palette (`common.py`), cars (`car.py`), paint ramps, tracks, font/UI, icon |
| `assets/data/cars/*.json` | Car stats and sprite anchors |
| `assets/data/tracks/*.json` | Track layers, parallax, props, lights, grade |
| `assets/shaders/` | Paint (ramp lookup, normal-mapped point lights, hologram), tint (lit trim), blur, composite (zoom, heat haze, upscale, bloom, LUT, CA, photo print, UI layer, CRT) |
| `assets/data/economy.json` | Starting cash, sell rate, starter cars, performance classes |
| `assets/data/career.json` | Career tiers, rivals (car, build, paint, AI skill, purse, map spot), boss intros |
| `tools/art/portraits.py` | Rival portraits, generated from a few parameters |
| `assets/data/parts.json` | Part slots, tiers and effects, prices, cosmetics, paint finishes, decals |
| `core/` | The game: `gfx/` (post-processing, font), `world/` (track renderer), `race/` (race flow, `Vehicle` physics, `DragMath`, tree, HUD, particles, AI driver) |
| `core/src/test/` | Physics tests: launch/bog/spin/shift/nitrous/burnout/red-light behaviour (`./gradlew core:test`) |
| `android/`, `desktop/` | Launchers |
| `car_references.md` | Internal: fictional cars -> real inspirations and stock specs |

### Car sprites

Cars are layered so upgrades and paint can change them: `wells`, `wheel` (4 rotation frames +
3 motion-blur frames), `body` (paint ramp indices, not colours: the paint shader maps them
through a row of `paint/ramps.png`), `trim` (glass, chrome, rubber) and `lights` (emissive).
