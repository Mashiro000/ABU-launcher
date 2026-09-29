import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { createHash, createPrivateKey, createPublicKey, generateKeyPairSync, sign } from "node:crypto";
import { validatePackage } from "./validate.mjs";

const args = process.argv.slice(2);
const option = (name) => args[args.indexOf(name) + 1];
const file = args.includes("--file") ? resolve(option("--file")) : null;
const key = args.includes("--key") ? resolve(option("--key")) : null;
const url = args.includes("--url") ? option("--url") : null;
const abi = args.includes("--abi") ? option("--abi") : "universal";
const parsedUrl = url ? URL.canParse(url) && new URL(url).protocol === "https:" : false;
if (!file || !key || !parsedUrl) {
  console.error("Usage: node tools/publish.mjs --file dist/plugin.abu-plugin --key .keys/publisher.pem --url https://example.com/plugin.abu-plugin [--abi universal]");
  process.exit(2);
}
const { manifest } = await validatePackage(file);
let privatePem;
try { privatePem = await readFile(key); }
catch (error) {
  if (error.code !== "ENOENT") throw error;
  const pair = generateKeyPairSync("ed25519");
  privatePem = pair.privateKey.export({ format: "pem", type: "pkcs8" });
  await mkdir(dirname(key), { recursive: true });
  await writeFile(key, privatePem, { mode: 0o600, flag: "wx" });
  console.error(`Created signing key at ${key}. Keep it private and back it up.`);
}
const privateKey = createPrivateKey(privatePem);
const publicDer = createPublicKey(privateKey).export({ format: "der", type: "spki" });
const content = await readFile(file);
const result = {
  publicKey: publicDer.subarray(-32).toString("base64"),
  asset: {
    abi, url, size: (await stat(file)).size,
    sha256: createHash("sha256").update(content).digest("hex"),
    signature: sign(null, content, privateKey).toString("base64"),
  },
};
console.log(JSON.stringify(result, null, 2));
