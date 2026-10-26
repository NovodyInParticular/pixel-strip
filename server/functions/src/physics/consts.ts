/** Vehicle.kt's companion constants. */
export const SPLITS_M = [18.288, 100.584, 201.168, 304.8, 402.336];
export const LEAVE_M = 0.06;
export const AIR_DENSITY = 1.2;
export const ROLLING = 0.015;
export const GRAVITY = 9.81;
export const DRIVELINE_EFF = 0.8;
export const BRAKE_DECEL = 7;
export const RPM_TO_RAD = 0.10471976;
export const HP_TO_W = 745.7;
export const PEAK_SLIP = 0.12;
export const PLATEAU_SLIP = 0.3;
export const SPIN_FALLOFF = 0.4;
export const SPIN_SHOCK = 2.5;
export const BURNOUT_SURFACE = 0.4;
export const DEFAULT_PREP = 1.25;
export const BOG_FLOOR = 0.9;
export const BOOST_RANGE_RPM = 1400;
/** Staged with the clutch in, an unloaded engine barely makes boost. */
export const NO_LOAD_BOOST = 0.3;
/** Factory turbo: stock boost (10 psi x PSI_GAIN x the stock intercooler) as a torque share; spool floor; spool time. */
export const STOCK_BOOST_GAIN = 10 * 0.05 * 0.86;
export const STOCK_SPOOL_MIN = 1200;
export const STOCK_LAG = 0.6;
export const PRESSURE_GRIP = 0.008;
export const PRESSURE_ROLL = 0.025;
export const PRESSURE_RADIUS = 0.0015;
export const LAUNCH_SAG = 0.25;
export const FEATHER_MIN = 0.45;
export const FEATHER_DROP = 4;
export const FEATHER_RISE = 1.4;
export const BURNOUT_HEAT = 0.012;
export const REV_UP = 2600;
export const REV_DOWN = 3200;
export const CREEP_SPEED = 3.4;
export const STAGE_SPEED = 0.45;
export const LIMITER_CUT_S = 0.035;
export const NITROUS_MIN_RPM = 2800;
export const PERFECT_BONUS = 0.06;
export const PERFECT_BONUS_S = 0.6;
export const ANTI_LAG_BOOST = 0.6;
export const TRANSFER_TAU = 0.06;
export const WHEELIE_LIFT = 0.35;
export const WHEELIE_BAR_LIFT = 0.18;
export const STEP = 1 / 240;
/** Tree.kt: the stage beam. */
export const STAGE_M = -0.02;
