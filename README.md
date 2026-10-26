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
- 54 cars (JDM, American and European legends, from a '65 roadster to a '02 rotary), each with its
  real inspiration's stock specs and drivetrain, written by `tools/make_roster.py` and checked
  against period road-test quarter miles (`RosterCalibrationTest`, which reads the table in
  `car_references.md`). 44 at the dealer, browsed by class; three starters (two RWD, the Civic
  FWD); boss cars; four barn finds on the career map; four rare tournament prizes. New cars borrow
  an existing body until their own art is drawn. Cars taken out of the roster were refunded in
  full, see "Retired cars" below.
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
- Audio, all synthesized: engines from the live physics (per-cylinder firing for I4, I6, V6, V8,
  V10, V12, flat-4 with its unequal-header burble, flat-6, flat-12 and rotary; a VTEC-style cam
  crossover that hardens the note; turbo, blower, limiter, nitrous, tyre squeal), effects, crowd,
  and chiptune music per screen and track
- Factory turbos spool: off boost an engine makes about 70% of its rated torque, and it comes on
  boost from just below its torque peak. Staged with the clutch in an engine has no load, so it
  barely builds boost; anti-lag on the two-step does, and so does an automatic brake-torqued
  against its converter (the GNX)
- Cloud save (Firebase): anonymous accounts, and every cash or garage change checked by the
  server (see below)

- Online races (Firebase): matchmaking by build, the opponent shown live, and both runs
  replayed by the server to decide the winner (see below)

- Bets and pink slips online: cash stakes of any size you can cover, or the car you race;
  both players accept, the server holds the stakes and pays the winner (see below)

- Crews: up to 20 racers under a name, a 2-4 character tag and a pixel emblem (worn on the door
  with the CREW decal); leaders, officers and members; online wins fight a weekly war for the
  six districts (see below)
- Tournaments: a daily time trial per class with an entry fee, five server-checked passes, a
  live leaderboard, and the top ten splitting the pool (see below)

- Leaderboards: the quickest server-checked ETs per class and distance, the most online wins and
  the top crews, this week or all time (see below)

- XP and levels: career, online and tournament races earn XP; every level pays cash, through the
  ledger on both sides (see below)

- Daily rewards: a claim a day in the garage, a seven-day streak with growing cash (and XP on day
  seven); the server's clock decides (see below)

- Tyre smoke, haze and spray come off the driven wheels: the fronts on FWD, rears on RWD, all four on AWD
- Polish, first pass: rubber in the lanes (a rubbered-in groove off the line, darkened by your
  burnouts and wheelspin), a mist trail off the tyres at Dockside and a dust trail in the
  desert, camera-flash volleys from the stands on the green and at the line,
  and confetti for a win, with fireworks over the stands on clear nights

Next: cash packs (Play Billing).

### How to race

| Touch | Desktop | What |
|---|---|---|
| GAS (right) | Up / W | Hold: burnout in the box, rev while staged, throttle in the run |
| Left button | Space | STAGE after the burnout, LAUNCH on green, SHIFT during the run |
| NOS (right, above GAS) | N | One tap fires the whole shot; the meter drains as it sprays, then the next charge (if any) is ready. Best once the tyres hook, 2nd gear on |
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

### Retired cars

On 2026-10-25 the roster was rebuilt and nine cars left the game. `assets/data/retired.json`
freezes what each one cost (and every part for it) at that moment; a `retire` ledger entry, checked
by the server like any other, pays the owner the car's price, every part bought for it and its
paint and looks, and removes it. A garage left empty gets a free Civic. The game queues these on
launch and after every sync (another device may still hand one back), and the garage shows a
one-time notice with the amounts. Retired ids are never reused (`tools/make_roster.py` checks).
Parts can be retired the same way (`retired.json` "parts").

### Barn finds and prize cars

