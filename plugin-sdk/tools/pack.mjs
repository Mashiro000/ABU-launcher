import { mkdir, readFile, rm, cp, rename } from "node:fs/promises";
import { basename, join, resolve } from "node:path";
import { execFileSync } from "node:child_process";

const source = resolve(process.argv[2] ?? "");
if (!source) throw new Error("usage: node tools/pack.mjs <plugin-directory>");
const manifest = JSON.parse(await readFile(join(source, "manifest.json"), "utf8"));
if (!/^[a-zA-Z0-9._-]{3,100}$/.test(manifest.id)) throw new Error("invalid plugin id");
if (!manifest.version || !manifest.entry) throw new Error("manifest version/entry missing");
await readFile(join(source, manifest.entry));
const stage = resolve(".pack", `${manifest.id}-${manifest.version}`);
await rm(stage, { recursive: true, force: true });
await mkdir(stage, { recursive: true });
await cp(join(source, "manifest.json"), join(stage, "manifest.json"));
await mkdir(join(stage, "dist"), { recursive: true });
await cp(join(source, "dist"), join(stage, "dist"), { recursive: true });
const output = resolve("dist", `${manifest.id}-${manifest.version}.abu-plugin`);
await mkdir(resolve("dist"), { recursive: true });
if (process.platform === "win32") {
  const zipOutput = `${output}.zip`;
  await rm(zipOutput, { force: true });
  execFileSync("tar.exe", ["-a", "-cf", zipOutput, "manifest.json", "dist"], { cwd: stage });
  await rm(output, { force: true });
  await rename(zipOutput, output);
} else {
  execFileSync("zip", ["-qr", output, "manifest.json", "dist"], { cwd: stage });
}
console.log(output);
