/** Car classes from the game's economy.json: the quickest class whose limit a car's perfect pass is under. */
import { readFileSync } from "node:fs";
import { join } from "node:path";

const economy = JSON.parse(readFileSync(join(__dirname, "data", "economy.json"), "utf8"));
const LIMITS: { name: string; maxEt: number }[] = economy.classes;

export function classFor(quarter: number): string {
  return (LIMITS.find((c) => quarter <= c.maxEt) ?? LIMITS[LIMITS.length - 1]).name;
}
