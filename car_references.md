# Car references (internal)

Maps each fictional in-game car to its real-world inspiration. Base stats in
`assets/data/cars/*.json` come from these stock specs (curb weights rounded to kg, torque to Nm).
Never use the real names, brands or logos in the game itself. Written by `tools/make_roster.py`;
the Drivetrain and Stock 1/4 columns are read by the tests (each car's real drivetrain, and a typical
period road-test quarter mile its stock simulated pass must land near).

| In-game | Id | Inspired by | Drivetrain | Stock specs | Stock 1/4 (s) | How you get it |
|---|---|---|---|---|---|---|
| Raijin Kaminari GT-X Mk1 | `kaminari_mk1` | 1989 Nissan Skyline GT-R (R32) | AWD | 276 hp @ 6,800 · 260 lb-ft @ 4,400 · ~3,150 lb · AWD (ATTESA E-TS) · 5-speed, 4.11 final | 13.3 | dealer |
| Raijin Kaminari GT-X Mk2 | `kaminari_mk2` | 1995 Nissan Skyline GT-R (R33) | AWD | 276 hp @ 6,800 · 368 Nm @ 4,400 · ~1,530 kg · AWD (ATTESA E-TS Pro) · 5-speed, 4.11 final | 13.2 | dealer |
| Raijin Kaminari GT-X Mk3 | `kaminari_mk3` | 1999 Nissan Skyline GT-R V-Spec (R34) | AWD | 276 hp rated (~330 real) @ 7,000 · 289 lb-ft @ 4,400 · ~3,440 lb · AWD · 6-speed Getrag, 3.545 final | 13.0 | dealer |
| Raijin Sylph K13 | `sylph_13` | 1991 Nissan Silvia K's (S13, SR20DET) | RWD | 205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,220 kg · RWD · 5-speed, 4.11 final | 14.8 | dealer |
| Raijin Sylph R15 | `sylph_15` | 1999 Nissan Silvia Spec-R (S15, SR20DET) | RWD | 250 PS @ 6,400 · 28 kgm @ 4,800 · ~1,250 kg · RWD · 6-speed, 4.08 final | 14.1 | dealer |
| Raijin Sylph 180 Fastback | `sylph_180` | 1996 Nissan 180SX Type X (SR20DET) | RWD | 205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,240 kg · RWD · 5-speed, 4.11 final | 14.9 | dealer |
| Hayate Tsunami TT | `tsunami` | 1994 Toyota Supra Turbo (MK4) | RWD | 320 hp @ 5,600 · 315 lb-ft @ 4,000 · ~3,450 lb · RWD · 6-speed Getrag V160, 3.13 rear | 13.3 | dealer |
| Hayate Ronin MR Turbo | `ronin` | 1991 Toyota MR2 Turbo (SW20, 3S-GTE) | RWD | 200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~2,888 lb · mid-engine RWD · 5-speed E153, 4.29 final | 14.3 | dealer |
| Kitsune Spiral Turbo | `spiral_turbo` | 1987 Mazda RX-7 Turbo II (FC3S) | RWD | 182 hp @ 6,500 · 183 lb-ft @ 3,500 · ~2,850 lb · RWD · 5-speed, 4.10 rear | 15.0 | dealer |
| Kitsune Spiral R Final | `spiral_r` | 2002 Mazda RX-7 Spirit R Type A (FD3S) | RWD | 280 PS @ 6,500 · 32 kgm @ 5,000 · ~1,270 kg · RWD · 5-speed, 4.30 final | 13.4 | dealer |
| Kitsune Prairie R3 | `prairie` | 1972 Mazda RX-3 / Savanna GT (S124A, 12A) | RWD | 120 PS gross @ 6,500 · 16 kgm @ 4,000 · ~905 kg · RWD · 5-speed, 4.11 final | 16.4 | barn find |
| Hikari Kirin S | `kirin` | 1991 Acura NSX | RWD | 270 hp @ 7,100 · 210 lb-ft @ 5,300 · ~3,010 lb · mid-engine RWD · 5-speed, 4.06 final | 13.6 | dealer |
| Hikari Kite VTX | `kite` | 1994 Honda Civic Si hatchback (EG) | FWD | 125 hp @ 6,600 · 106 lb-ft @ 5,200 · ~2,350 lb · FWD · 5-speed, 4.25 final | 16.0 | dealer |
| Hikari Kite EF Hatch | `kite_ef` | 1990 Honda Civic Si hatchback (EF); boss car of Joe "Ground Control", built to the user's friend's real car | FWD | 108 hp @ 6,000 · 100 lb-ft @ 5,000 · ~2,250 lb · FWD · 5-speed, 4.25 final | 16.3 | career boss |
| Hikari Kite RX | `kite_rx` | 1997 Honda Civic Type R (EK9, B16B) | FWD | 185 PS @ 8,200 · 16.3 kgm @ 7,500 · ~1,050 kg · FWD · 5-speed close-ratio, 4.79 final | 15.1 | dealer |
| Hikari Arrow RS-X | `arrow` | 1997 Acura Integra Type R (DC2) | FWD | 195 hp @ 8,000 · 130 lb-ft @ 7,500 · ~2,640 lb · FWD · 5-speed close-ratio, 4.40 final | 15.1 | dealer |
| Hikari Overture SH | `overture` | 1999 Honda Prelude Type SH (BB6, H22A4) | FWD | 200 hp @ 7,000 · 156 lb-ft @ 5,250 · ~3,042 lb · FWD (ATTS) · 5-speed, 4.27 final | 15.2 | dealer |
| Hikari Kite Sol | `kite_sol` | 1994 Honda Civic del Sol VTEC (B16A3) | FWD | 160 hp @ 7,600 · 111 lb-ft @ 7,000 · ~2,445 lb · FWD · 5-speed, 4.27 final | 15.6 | dealer |
| Hikari Tenshi 2000 | `tenshi` | 2000 Honda S2000 (AP1, F20C) | RWD | 240 hp @ 8,300 · 153 lb-ft @ 7,500 · ~2,809 lb · RWD · 6-speed, 4.10 rear | 14.2 | dealer |
| Tsubasa Halberd VI | `halberd_6` | 1999 Mitsubishi Lancer Evolution VI GSR (CP9A) | AWD | 280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final | 13.6 | dealer |
| Tsubasa Typhoon TT | `typhoon` | 1991 Mitsubishi 3000GT VR-4 | AWD | 300 hp @ 6,000 · 307 lb-ft @ 2,500 · ~3,740 lb · AWD · 5-speed, 4.15 final | 13.8 | dealer |
| Seiza Comet STX | `comet_gd` | 2004 Subaru Impreza WRX STI (GD, EJ257) | AWD | 300 hp @ 6,000 · 300 lb-ft @ 4,000 · ~3,263 lb · AWD (DCCD) · 6-speed, 3.90 final | 13.3 | dealer |
| Crestline Vortex Z6 | `vortex` | 2002 Chevrolet Corvette Z06 (C5, LS6) | RWD | 405 hp @ 6,000 · 400 lb-ft @ 4,800 · ~3,118 lb · RWD (rear transaxle) · 6-speed M12, 3.42 rear | 12.4 | dealer |

