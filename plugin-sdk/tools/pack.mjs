import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from "node:fs/promises";
import { dirname, join, relative, resolve, sep } from "node:path";
import { MAX_ENTRY_BYTES, validRelativePath, validateManifest } from "./manifest.mjs";
import { validatePackage } from "./validate.mjs";
import { writeZip } from "./zip-writer.mjs";

const sourceArgument = process.argv[2];
if (!sourceArgument) { console.error("Usage: node tools/pack.mjs <plugin-directory>"); process.exit(2); }
const source = resolve(sourceArgument);
if ((await stat(join(source, "manifest.json"))).isSymbolicLink()) throw new Error("manifest.json may not be a symbolic link");
const manifestBytes = await readFile(join(source, "manifest.json"));
const manifest = JSON.parse(manifestBytes.toString("utf8"));
const errors = validateManifest(manifest);
if (errors.length) throw new Error(`invalid manifest:\n- ${errors.join("\n- ")}`);

const files = [["manifest.json", manifestBytes]];
async function collect(directory) {
  for (const item of await readdir(directory, { withFileTypes: true })) {
    const full = join(directory, item.name);
    const name = relative(source, full).split(sep).join("/");
    if (!validRelativePath(name)) throw new Error(`unsafe file path: ${name}`);
    if (item.isSymbolicLink()) throw new Error(`symbolic links are not allowed: ${name}`);
    if (item.isDirectory()) await collect(full);
    else if (item.isFile()) {
      if (name.endsWith(".abu-plugin") || name.endsWith(".tmp")) continue;
      if (/\.(so|dex|apk)$/i.test(name)) throw new Error(`native or executable files are not allowed: ${name}`);
      files.push([name, await readFile(full)]);
    }
  }
}
for (const folder of ["dist", "assets"]) {
  const directory = join(source, folder);
  if ((await stat(directory).catch(() => null))?.isDirectory()) await collect(directory);
}
const entry = files.find(([name]) => name === manifest.entry)?.[1];
if (!entry || entry.length > MAX_ENTRY_BYTES) throw new Error(`entry missing or oversized: ${manifest.entry}`);
if (files.length > 2000) throw new Error("package contains too many files");
if (files.reduce((sum, [, bytes]) => sum + bytes.length, 0) > 200 * 1024 * 1024) throw new Error("package extracted size exceeds 200 MiB");

const output = resolve("dist", `${manifest.id}-${manifest.version}.abu-plugin`);
await mkdir(dirname(output), { recursive: true });
const temporary = `${output}.${process.pid}.tmp`;
try {
  await writeFile(temporary, writeZip(files));
  await validatePackage(temporary);
  await rm(output, { force: true });
  await rename(temporary, output);
  console.log(output);
} finally {
  await rm(temporary, { force: true });
}
