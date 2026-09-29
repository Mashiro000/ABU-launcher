import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { validatePackage } from "./validate.mjs";

const directory = resolve(process.argv[2] || ".");
const manifest = JSON.parse(await readFile(resolve(directory, "manifest.json"), "utf8"));
const file = resolve(directory, "dist", `${manifest.id}-${manifest.version}.abu-plugin`);
const result = await validatePackage(file);
if (result.manifest.id !== manifest.id || result.manifest.version !== manifest.version) {
  throw new Error("package identity does not match current manifest");
}
console.log(`Valid ${manifest.id}@${manifest.version} (${result.bytes} bytes)`);