Fictional brands: Raijin (Nissan), Hayate (Toyota), Kitsune (Mazda), Hikari (Honda/Acura), Tsubasa (Mitsubishi), Seiza (Subaru), Crestline (Chevrolet).

Removed on 2026-10-25 (owners refunded, see `assets/data/retired.json`): Dominion Vandal R/T ('69 Charger R/T),
Harlan Ranger GT Fastback ('67 Mustang GT 390), Crestline Corsair SS 396 ('69 Camaro SS), Dominion Piranha Hemi
('70 Hemi 'Cuda), Apache Warrior 389 ('64 GTO), Harlan Ranger 5.0 LX ('88 Fox Mustang), Crestline Corsair IROC 5.7
('88 IROC-Z), Kessler 940 Turbo ('86 944 Turbo), Raijin Zephyr 300 Turbo ('86 Z31 300ZX).

Removed on 2026-10-26, when the roster became only the cars in the user's reference photos (owners refunded the
same way): asp, comet, comet_22x, condor, dart, halberd_4, halberd_5, isar_s3, kaminari_c10, kestrel, kirin_r, kokoro, lm40, mamba, mammoth, phantom, pursuit, quaranta, ranger_500, scarlatta, seiran, shirasagi, spiral_sa, squall, stellar, sylph_14, tsunami_mk3, wal, wal_awd, wraith, zanna, zephyr_240, zephyr_tt. The Crestline Vortex became a C5 Z06 (was a C4 ZR-1) and the
Hikari Overture a 5th-generation Prelude (was a 4th). Retired ids are never reused.

## Parts

Fictional parts makers in the game and the real companies they stand in for. Never show the real names.

| In-game brand | Real-world equivalents |
|---|---|
| AIRHAMMER | K&N, AEM (intakes) |
| BRYDE | Bride (race seats) |
| CALDER | Calvert Racing (bars, tubs, cages) |
| CLEARLITE | Lexan polycarbonate windows |
| COLLIN | Collins Adapters (RB/JZ swaps) |
| CROWLEY | Brian Crower, Crower (cams, heads, valve springs, strokers) |
| EXODI | Exedy, ACT (clutches, flywheels) |
| GARNET | Garrett (turbos, ball-bearing turbos) |
| HALTRON | Haltech (standalone ECUs, 2-step) |
| HARLAN RACING | Ford Performance (Coyote crate engine) |
| HASLER | Hasport (Honda swap mounts and kits) |
| HOOKLINE | Hooker, Kooks (headers) |
| HOOZER | Hoosier, Mickey Thompson (drag radials, slicks) |
| IANNO | Iannetti (apex seals) |
| INJEX | Injector Dynamics, FIC (injectors, fuel systems) |
| KARRO | Carrillo, Dart (rods, rotating assemblies, blocks) |
| KOBUN | HKS, Tomei, GReddy (manifolds, downpipes, intercoolers, twin-turbo kits) |
| MOTEX | MoTeC (anti-lag) |
| NITTAN | Nitto (street tyres) |
| NOVUS | NOS, Nitrous Express (nitrous kits) |
| OSAKI | OS Giken (twin-disc clutches, LSDs) |
| PRECISE | Precision Turbo (big singles) |
| QUAYLE | Quaife (gearboxes, dog boxes, sequentials, diffs) |
| REFLEXA | Hondata, COBB (reflash) |
| ROTOHEAD | Pineapple Racing, Mazdatrix (porting, oil injection, 3/4-rotor swaps) |
| SANDHOFF | Santhuff (drag springs and shocks) |
| SEIBAN | Seibon (carbon panels and body parts) |
| SHAFTWORKS | The Driveshaft Shop (driveshafts, axles, short shifters) |
| SIKKA | Sikky (LS swap kits) |
| SPLICE | Unichip, GReddy e-Manage (piggyback ECUs) |
| STACKWELL | Jenvey, TWM (individual throttle bodies, velocity stacks) |
| TENNO | Tein, KW (coilovers) |
| THUNDERBORE | Borla, MagnaFlow (exhausts) |
| TIDEGATE | TiAL (external wastegates) |
| TURBOZEN | Turbosmart (boost controllers, BOVs, intercoolers) |
| VOLKER | Volk, Weld (forged wheels, beadlocks) |
| VOLTCELL | Odyssey, Braille (light batteries) |
| VORTRAX | Vortech, ProCharger (centrifugal superchargers) |
| WALDEN | Walbro, Aeromotive (fuel pumps) |
| WEYLAND | Weiand, BDS (roots blowers, big-block crate engines) |
| WHIPSAW | Whipple (twin-screw superchargers) |
| WISEMAN | Wiseco (forged pistons) |

Engine swaps (what each kit stands in for, and which chassis take it):

| Swap | Real engine | Fits |
|---|---|---|
| K-SERIES (K24/K20 HYBRID) | Honda K24/K20 'frankenstein' hybrid | kite, kite_ef, kite_rx, kite_sol, arrow |
| B18C TYPE R | Honda B18C (Integra Type R) | kite, kite_ef, kite_rx, kite_sol |
| H22A | Honda H22A (Prelude) | kite, kite_ef, kite_sol |
| RB26DETT TWIN TURBO | Nissan RB26DETT (Skyline GT-R) | sylph_13, sylph_15, sylph_180 |
| 2JZ-GTE TWIN TURBO | Toyota 2JZ-GTE (Supra MK4) | sylph_13, sylph_15, sylph_180 |
| LS1 5.7 V8 | GM LS1 5.7 | spiral_turbo, spiral_r, sylph_13, sylph_15, sylph_180, tsunami |
| 572 BIG-BLOCK CRATE | Chevrolet 572 big-block crate | vortex |
| COYOTE 5.0 V8 | Ford Coyote 5.0 | sylph_13, sylph_180, spiral_r |
| 13B-REW SEQUENTIAL TWIN TURBO | Mazda 13B-REW (FD RX-7) | spiral_turbo, prairie |
| 20B-REW 3-ROTOR TWIN TURBO | Mazda 20B-REW (Eunos Cosmo) | prairie, spiral_r, spiral_turbo |
| 26B 4-ROTOR, BRIDGE PORTED | Mazda 26B 4-rotor (787B-style) | prairie, spiral_r, spiral_turbo |
