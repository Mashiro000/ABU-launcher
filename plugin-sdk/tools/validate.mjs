import { readFile, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { createHash, createPublicKey, verify } from "node:crypto";
import { MAX_ENTRY_BYTES, MAX_PACKAGE_BYTES, validRelativePath, validateManifest } from "./manifest.mjs";
import { readZip } from "./zip-reader.mjs";

export async function validatePackage(path) {
  const fullPath = resolve(path);
  const file = await stat(fullPath);
  if (file.size > MAX_PACKAGE_BYTES) throw new Error("package exceeds 250 MiB");
  const archive = readZip(await readFile(fullPath));
  if (archive.entries.size > 2000) throw new Error("package contains too many files");
  let extractedBytes = 0;
  for (const [name, entry] of archive.entries) {
    if (!validRelativePath(name.endsWith("/") ? name.slice(0, -1) : name)) throw new Error(`unsafe ZIP path: ${name}`);
    if (name !== "manifest.json" && !name.startsWith("dist/") && !name.startsWith("assets/") && name !== "dist/" && name !== "assets/") {
      throw new Error(`unexpected ZIP path: ${name}; only manifest.json, dist/ and assets/ are allowed`);
    }
    extractedBytes += entry.uncompressed;
    if (extractedBytes > 200 * 1024 * 1024) throw new Error("extracted package exceeds 200 MiB");
    if (/\.(so|dex|apk)$/i.test(name)) throw new Error(`script plugin may not contain native or executable file: ${name}`);
  }
  const manifest = JSON.parse(archive.read("manifest.json").toString("utf8"));
  const errors = validateManifest(manifest);
  if (errors.length) throw new Error(`invalid manifest:\n- ${errors.join("\n- ")}`);
  const entry = archive.read(manifest.entry, MAX_ENTRY_BYTES).toString("utf8");
  if (!entry.includes("ABUPlugin")) throw new Error(`${manifest.entry}: missing ABUPlugin export`);
  return { manifest, bytes: file.size };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const path = process.argv[2];
  const args = process.argv.slice(3);
  const option = (name) => args.includes(name) ? args[args.indexOf(name) + 1] : null;
  if (!path) { console.error("Usage: node tools/validate.mjs <plugin.abu-plugin> [--sha256 hex] [--public-key base64 --signature base64]"); process.exitCode = 2; }
  else validatePackage(path).then(async ({ manifest, bytes }) => {
    const content = await readFile(resolve(path));
    const expectedHash = option("--sha256");
    if (expectedHash && createHash("sha256").update(content).digest("hex") !== expectedHash.toLowerCase()) throw new Error("SHA-256 mismatch");
    const publicKey = option("--public-key");
    const signature = option("--signature");
    if (Boolean(publicKey) !== Boolean(signature)) throw new Error("supply both --public-key and --signature");
    if (publicKey) {
      const prefix = Buffer.from("302a300506032b6570032100", "hex");
      const raw = Buffer.from(publicKey, "base64");
      if (raw.length !== 32) throw new Error("Ed25519 public key must be 32 bytes");
      const key = createPublicKey({ key: Buffer.concat([prefix, raw]), format: "der", type: "spki" });
      if (!verify(null, content, key, Buffer.from(signature, "base64"))) throw new Error("Ed25519 signature mismatch");
    }
    console.log(`Valid ${manifest.id}@${manifest.version} (${bytes} bytes)`);
  }).catch((error) => { console.error(error.message); process.exitCode = 1; });
}
