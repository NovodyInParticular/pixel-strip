# Car references (internal)

Maps each fictional in-game car to its real-world inspiration. Base stats in
`assets/data/cars/*.json` come from these stock specs (curb weights rounded to kg, torque to Nm).
Never use the real names, brands or logos in the game itself. Written by `tools/make_roster.py`;
the Drivetrain and Stock 1/4 columns are read by the tests (each car's real drivetrain, and a typical
period road-test quarter mile its stock simulated pass must land near).

| In-game | Id | Inspired by | Drivetrain | Stock specs | Stock 1/4 (s) | How you get it |
|---|---|---|---|---|---|---|
| Raijin Kaminari 2000 GT-X | `kaminari_c10` | 1969 Nissan Skyline 2000GT-R (KPGC10 'Hakosuka') | RWD | 160 PS gross @ 7,000 · 18 kgm @ 5,600 · ~1,100 kg · RWD · 5-speed, 4.44 final | 16.0 | barn find |
| Raijin Kaminari GT-X Mk1 | `kaminari_mk1` | 1989 Nissan Skyline GT-R (R32) | AWD | 276 hp @ 6,800 · 260 lb-ft @ 4,400 · ~3,150 lb · AWD (ATTESA E-TS) · 5-speed, 4.11 final | 13.3 | dealer |
| Raijin Kaminari GT-X Mk2 | `kaminari_mk2` | 1995 Nissan Skyline GT-R (R33) | AWD | 276 hp @ 6,800 · 368 Nm @ 4,400 · ~1,530 kg · AWD (ATTESA E-TS Pro) · 5-speed, 4.11 final | 13.2 | dealer |
| Raijin Kaminari GT-X Mk3 | `kaminari_mk3` | 1999 Nissan Skyline GT-R V-Spec (R34) | AWD | 276 hp rated (~330 real) @ 7,000 · 289 lb-ft @ 4,400 · ~3,440 lb · AWD · 6-speed Getrag, 3.545 final | 13.0 | dealer |
| Raijin Zephyr 240 | `zephyr_240` | 1970 Datsun 240Z (HS30) | RWD | 151 hp gross @ 5,600 · 146 lb-ft @ 4,400 · ~2,355 lb · RWD · 4-speed, 3.36 rear | 16.1 | dealer |
| Raijin Zephyr 300 TT | `zephyr_tt` | 1990 Nissan 300ZX Twin Turbo (Z32) | RWD | 300 hp @ 6,400 · 283 lb-ft @ 3,600 · ~3,450 lb · RWD · 5-speed, 3.69 rear | 13.8 | dealer |
| Raijin Sylph K13 | `sylph_13` | 1991 Nissan Silvia K's (S13, SR20DET) | RWD | 205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,220 kg · RWD · 5-speed, 4.11 final | 14.8 | dealer |
| Raijin Sylph K14 | `sylph_14` | 1997 Nissan Silvia K's (S14 kouki, SR20DET) | RWD | 220 PS @ 6,000 · 28 kgm @ 4,800 · ~1,240 kg · RWD · 5-speed, 4.11 final | 14.5 | dealer |
| Raijin Sylph R15 | `sylph_15` | 1999 Nissan Silvia Spec-R (S15, SR20DET) | RWD | 250 PS @ 6,400 · 28 kgm @ 4,800 · ~1,250 kg · RWD · 6-speed, 4.08 final | 14.1 | dealer |
| Raijin Sylph 180 Fastback | `sylph_180` | 1996 Nissan 180SX Type X (SR20DET) | RWD | 205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,240 kg · RWD · 5-speed, 4.11 final | 14.9 | dealer |
| Hayate Tsunami Turbo Mk3 | `tsunami_mk3` | 1987 Toyota Supra Turbo (MA70, 7M-GTE) | RWD | 230 hp @ 5,600 · 246 lb-ft @ 3,200 · ~3,535 lb · RWD · 5-speed R154, 3.73 rear | 15.1 | dealer |
| Hayate Tsunami TT | `tsunami` | 1994 Toyota Supra Turbo (MK4) | RWD | 320 hp @ 5,600 · 315 lb-ft @ 4,000 · ~3,450 lb · RWD · 6-speed Getrag V160, 3.13 rear | 13.3 | dealer |
| Hayate Kestrel GTS | `kestrel` | 1985 Toyota Corolla GT-S (AE86) | RWD | 112 hp @ 6,600 · 97 lb-ft @ 5,800 · ~2,400 lb · RWD · 5-speed T50, 4.30 rear | 16.5 | dealer |
| Hayate Ronin MR Turbo | `ronin` | 1991 Toyota MR2 Turbo (SW20, 3S-GTE) | RWD | 200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~2,888 lb · mid-engine RWD · 5-speed E153, 4.29 final | 14.3 | dealer |
| Hayate Seiran GT-4 | `seiran` | 1991 Toyota Celica All-Trac / GT-Four (ST185) | AWD | 200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~3,260 lb · AWD · 5-speed E150F, 4.29 final | 15.0 | dealer |
| Hayate Pursuit Tourer TV | `pursuit` | 1998 Toyota Chaser Tourer V (JZX100, 1JZ-GTE) | RWD | 280 PS @ 6,200 · 38.5 kgm @ 2,400 · ~1,510 kg · RWD · 5-speed R154, 3.91 final | 14.4 | dealer |
| Hayate Shirasagi 2000 | `shirasagi` | 1967 Toyota 2000GT | RWD | 150 PS gross @ 6,600 · 18 kgm @ 5,000 · ~1,120 kg · RWD · 5-speed, 4.375 final | 16.8 | barn find |
| Kitsune Spiral SA | `spiral_sa` | 1984 Mazda RX-7 GSL-SE (FB, 13B) | RWD | 135 hp @ 6,000 · 133 lb-ft @ 2,750 · ~2,590 lb · RWD · 5-speed, 4.10 rear | 16.1 | dealer |
| Kitsune Spiral Turbo | `spiral_turbo` | 1987 Mazda RX-7 Turbo II (FC3S) | RWD | 182 hp @ 6,500 · 183 lb-ft @ 3,500 · ~2,850 lb · RWD · 5-speed, 4.10 rear | 15.0 | dealer |
| Kitsune Spiral R Final | `spiral_r` | 2002 Mazda RX-7 Spirit R Type A (FD3S) | RWD | 280 PS @ 6,500 · 32 kgm @ 5,000 · ~1,270 kg · RWD · 5-speed, 4.30 final | 13.4 | dealer |
| Kitsune Kokoro 1.6 | `kokoro` | 1990 Mazda MX-5 Miata (NA, 1.6) | RWD | 116 hp @ 6,500 · 100 lb-ft @ 5,500 · ~2,182 lb · RWD · 5-speed, 4.30 rear | 16.8 | dealer |
| Kitsune Prairie R3 | `prairie` | 1972 Mazda RX-3 / Savanna GT (S124A, 12A) | RWD | 120 PS gross @ 6,500 · 16 kgm @ 4,000 · ~905 kg · RWD · 5-speed, 4.11 final | 16.4 | barn find |
| Hikari Kirin S | `kirin` | 1991 Acura NSX | RWD | 270 hp @ 7,100 · 210 lb-ft @ 5,300 · ~3,010 lb · mid-engine RWD · 5-speed, 4.06 final | 13.6 | dealer |
| Hikari Kirin R | `kirin_r` | 1992 Honda NSX Type R (NA1) | RWD | 280 PS @ 7,300 · 30 kgm @ 5,400 · ~1,230 kg · mid-engine RWD · 5-speed close-ratio, 4.24 final | 13.2 | tournament prize |
| Hikari Kite VTX | `kite` | 1994 Honda Civic Si hatchback (EG) | FWD | 125 hp @ 6,600 · 106 lb-ft @ 5,200 · ~2,350 lb · FWD · 5-speed, 4.25 final | 16.0 | dealer |
| Hikari Kite EF Hatch | `kite_ef` | 1990 Honda Civic Si hatchback (EF); boss car of Joe "Ground Control", built to the user's friend's real car | FWD | 108 hp @ 6,000 · 100 lb-ft @ 5,000 · ~2,250 lb · FWD · 5-speed, 4.25 final | 16.3 | career boss |
| Hikari Kite RX | `kite_rx` | 1997 Honda Civic Type R (EK9, B16B) | FWD | 185 PS @ 8,200 · 16.3 kgm @ 7,500 · ~1,050 kg · FWD · 5-speed close-ratio, 4.79 final | 15.1 | dealer |
| Hikari Arrow RS-X | `arrow` | 1997 Acura Integra Type R (DC2) | FWD | 195 hp @ 8,000 · 130 lb-ft @ 7,500 · ~2,640 lb · FWD · 5-speed close-ratio, 4.40 final | 15.1 | dealer |
| Hikari Dart Si | `dart` | 1988 Honda CRX Si (EF8) | FWD | 105 hp @ 6,000 · 98 lb-ft @ 5,000 · ~2,112 lb · FWD · 5-speed, 4.25 final | 16.3 | dealer |
| Hikari Overture VTX | `overture` | 1993 Honda Prelude VTEC (BB1, H22A) | FWD | 190 hp @ 6,800 · 158 lb-ft @ 5,500 · ~2,866 lb · FWD · 5-speed, 4.27 final | 15.4 | dealer |
| Hikari Tenshi 2000 | `tenshi` | 2000 Honda S2000 (AP1, F20C) | RWD | 240 hp @ 8,300 · 153 lb-ft @ 7,500 · ~2,809 lb · RWD · 6-speed, 4.10 rear | 14.2 | dealer |
| Tsubasa Halberd IV | `halberd_4` | 1996 Mitsubishi Lancer Evolution IV GSR (CN9A) | AWD | 280 PS @ 6,500 · 36 kgm @ 3,000 · ~1,350 kg · AWD (AYC) · 5-speed, 4.53 final | 13.8 | dealer |
| Tsubasa Halberd V | `halberd_5` | 1998 Mitsubishi Lancer Evolution V GSR (CP9A) | AWD | 280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final | 13.7 | dealer |
| Tsubasa Halberd VI | `halberd_6` | 1999 Mitsubishi Lancer Evolution VI GSR (CP9A) | AWD | 280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final | 13.6 | dealer |
| Tsubasa Typhoon TT | `typhoon` | 1991 Mitsubishi 3000GT VR-4 | AWD | 300 hp @ 6,000 · 307 lb-ft @ 2,500 · ~3,740 lb · AWD · 5-speed, 4.15 final | 13.8 | dealer |
| Tsubasa Squall AWD | `squall` | 1995 Mitsubishi Eclipse GSX (2G) | AWD | 210 hp @ 6,000 · 214 lb-ft @ 3,000 · ~3,200 lb · AWD · 5-speed, 4.15 final | 14.5 | dealer |
| Tsubasa Stellar ESR | `stellar` | 1988 Mitsubishi Starion ESI-R (4G54 turbo) | RWD | 188 hp @ 5,000 · 234 lb-ft @ 2,500 · ~2,940 lb · RWD · 5-speed, 3.55 rear | 15.8 | dealer |
| Seiza Comet STX | `comet` | 1998 Subaru Impreza WRX STI Version V (GC8) | AWD | 280 PS @ 6,500 · 36 kgm @ 4,000 · ~1,270 kg · AWD (DCCD) · 5-speed, 4.44 final | 13.7 | dealer |
| Seiza Comet 22X | `comet_22x` | 1998 Subaru Impreza 22B STi (GC8, EJ22G) | AWD | 280 PS @ 6,000 · 37 kgm @ 3,200 · ~1,270 kg · AWD · 5-speed, 3.90 final | 13.4 | tournament prize |
| Carver Ranger 500 | `ranger_500` | 1967 Shelby GT500 (Mustang fastback, 428 Police Interceptor) | RWD | 355 hp gross @ 5,400 · 420 lb-ft @ 3,200 · ~3,500 lb · RWD · 4-speed Top Loader, 3.50 rear | 14.7 | dealer |
| Carver Mamba 427 | `mamba` | 1965 Shelby Cobra 427 (street) | RWD | 425 hp gross @ 6,000 · 480 lb-ft @ 3,700 · ~2,529 lb · RWD · 4-speed Top Loader, 3.54 rear | 12.4 | barn find |
| Harlan LM40 Road | `lm40` | 1967 Ford GT40 Mk III road car (289 HiPo) | RWD | 306 hp gross @ 6,000 · 329 lb-ft @ 4,200 · ~1,030 kg · mid-engine RWD · 5-speed ZF, 3.77 final | 13.1 | tournament prize |
| Regent Phantom GNX | `phantom` | 1987 Buick GNX | RWD | 276 hp rated (~300 real) @ 4,400 · 360 lb-ft @ 3,000 · ~3,545 lb · RWD · 4-speed auto 200-4R, 3.42 rear | 13.4 | dealer |
| Crestline Vortex ZX-1 | `vortex` | 1990 Chevrolet Corvette ZR-1 (C4) | RWD | 375 hp @ 5,800 · 370 lb-ft @ 4,800 · ~3,465 lb · RWD · 6-speed ZF, 3.45 rear | 13.0 | dealer |
| Dominion Asp GTS | `asp` | 1996 Dodge Viper GTS | RWD | 450 hp @ 5,200 · 490 lb-ft @ 3,700 · ~3,383 lb · RWD · 6-speed T56, 3.07 rear | 12.4 | dealer |
| Crestline Mammoth SS 454 | `mammoth` | 1970 Chevrolet Chevelle SS 454 LS6 | RWD | 450 hp @ 5,600 · 500 lb-ft @ 3,600 · ~3,800 lb · RWD · 4-speed Muncie M22, 3.73 rear | 13.8 | dealer |
| Dominion Condor 440 | `condor` | 1970 Plymouth Road Runner Superbird (440 Super Commando) | RWD | 375 hp gross @ 4,600 · 480 lb-ft @ 3,200 · ~3,840 lb · RWD · 4-speed A833, 3.54 rear | 14.4 | career boss |
| Kessler Wal Turbo | `wal` | 1979 Porsche 911 Turbo 3.3 (930) | RWD | 300 PS @ 5,500 · 42 kgm @ 4,000 · ~1,300 kg · rear-engine RWD · 4-speed, 4.22 final | 13.6 | dealer |
| Kessler Wal Turbo AWD | `wal_awd` | 1996 Porsche 911 Turbo (993) | AWD | 408 PS @ 5,750 · 540 Nm @ 4,500 · ~1,500 kg · rear-engine AWD · 6-speed, 3.44 final | 12.3 | dealer |
| Isar Isar S3 Sport | `isar_s3` | 1988 BMW M3 (E30, S14) | RWD | 192 hp @ 6,750 · 170 lb-ft @ 4,750 · ~2,865 lb · RWD · 5-speed dogleg Getrag 265, 4.10 rear | 15.3 | dealer |
| Fenice Quaranta | `quaranta` | 1990 Ferrari F40 | RWD | 478 hp @ 7,000 · 426 lb-ft @ 4,000 · ~1,254 kg · mid-engine RWD · 5-speed, 2.73 final | 11.9 | tournament prize |
| Fenice Scarlatta | `scarlatta` | 1986 Ferrari Testarossa (US) | RWD | 380 hp @ 5,750 · 361 lb-ft @ 4,500 · ~3,660 lb · mid-engine RWD · 5-speed, 3.21 final | 13.5 | dealer |
| Furia Zanna 5000 | `zanna` | 1985 Lamborghini Countach 5000 Quattrovalvole (US) | RWD | 420 hp @ 7,000 · 341 lb-ft @ 5,000 · ~3,350 lb · mid-engine RWD · 5-speed, 4.09 final; tall wing | 13.0 | dealer |
| Arden Wraith Turbo SE | `wraith` | 1989 Lotus Esprit Turbo SE | RWD | 264 hp @ 6,500 · 261 lb-ft @ 4,250 · ~2,800 lb · mid-engine RWD · 5-speed, 3.89 final | 13.8 | dealer |

