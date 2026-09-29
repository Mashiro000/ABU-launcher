import { execFileSync } from "node:child_process";
import { readdir } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const tsc = join(root, "node_modules", "typescript", "bin", "tsc");
for (const entry of await readdir(join(root, "examples"), { withFileTypes: true })) {
  if (!entry.isDirectory()) continue;
  const directory = resolve(root, "examples", entry.name);
  execFileSync(process.execPath, [tsc, "-p", join(directory, "tsconfig.json")], { cwd: root, stdio: "inherit" });
  execFileSync(process.execPath, [join(root, "tools", "pack.mjs"), directory], { cwd: root, stdio: "inherit" });
}