Beating each tier's boss puts a barn on the career map with a rare car in it, free, once
(`career.json` "barns", a `find` ledger entry). Each day's tournament winner in SPORT, PRO and
OUTLAW also gets that class's prize car if they don't have one (`tournaments.ts` PRIZE_CARS, the
outlaw one rotating by date).

### Bets and pink slips

In the online lobby pick FOR FUN, a CASH BET (stepped up to whatever you have), or PINK SLIP (the
car you race; never your last one). You only meet someone staking exactly the same. After the
match both players see the other car and have 25 s to accept; only when both have does the
server take the stakes (the cash, or a lock on the car so it can't be sold mid-race). At the
verdict the winner gets both cash stakes, or the loser's car with its parts. Declines, timeouts
and races nobody finishes give the stakes back; a race only one side finished goes to them
after 150 s. A negative balance (say, after a refunded purchase) blocks betting. Every step is
logged in Firestore under `wagers/{raceId}`. The money logic is plain functions in
`server/functions/src/wager.ts` with their own tests; the e2e test plays a cash race, a declined
and a won pink slip, and the refusals.

### Crews

CREW in the garage. Without a crew: open crews (this week's strongest first), or find one by
tag, or found your own (name, tag, emblem: shape, three colours, icon). In one: the roster with
roles and each member's best online run; leaders edit the emblem and open or close the door,
leaders and officers manage members. Every online win scores for the winner's crew in the
district (track) it was raced on; whoever has the most wins there this ISO week holds it on the
war map. `server/functions/src/crews.ts`, with unit tests and an e2e pass.

### Tournaments

TOURNEY in the garage. Each UTC day every class (Street, Sport, Pro, Outlaw) has a quarter-mile
time trial on a track picked by the date. Enter with a car the server rates in that class; the
fee ($1k / $4k / $15k / $50k) goes into a pool the house seeds with ten fees. You get five passes,
each replayed by the server with the car as entered, and your best clean ET counts (ties: the
earlier run). After midnight UTC the first call settles it once: 40/20/12/8/6/5/3/2/2/2 percent
of the pool to the top ten (fewer finishers split it all), credited to their garages.
`server/functions/src/tournaments.ts`; the e2e test enters, races, hits the run limit and checks
a payout.

### Leaderboards

RANKS in the garage. QUICKEST lists each racer's best ET in a class over the 1/8 or 1/4 mile;
only runs the server replayed count (online races, tournament passes), each appended to
`runs/` with the car's class at the time. WINS counts online wins this ISO week or all time
(`stats/{uid}`); CREWS ranks crews by their weekly war score or all-time wins. Names and tags
are looked up when a board is read, so a rename shows straight away. Production needs the
composite index in `server/firestore.indexes.json` (deployed with the rules).
`server/functions/src/leaderboards.ts`.

### XP and levels

Career wins give 100 XP (a boss's first defeat 250 more), losses 25; online races 150 for a win
and 40 for a loss; tournament passes 30 each when clean. Level L takes 250 x (L-1) x L / 2 XP in
all (level 2 at 250, 10 at 11,250, 50 at 306,250) and pays $1,000 x (L-1). The numbers live in
`rules.json` (`xp`), and the level-up cash is paid by the ledger itself (`Ledger.addXp` /
`ledger.ts addXp`), so the game and the server agree to the dollar; the shared ledger cases cross
levels. Online and tournament XP is granted by the server only. The garage shows the level and
the bar to the next; the first menu after a level-up shows the banner.

### Daily rewards

The garage pops up the reward when today's (UTC) is waiting. Claiming on consecutive days walks
a seven-day cycle ($1,000 / 1,500 / 2,000 / 3,000 / 4,000 / 6,000 / 10,000, plus $200 per level;
day seven adds 250 XP); a missed day starts it over. LATER hides it until the next launch. Only
the server's clock and its record (`players/{uid}.daily`) count, so changing the phone's date
does nothing. `server/functions/src/daily.ts`.

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