Fictional brands: Raijin (Nissan), Hayate (Toyota), Kitsune (Mazda), Hikari (Honda/Acura), Tsubasa (Mitsubishi), Seiza (Subaru), Carver (Shelby), Harlan (Ford), Crestline (Chevrolet), Regent (Buick), Dominion (Dodge/Plymouth), Kessler (Porsche), Isar (BMW), Fenice (Ferrari), Furia (Lamborghini), Arden (Lotus).

Removed on 2026-10-25 (owners refunded, see `assets/data/retired.json`): Dominion Vandal R/T ('69 Charger R/T),
Harlan Ranger GT Fastback ('67 Mustang GT 390), Crestline Corsair SS 396 ('69 Camaro SS), Dominion Piranha Hemi
('70 Hemi 'Cuda), Apache Warrior 389 ('64 GTO), Harlan Ranger 5.0 LX ('88 Fox Mustang), Crestline Corsair IROC 5.7
('88 IROC-Z), Kessler 940 Turbo ('86 944 Turbo), Raijin Zephyr 300 Turbo ('86 Z31 300ZX). Their ids are never reused.

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
| K-SERIES (K24/K20 HYBRID) | Honda K24/K20 'frankenstein' hybrid | kite, kite_ef, kite_rx, dart, arrow |
| B18C TYPE R | Honda B18C (Integra Type R) | kite, kite_ef, kite_rx, dart |
| H22A | Honda H22A (Prelude) | kite, kite_ef, dart |
| RB26DETT TWIN TURBO | Nissan RB26DETT (Skyline GT-R) | sylph_13, sylph_14, sylph_15, sylph_180, zephyr_240, kaminari_c10 |
| 2JZ-GTE TWIN TURBO | Toyota 2JZ-GTE (Supra MK4) | tsunami_mk3, pursuit, kestrel |
| LS1 5.7 V8 | GM LS1 5.7 | spiral_sa, spiral_turbo, spiral_r, zephyr_240, zephyr_tt, kokoro, sylph_13, sylph_14, isar_s3, tsunami_mk3, phantom |
| 572 BIG-BLOCK CRATE | Chevrolet 572 big-block crate | mammoth, condor |
| COYOTE 5.0 V8 | Ford Coyote 5.0 | ranger_500, mamba |
| 13B-REW SEQUENTIAL TWIN TURBO | Mazda 13B-REW (FD RX-7) | spiral_sa, spiral_turbo, prairie |
| 20B-REW 3-ROTOR TWIN TURBO | Mazda 20B-REW (Eunos Cosmo) | prairie, spiral_r, spiral_sa, spiral_turbo |
| 26B 4-ROTOR, BRIDGE PORTED | Mazda 26B 4-rotor (787B-style) | prairie, spiral_r, spiral_sa, spiral_turbo |
