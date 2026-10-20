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

- Parts: 46 slots and over a hundred real parts (`tools/make_parts.py` documents every effect).
  Cams, heads and manifolds reshape the torque curve; turbos come in sizes (spool vs top end),
  superchargers in types, nitrous in shot sizes; boost and nitrous are held back by the knock
  limit (bottom end, fuel octane, timing, air/fuel) and the fuel system's capacity, so big power
  needs the supporting parts. Clutches, flywheels, gearboxes, diffs, suspension (squat, shock
  speed, wheelie bars), tyres, weight reduction and body aero. Bought parts stay with the car, so
  swapping back is free. Joe's EF has two parts nothing else can take (an AWD conversion and a
  K-series swap) and, built right, is the quickest Street-class car (`PotentialTest`).
- Paint shop: 12 colours, gloss/metallic/pearl/matte/candy finishes, decals
- Deep tuning in five pages: gears and final drive; ignition timing, air/fuel, rev limit, launch
  control; turbo and blower boost and boost by gear; nitrous shot, timing and ramp; tyre pressure,
  ride height and shocks. The ECU and other parts decide which of these unlock. Neon gear chart
  and a live power curve that marks knock and fuel limits; 4 presets per car; share codes
- Test-n-tune runs with a run log, and a dyno with before/after pulls

- Career: a city map with four tiers (Street, Sport, Pro, Outlaw) of three rivals and a boss each;
  VS cards, boss intro scenes, purses, and the boss's car as a pink-slip prize. Street boss is Joe
  "Ground Control" in his EF Civic hatch, Sport boss is Rotor in a black FC RX-7.

- Six tracks: Neon City, Sundown Strip (desert), Dockside (rain, wet reflections, lightning),
  Sunset Coast, Summit Airfield (snow), Dusk Speedway '77; each with its own grip, weather and
  song. Joe's boss race has a seventh, San Antonio, which is his alone.
- Audio, all synthesized: engines from the live physics (per-cylinder firing, turbo, blower,
  limiter, nitrous, tyre squeal), effects, crowd, and chiptune music per screen and track
- Cloud save (Firebase): anonymous accounts, and every cash or garage change checked by the
  server (see below)

- Online races (Firebase): matchmaking by build, the opponent shown live, and both runs
  replayed by the server to decide the winner (see below)

Next: bet and pink-slip races online.

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

## Cloud save (Firebase)

Every change to cash or the garage (buying, upgrading, painting, selling, career races) is a
ledger entry (`core/.../save/Ledger.kt`). The game applies it at once and queues it. The `sync`
Cloud Function (`server/functions/src`) replays the queue with the same rules, refuses anything
that breaks them (wrong prices, locked rivals, races closer than 6 s apart, timestamps from the
future) and returns the state the game must adopt. Firestore rules deny every direct client
read and write; only the function writes, through the Admin SDK.

Both sides read the same price table, `assets/data/rules.json`, generated from the game data
(`LedgerTest` fails if it's stale: `REGEN=1 ./gradlew core:test --tests '*LedgerTest*'`). Both
run the same cases, `shared/ledger_cases.json` (written by `tools/make_ledger_cases.py`).

Career races are checked for order and timing only; full re-simulation of each run on the
server comes with multiplayer. A save from before cloud sync is imported once, when its account
is created, with cash capped at $250,000.

With `assets/data/firebase.json` left empty the game plays offline and just queues entries.

### Setting up the Firebase project

1. At https://console.firebase.google.com, add a project (Google Analytics isn't needed).
2. Switch it to the **Blaze** plan (Cloud Functions need it), then in the Google Cloud console
   under **Billing > Budgets & alerts** create a budget (for example $5 with alerts at 50%,
   90% and 100%). A budget only emails you; it doesn't stop spending.
3. **Build > Authentication > Get started > Sign-in method**: enable **Anonymous**.
4. **Build > Firestore Database > Create database**, in production mode.
5. **Build > Realtime Database > Create database**, in locked mode. Copy its URL
   (`https://<project>-default-rtdb.firebaseio.com`) into `databaseURL` in
   `assets/data/firebase.json`.
6. **Project settings > General**: copy the Project ID and the Web API Key into
   `assets/data/firebase.json`. The key identifies the project; it isn't a secret.
7. Deploy the functions and both sets of rules:

   ```sh
   cd server
   npm install && npm --prefix functions install
   npx firebase login
   npx firebase use --add <project-id>
   npx firebase deploy --only functions,firestore:rules,database
   ```

Anonymous accounts live on the device: clearing the app's data starts a new account.

### Online races

ONLINE in the garage: the server rates your build with a perfect pass of its own and pairs you
with someone close (the window widens the longer you wait). Each phone streams its car to the
race room in the Realtime Database ten times a second, so you see the other car live; the tree
starts when both are staged, after a delay the room shares. Every input your car got (throttle,
brake, stage, launch, shifts, nitrous, by physics step) is recorded (`RunRecorder`), and when you
finish the server replays it with its TypeScript port of the physics (`server/functions/src/physics`)
on the car and tune it holds for the race. The replay's reaction time and ET decide the race; the
phone's own times are never used. Both sides run `shared/physics_cases.json` (written by
`PhysicsCasesTest`): the port agrees with the game within a few milliseconds.

What the replay can't prove: the race clock at the launch comes from the phone (it depends on
when the other car staged), so a modified client could claim a perfect light, and a bot could
press the buttons perfectly. If the other side never finishes, the race goes to whoever did after
150 s.

### Testing against the emulators

```sh
cd server
npm --prefix functions test     # ledger unit tests + the shared cases
npm run e2e                     # sign-in, sync, refused cheats, closed Firestore (needs Java 21+)
npx firebase emulators:start --project demo-pixel-strip    # then run the desktop build with
# JAVA_OPTS=-Dpixelstrip.emulator=127.0.0.1 to use the emulators instead of the real project
```

Two players on one machine: add `-Dpixelstrip.profile=p1` (and `p2` for the second copy) so each
keeps its own save and account. With `--screen online --shots DIR 4,40` the autoplay driver
races, which is how an online race was tested end to end. The emulator doesn't apply
`database.rules.json` to a demo project's database by itself; `npm run e2e` loads them, or
`curl -X PUT -H "Authorization: Bearer owner" --data-binary @database.rules.json
"http://127.0.0.1:9000/.settings/rules.json?ns=demo-pixel-strip"`.

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
